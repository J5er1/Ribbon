@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.TranslationID
import app.readribbon.core.VerseRange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The Android client and the iOS client write to the same Supabase project,
 * so the column names these rows serialize to are not a private choice —
 * they are the schema in `supabase/migrations`.
 *
 * Nothing else catches a mistake here. A renamed field still compiles, still
 * type-checks, and still round-trips against itself; it fails only in a real
 * room, on someone else's phone, as a note or a fire that never arrives. So
 * the expected key sets below are transcribed from the migration by hand and
 * asserted exactly.
 *
 * If a test here fails, either the schema moved and this file should follow
 * it, or the port drifted and the port is wrong. Check the migration first.
 */
class WireFormatTest {

    private val json: Json = SupabaseClient.json
    private val id = Uuid.parse("11111111-2222-3333-4444-555555555555")
    private val other = Uuid.parse("66666666-7777-8888-9999-000000000000")
    private val at = Instant.fromEpochSeconds(1_780_000_000)

    private fun keysOf(encoded: String): Set<String> =
        Json.parseToJsonElement(encoded).jsonObject.keys

    @Test
    fun testProfilesRowMatchesSchema() {
        val row = RemoteSync.ProfileRow(
            id = id, name = "Ruth", portraitPath = "p.jpg", translation = "bsb",
        )
        assertEquals(
            setOf("id", "name", "portrait_path", "translation"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testRoomsRowMatchesSchema() {
        val row = RemoteSync.RoomRow(id = id, name = "Ours", isPaused = false, createdAt = at)
        assertEquals(
            setOf("id", "name", "is_paused", "created_at"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testMembershipsRowMatchesSchema() {
        val row = RemoteSync.MembershipRow(
            id = id, roomId = other, personId = id, ink = "teal", joinedAt = at,
        )
        assertEquals(
            setOf("id", "room_id", "person_id", "ink", "joined_at"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testRoomInksRowMatchesSchema() {
        // 20260907170000_ribbon_ink_outlives_membership.sql. `chosen_at` has
        // a default and the client never sends it — the row says which ink
        // and for whom, and the server says when.
        val row = RemoteSync.RoomInkRow(roomId = other, personId = id, ink = "teal")
        assertEquals(
            setOf("room_id", "person_id", "ink"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testInvitesRowMatchesSchema() {
        val row = RemoteSync.InviteRow(
            id = id, roomId = other, createdBy = id, createdAt = at, expiresAt = at,
        )
        assertEquals(
            setOf("id", "room_id", "created_by", "created_at", "expires_at"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testReadingsRowMatchesSchema() {
        val row = RemoteSync.ReadingRow(
            id = id, roomId = other, bookId = "MRK", scale = "medium",
            startedAt = at, finishedAt = at,
        )
        assertEquals(
            setOf("id", "room_id", "book_id", "scale", "started_at", "finished_at"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testFiresRowMatchesSchema() {
        val row = RemoteSync.FireRow(
            readingId = id, coalDepth = 0.25, lastFuelAt = at, restartAt = at,
            stateAtLastFuel = "burning",
        )
        assertEquals(
            setOf("reading_id", "coal_depth", "last_fuel_at", "restart_at", "state_at_last_fuel"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testFuelEventsRowMatchesSchema() {
        val row = RemoteSync.FuelEventRow(id = id, readingId = other, personId = id, at = at)
        assertEquals(
            setOf("id", "reading_id", "person_id", "at"),
            keysOf(json.encodeToString(row)),
        )
    }

    @Test
    fun testQuietDaysRowMatchesSchema() {
        val row = RemoteSync.QuietDayRow(
            id = id, roomId = other, personId = id,
            localDate = "2026-09-01", timeZone = "America/New_York", markedAt = at,
        )
        assertEquals(
            setOf("id", "room_id", "person_id", "local_date", "time_zone", "marked_at"),
            keysOf(json.encodeToString(row)),
        )
    }

    /**
     * 20260916190000_ribbon_a_mark_on_a_phrase.sql and
     * 20261002120000_ribbon_a_mark_follows_its_words.sql. A mark on whole
     * verses sends none of the six optional columns; a mark on a phrase
     * sends its offsets, the version they were taken in, and the original
     * words under it with the source they count in (A60). The row is built
     * the way `push(highlight)` builds it.
     */
    @Test
    fun testHighlightsRowMatchesSchema() {
        val whole = Highlight(
            id = id, readingID = other, authorID = id,
            range = VerseRange("JHN", 1, 3, 4), ink = Ink.teal, createdAt = at,
        )
        val base = setOf(
            "id", "reading_id", "author_id", "book_id", "chapter", "start_verse", "end_verse",
            "ink", "created_at",
        )
        assertEquals(base, keysOf(json.encodeToString(RemoteSync.HighlightRow.of(whole))))

        val phrase = whole.copy(
            range = VerseRange(
                bookID = "JHN", chapter = 1, startVerse = 3, endVerse = 4,
                startChar = 12, endChar = 22, charTranslation = TranslationID.bsb,
                startWords = listOf(3, 0), endWords = listOf(5), wordsSource = "bsbt-5558512b",
            ),
        )
        val encoded = json.encodeToString(RemoteSync.HighlightRow.of(phrase))
        assertEquals(
            base + setOf(
                "start_char", "end_char", "char_translation",
                "start_words", "end_words", "words_source",
            ),
            keysOf(encoded),
        )
        val fields = Json.parseToJsonElement(encoded).jsonObject
        assertEquals("[0,3]", fields["start_words"].toString())
        assertEquals("[5]", fields["end_words"].toString())
        assertEquals("bsbt-5558512b", fields["words_source"]?.jsonPrimitive?.content)

        // And back, as a pull reads it.
        val pulled = json.decodeFromString<RemoteSync.HighlightRow>(encoded)
        assertEquals(phrase.range, pulled.range())
    }

    /**
     * What a pull keeps: offsets only with the version they were taken in,
     * and original words only with the source they count in — a row from a
     * build before either decodes as whole verses, and a malformed set of
     * words costs only itself rather than the pull.
     */
    @Test
    fun testHighlightsRowIsPulledTolerantly() {
        fun pulled(extra: String): VerseRange = json.decodeFromString<RemoteSync.HighlightRow>(
            """{"id":"$id","reading_id":"$other","author_id":"$id","book_id":"JHN",""" +
                """"chapter":1,"start_verse":3,"end_verse":3,"ink":"teal",""" +
                """"created_at":"2026-05-28T20:26:40Z"$extra}""",
        ).range()

        val old = pulled("")
        assertTrue(old.isWholeVerses)
        assertEquals(null, old.wordsSource)

        val chars = ""","start_char":12,"end_char":22,"char_translation":"bsb""""
        val anchored = pulled("""$chars,"start_words":[0],"words_source":"bsbt-5558512b"""")
        assertEquals(listOf(0), anchored.startWords)
        assertEquals("bsbt-5558512b", anchored.wordsSource)

        val sourceless = pulled("""$chars,"start_words":[0]""")
        assertEquals(12, sourceless.startChar)
        assertEquals(null, sourceless.startWords)

        val malformed = pulled("""$chars,"start_words":["a",1],"words_source":"bsbt-5558512b"""")
        assertEquals(12, malformed.startChar)
        assertEquals(null, malformed.startWords)
        assertEquals(null, malformed.wordsSource)

        val unnamed = pulled(""","start_char":12,"end_char":22,"start_words":[0],"words_source":"bsbt-5558512b"""")
        assertTrue("offsets without their version are whole verses", unnamed.isWholeVerses)
    }

    /**
     * A null optional is OMITTED rather than sent as JSON null — matching
     * Swift's synthesized `encodeIfPresent`. Both clients behave this way, so
     * on both an upsert never clears one of these columns back to null. That
     * is a real gap in the sync surface (docs/deviations.md A13); it is
     * asserted here so it stays a known one.
     */
    @Test
    fun testNullOptionalsAreOmittedNotNulled() {
        val row = RemoteSync.ProfileRow(
            id = id, name = "Ruth", portraitPath = null, translation = "bsb",
        )
        val keys = keysOf(json.encodeToString(row))
        assertEquals(setOf("id", "name", "translation"), keys)
    }

    /**
     * Timestamps have to be something Postgres accepts as timestamptz, and
     * something the iOS client's decoder reads back. Both mean RFC-3339 with
     * an explicit zone.
     */
    @Test
    fun testTimestampsAreRfc3339() {
        val encoded = json.encodeToString(
            RemoteSync.RoomRow(id = id, name = null, isPaused = true, createdAt = at),
        )
        assertTrue(
            "timestamp is not RFC-3339: $encoded",
            Regex("\"created_at\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z\"")
                .containsMatchIn(encoded),
        )
    }

    /**
     * Ink and fire-state raw values are constrained by CHECK clauses in the
     * migration. The Kotlin enums serialize by entry name, so the entry names
     * are the wire values — this asserts they are still the ones the database
     * will accept.
     */
    @Test
    fun testEnumRawValuesSatisfyTheSchemaChecks() {
        val inks = app.readribbon.core.Ink.entries.map { it.name }.toSet()
        assertEquals(
            setOf("crimson", "clay", "ochre", "moss", "teal", "indigo", "plum", "rose"),
            inks,
        )
        // fires.state_at_last_fuel permits three of the four states; banked
        // is not restorable state, because banking lives on quiet days.
        val storable = app.readribbon.core.FireState.entries
            .map { it.name }
            .filter { it != "banked" }
            .toSet()
        assertEquals(setOf("catching", "burning", "steady"), storable)
    }

    /**
     * FireScale is written to readings.scale, which has its own CHECK.
     */
    @Test
    fun testFireScaleRawValuesSatisfyTheSchemaCheck() {
        assertEquals(
            setOf("small", "medium", "large"),
            app.readribbon.core.FireScale.entries.map { it.name }.toSet(),
        )
    }
}
