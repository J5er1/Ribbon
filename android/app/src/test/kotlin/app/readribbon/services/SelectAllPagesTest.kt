package app.readribbon.services

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * PostgREST ends a response at the project's max rows and says nothing in
 * the body. A pull that asked once got an arbitrary thousand of a room's
 * marks, and the phone pruned the rest. [SupabaseClient.selectAll] must
 * bring back every row, whatever the server's cap, and fail loudly rather
 * than stop short. The server here is a small PostgREST: `in.`/`gt.`/`eq.`
 * filters, `order`, `limit`, `offset`, a max-rows cap, and `count=exact`.
 */
@OptIn(ExperimentalUuidApi::class)
class SelectAllPagesTest {

    private class FakePostgrest(
        var rows: MutableList<JsonObject>,
        private val maxRows: Int,
        private val sayCount: Boolean = true,
    ) {
        val requests = mutableListOf<Map<String, List<String>>>()
        var betweenPages: (() -> Unit)? = null
        var failOnRequest: Int? = null
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        init {
            server.createContext("/") { exchange ->
                val params = (exchange.requestURI.rawQuery ?: "").split("&").filter { it.isNotEmpty() }
                    .map { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                    .groupBy({ it.first }, { it.second })
                requests += params
                if (requests.size > 1) betweenPages?.invoke()
                if (failOnRequest == requests.size) {
                    exchange.sendResponseHeaders(500, -1)
                    exchange.close()
                    return@createContext
                }
                var matched = rows.toList()
                for ((name, values) in params) {
                    if (name in setOf("order", "limit", "offset")) continue
                    for (value in values) matched = matched.filter { row -> matches(row[name]?.jsonPrimitive?.content, value) }
                }
                val order = params["order"]?.single()?.split(",")?.map { it.substringBefore(".") }.orEmpty()
                matched = matched.sortedWith { a, b ->
                    order.asSequence().map { compareValues(a[it]?.jsonPrimitive?.content, b[it]?.jsonPrimitive?.content) }
                        .firstOrNull { it != 0 } ?: 0
                }
                val offset = params["offset"]?.single()?.toInt() ?: 0
                if (sayCount && offset > 0 && offset >= matched.size) {
                    // PostgREST's PGRST103: an offset past the counted end.
                    exchange.responseHeaders.add("Content-Range", "*/${matched.size}")
                    exchange.sendResponseHeaders(416, -1)
                    exchange.close()
                    return@createContext
                }
                val limit = minOf(params["limit"]?.single()?.toInt() ?: Int.MAX_VALUE, maxRows)
                val page = matched.drop(offset).take(limit)
                val total = if (sayCount && params.isNotEmpty()) matched.size.toString() else "*"
                val range = if (page.isEmpty()) "*" else "$offset-${offset + page.size - 1}"
                exchange.responseHeaders.add("Content-Range", "$range/$total")
                val body = JsonArray(page).toString().toByteArray()
                exchange.sendResponseHeaders(if (page.size < matched.size) 206 else 200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        private fun matches(cell: String?, filter: String): Boolean = when {
            filter.startsWith("in.(") -> cell in filter.removePrefix("in.(").removeSuffix(")").split(",")
            filter.startsWith("gt.") -> cell != null && cell > filter.removePrefix("gt.")
            filter.startsWith("eq.") -> cell == filter.removePrefix("eq.")
            else -> error("unknown filter $filter")
        }

        val base get() = "http://127.0.0.1:${server.address.port}"
    }

    private var fake: FakePostgrest? = null

    @After
    fun stop() {
        fake?.server?.stop(0)
    }

    private val reading = Uuid.random().toString()

    private fun marks(count: Int): MutableList<JsonObject> = MutableList(count) {
        buildJsonObject {
            put("id", Uuid.random().toString())
            put("reading_id", reading)
        }
    }

    private fun answers(cards: Int, people: Int): MutableList<JsonObject> =
        (0 until cards).flatMap { _ ->
            val card = Uuid.random().toString()
            (0 until people).map {
                buildJsonObject {
                    put("card_id", card)
                    put("person_id", Uuid.random().toString())
                    put("reading_id", reading)
                }
            }
        }.toMutableList()

    private fun client(server: FakePostgrest): SupabaseClient = runBlocking {
        SupabaseClient(base = server.base, key = "k").also {
            it.restore(SupabaseSession("token", "refresh", SupabaseUser(Uuid.random())))
        }
    }

    private fun ids(json: String, vararg key: String): List<String> =
        Json.parseToJsonElement(json).jsonArray.map { row ->
            key.joinToString("/") { row.jsonObject[it]!!.jsonPrimitive.content }
        }

    private fun selectMarks(server: FakePostgrest): String = runBlocking {
        client(server).selectAll("highlights", listOf("reading_id" to "in.($reading)"), listOf("id"))
    }

    @Test
    fun testEveryMarkComesBackPastTheThousand() {
        val server = FakePostgrest(marks(2537), maxRows = 1000).also { fake = it }
        val got = ids(selectMarks(server), "id")
        assertEquals(server.rows.map { it["id"]!!.jsonPrimitive.content }.sorted(), got)
        assertEquals(3, server.requests.size)
    }

    @Test
    fun testAProjectCapBelowThePageSizeStillBringsEveryRow() {
        // Asking for 1,000 and getting 400 is not the end: the count says so.
        val server = FakePostgrest(marks(1500), maxRows = 400).also { fake = it }
        assertEquals(1500, ids(selectMarks(server), "id").toSet().size)
        assertEquals(4, server.requests.size)
    }

    @Test
    fun testAFewMarksAreOneRequest() {
        val server = FakePostgrest(marks(12), maxRows = 1000).also { fake = it }
        assertEquals(12, ids(selectMarks(server), "id").size)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun testNoMarksAtAllIsOneRequest() {
        val server = FakePostgrest(mutableListOf(), maxRows = 1000).also { fake = it }
        assertEquals(0, ids(selectMarks(server), "id").size)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun testAMarkDeletedBetweenPagesTakesNoOtherWithIt() {
        // By offset, removing a row from the first page would slide the
        // second page's first row back past the offset, and the pull would
        // prune a mark nobody deleted. By keyset it cannot.
        val server = FakePostgrest(marks(2200), maxRows = 1000).also { fake = it }
        val sorted = server.rows.sortedBy { it["id"]!!.jsonPrimitive.content }
        val gone = sorted[10]
        server.betweenPages = { server.rows.remove(gone); server.betweenPages = null }
        val got = ids(selectMarks(server), "id")
        // The deleted mark had already come back on the first page; every
        // other mark comes back exactly once.
        assertEquals(sorted.map { it["id"]!!.jsonPrimitive.content }, got)
    }

    @Test
    fun testACompositeKeyPagesByOffset() {
        val server = FakePostgrest(answers(cards = 300, people = 6), maxRows = 1000).also { fake = it }
        val got = runBlocking {
            client(server).selectAll(
                "card_answers", listOf("reading_id" to "in.($reading)"), listOf("card_id", "person_id"))
        }
        val keys = ids(got, "card_id", "person_id")
        assertEquals(1800, keys.toSet().size)
        assertEquals(keys.sorted(), keys)
        assertEquals(listOf(null, "1000"), server.requests.map { it["offset"]?.single() })
    }

    @Test
    fun testAnOffsetPastAShrunkEndIsTheEnd() {
        // Answers deleted between pages leave the next offset past the end;
        // the server says 416, and that is the last page, not a failure.
        val server = FakePostgrest(answers(cards = 200, people = 6), maxRows = 1000).also { fake = it }
        server.betweenPages = { repeat(300) { server.rows.removeAt(server.rows.size - 1) }; server.betweenPages = null }
        val got = runBlocking {
            client(server).selectAll(
                "card_answers", listOf("reading_id" to "in.($reading)"), listOf("card_id", "person_id"))
        }
        assertEquals(1000, ids(got, "card_id", "person_id").size)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun testWithoutACountItStopsOnlyAtAnEmptyPage() {
        val server = FakePostgrest(marks(1200), maxRows = 500, sayCount = false).also { fake = it }
        assertEquals(1200, ids(selectMarks(server), "id").toSet().size)
        assertEquals(4, server.requests.size)
    }

    @Test
    fun testAFailedPageFailsTheWholeSelect() {
        // So the pull never marks a part of the marks as all of them.
        val server = FakePostgrest(marks(2500), maxRows = 1000).also { fake = it }
        server.failOnRequest = 2
        try {
            selectMarks(server)
            fail("a failed second page must not read as a short answer")
        } catch (expected: SupabaseError.Http) {
            assertEquals(500, expected.status)
        }
    }

    @Test
    fun testTheCountIsReadFromContentRange() {
        assertEquals(1234, SupabaseClient.total("0-999/1234"))
        assertEquals(0, SupabaseClient.total("*/0"))
        assertNull(SupabaseClient.total("0-24/*"))
        assertNull(SupabaseClient.total(null))
    }
}
