@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.ReadingPoint
import app.readribbon.core.VerseAddress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The Android client and the iOS client join the *same* Phoenix topic on the
 * same Supabase project, so these shapes are not a private choice — they are
 * the contract between the two builds, and the counterpart is
 * `ios/Ribbon/Services/RoomChannel.swift`.
 *
 * Nothing else catches a mistake here. A differently-spelled topic still
 * connects, still heartbeats, and still reports a healthy channel; it fails
 * only in a real room, as one person who is plainly reading and simply never
 * appears — which is indistinguishable from nobody being there. That is
 * exactly the bug this file exists to stop: the first cut of the Android
 * client keyed its topic and its presence ids off `Uuid.toHexString()`, which
 * drops the dashes, and the two platforms never saw each other once.
 *
 * If a test here fails, either the Swift moved and this should follow it, or
 * the port drifted and the port is wrong. Check RoomChannel.swift first.
 */
class RoomChannelWireTest {

    private val room = Uuid.parse("11111111-2222-3333-4444-555555555555")
    private val me = Uuid.parse("66666666-7777-8888-9999-000000000000")
    private val other = Uuid.parse("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private val person = Person(id = me, name = "Ruth")

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    @Test
    fun testTopicIsHexAndDashLowercased() {
        // Postgres stores `11111111-2222-…`, the iOS client writes
        // `UUID.uuidString.lowercased()`, and the topic has to be that same
        // string on both platforms or the two clients are in different rooms.
        assertEquals(
            "realtime:room:11111111-2222-3333-4444-555555555555",
            RoomChannelWire.topic(room),
        )
        assertTrue(RoomChannelWire.id(me).contains("-"))
        assertEquals(RoomChannelWire.id(me), me.toString().lowercase())
    }

    @Test
    fun testJoinAsksForAPrivateChannelWithTheAccountsToken() {
        val message = RoomChannelWire.join(
            roomID = room, personID = me, private = true, accessToken = "jwt", ref = "1")
        assertEquals("phx_join", message.str("event"))
        assertEquals(RoomChannelWire.topic(room), message.str("topic"))
        // Phoenix matches a channel's later messages to its join by join_ref.
        assertEquals("1", message.str("ref"))
        assertEquals("1", message.str("join_ref"))

        val payload = message["payload"]!!.jsonObject
        assertEquals("jwt", payload.str("access_token"))

        val config = payload["config"]!!.jsonObject
        // Private is what makes Realtime check realtime.messages RLS against
        // the token above — the migration's whole point.
        assertEquals(true, config["private"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(0, config["postgres_changes"]!!.jsonArray.size)

        val presence = config["presence"]!!.jsonObject
        // Person-keyed, not socket-keyed: two devices are one person.
        assertEquals(RoomChannelWire.id(me), presence.str("key"))
        assertEquals(true, presence["enabled"]?.jsonPrimitive?.booleanOrNull)

        val broadcast = config["broadcast"]!!.jsonObject
        // self:false is why a room_changed nudge never bounces back and
        // makes the sender pull its own write.
        assertEquals(false, broadcast["self"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun testJoinOmitsTheTokenWhenThereIsNone() {
        val message = RoomChannelWire.join(
            roomID = room, personID = me, private = false, accessToken = null, ref = "7")
        val payload = message["payload"]!!.jsonObject
        assertNull(payload["access_token"])
        assertEquals(
            false,
            payload["config"]!!.jsonObject["private"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    @Test
    fun testTrackCarriesAnAddressNeverAPercentage() {
        val message = RoomChannelWire.track(
            roomID = room,
            person = person,
            position = VerseAddress("MRK", 6, 7),
            scrollFraction = 0.5,
            isIdle = false,
            following = other,
            ref = "2",
        )
        assertEquals("presence", message.str("event"))
        val outer = message["payload"]!!.jsonObject
        assertEquals("presence", outer.str("type"))
        assertEquals("track", outer.str("event"))

        val meta = outer["payload"]!!.jsonObject
        assertEquals(RoomChannelWire.id(me), meta.str("id"))
        assertEquals("Ruth", meta.str("name"))
        assertEquals("0.5", meta.str("scrollFraction"))
        assertEquals(false, meta["isIdle"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(RoomChannelWire.id(other), meta.str("followingPersonID"))

        val position = meta["position"]!!.jsonObject
        assertEquals("MRK", position.str("book"))
        assertEquals("6", position.str("chapter"))
        assertEquals("7", position.str("verse"))
    }

    /**
     * Absent is absent. The iOS side builds its dictionary the same way, and
     * for a harder reason than symmetry: an Optional bridged into a JSON
     * dictionary there is not serializable at all, so a null position would
     * drop the *whole* message rather than one key.
     */
    @Test
    fun testTrackOmitsAnAbsentPositionAndFollow() {
        val message = RoomChannelWire.track(
            roomID = room, person = person, position = null, scrollFraction = 0.0,
            isIdle = true, following = null, ref = "3")
        val meta = message["payload"]!!.jsonObject["payload"]!!.jsonObject
        assertNull(meta["position"])
        assertNull(meta["followingPersonID"])
        assertEquals(true, meta["isIdle"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(
            setOf("id", "name", "scrollFraction", "isIdle"),
            meta.keys,
        )
    }

    @Test
    fun testUntrackLeavesTheChannelOpen() {
        val message = RoomChannelWire.untrack(room, "4")
        // Not phx_leave: out of the book, still in the room.
        assertEquals("presence", message.str("event"))
        assertEquals("untrack", message["payload"]!!.jsonObject.str("event"))
        assertFalse(message.str("event") == "phx_leave")
    }

    @Test
    fun testThinkingOfYouIsContentless() {
        val message = RoomChannelWire.thinkingOfYou(room, fromName = "Ruth", to = other, ref = "5")
        assertEquals("broadcast", message.str("event"))
        val outer = message["payload"]!!.jsonObject
        assertEquals("thinking_of_you", outer.str("event"))
        val body = outer["payload"]!!.jsonObject
        // A name and who it is for. No message, ever (§4.3).
        assertEquals(setOf("fromName", "toPersonID"), body.keys)
        assertEquals(RoomChannelWire.id(other), body.str("toPersonID"))
    }

    @Test
    fun testRoomChangedCarriesNoContent() {
        val message = RoomChannelWire.roomChanged(room, "6")
        assertEquals("broadcast", message.str("event"))
        val outer = message["payload"]!!.jsonObject
        assertEquals("room_changed", outer.str("event"))
        // The nudge names the room and nothing else: the database stays the
        // only copy of the truth, and the other phone pulls it.
        assertEquals(setOf("roomID"), outer["payload"]!!.jsonObject.keys)
    }

    /**
     * Where a reader's line is, for the person following them: a point and
     * never a time (§13). No timestamp, no rate, no duration — a follower
     * stamps it when it arrives — and the chapter and verse are whole
     * numbers, because the Swift side reads them as `Int`.
     */
    @Test
    fun testReadingCarriesAPointNeverATime() {
        val message = RoomChannelWire.reading(
            roomID = room,
            personID = me,
            source = "0a1b2c3d",
            book = "MRK",
            at = ReadingPoint(chapter = 6, verse = 12, part = 0.42),
            end = ReadingPoint(chapter = 6, verse = 19, part = 0.7),
            settled = true,
            carried = true,
            ref = "10",
        )
        assertEquals("broadcast", message.str("event"))
        assertEquals(RoomChannelWire.topic(room), message.str("topic"))
        val outer = message["payload"]!!.jsonObject
        assertEquals("broadcast", outer.str("type"))
        assertEquals("reading", outer.str("event"))

        val body = outer["payload"]!!.jsonObject
        assertEquals(
            setOf("id", "source", "book", "chapter", "verse", "part", "end", "settled", "carried"),
            body.keys,
        )
        assertEquals(RoomChannelWire.id(me), body.str("id"))
        assertEquals("0a1b2c3d", body.str("source"))
        assertEquals("MRK", body.str("book"))
        assertEquals("6", body.str("chapter"))
        assertEquals("12", body.str("verse"))
        assertFalse(body["chapter"]!!.jsonPrimitive.isString)
        assertFalse(body["verse"]!!.jsonPrimitive.isString)
        assertEquals("0.42", body.str("part"))
        assertEquals(true, body["settled"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, body["carried"]?.jsonPrimitive?.booleanOrNull)

        val end = body["end"]!!.jsonObject
        assertEquals(setOf("chapter", "verse", "part"), end.keys)
        assertEquals("6", end.str("chapter"))
        assertEquals("19", end.str("verse"))
        assertEquals("0.7", end.str("part"))
    }

    /**
     * Absent is absent, as it is for presence: no `end` when the sender
     * could not say, and no `carried` when their page is their own.
     */
    @Test
    fun testReadingOmitsAnAbsentEndAndCarried() {
        val message = RoomChannelWire.reading(
            roomID = room, personID = me, source = "0a1b2c3d", book = "MRK",
            at = ReadingPoint(chapter = 1, verse = 1), end = null,
            settled = false, carried = false, ref = "11")
        val body = message["payload"]!!.jsonObject["payload"]!!.jsonObject
        assertEquals(
            setOf("id", "source", "book", "chapter", "verse", "part", "settled"),
            body.keys,
        )
        assertEquals(false, body["settled"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(0.0, body["part"]!!.jsonPrimitive.doubleOrNull!!, 0.0)
    }

    /**
     * Two verses that begin on one line leave nothing to divide by, and a
     * NaN serialises as a bare `NaN` — not JSON, and fatal to the Swift
     * decoder. Whatever goes in, what goes out is a number inside 0…1 that
     * parses.
     */
    @Test
    fun testReadingNeverSendsANonFinitePart() {
        val cases = listOf(
            Double.NaN to 0.0,
            Double.POSITIVE_INFINITY to 1.0,
            Double.NEGATIVE_INFINITY to 0.0,
            1.7 to 1.0,
            -0.3 to 0.0,
            0.126 to 0.13,
        )
        for ((given, sent) in cases) {
            val message = RoomChannelWire.reading(
                roomID = room, personID = me, source = "0a1b2c3d", book = "MRK",
                at = ReadingPoint(chapter = 4, verse = 14, part = given),
                end = ReadingPoint(chapter = 4, verse = 15, part = given),
                settled = true, carried = false, ref = "12")
            val parsed = Json.parseToJsonElement(message.toString()).jsonObject
            val body = parsed["payload"]!!.jsonObject["payload"]!!.jsonObject
            val part = body["part"]!!.jsonPrimitive.doubleOrNull
            assertNotNull("a part of $given should parse as a number", part)
            assertEquals("a part of $given", sent, part!!, 1e-9)
            val endPart = body["end"]!!.jsonObject["part"]!!.jsonPrimitive.doubleOrNull
            assertEquals("an end part of $given", sent, endPart!!, 1e-9)
        }
    }

    /** What this build hears back is what it sends. */
    @Test
    fun testReadingIsHeardAsItWasSent() {
        val message = RoomChannelWire.reading(
            roomID = room, personID = other, source = "0a1b2c3d", book = "MRK",
            at = ReadingPoint(chapter = 6, verse = 12, part = 0.42),
            end = ReadingPoint(chapter = 7, verse = 1, part = 0.0),
            settled = true, carried = false, ref = "13")
        val heard = RoomChannelWire.heard(message["payload"]!!.jsonObject["payload"])
        assertNotNull(heard)
        assertEquals(other, heard!!.personID)
        assertEquals("0a1b2c3d", heard.source)
        assertEquals("MRK", heard.book)
        assertEquals(ReadingPoint(chapter = 6, verse = 12, part = 0.42), heard.at)
        assertEquals(ReadingPoint(chapter = 7, verse = 1, part = 0.0), heard.end)
        assertTrue(heard.settled)
        assertFalse(heard.carried)
    }

    /**
     * Somebody else's shape is read defensively: anything malformed drops
     * the report rather than guessing at it, and a missing `settled` is a
     * report at rest.
     */
    @Test
    fun testReadingIsHeardDefensively() {
        fun heard(json: String) = RoomChannelWire.heard(Json.parseToJsonElement(json))
        val id = RoomChannelWire.id(other)
        fun body(extra: String = "", chapter: String = "6", part: String = "0.5") =
            """{"id":"$id","source":"a","book":"MRK","chapter":$chapter,"verse":12,"part":$part$extra}"""

        val good = heard(body())
        assertNotNull(good)
        assertEquals(true, good!!.settled)
        assertEquals(false, good.carried)
        assertNull(good.end)
        // Keys this build does not know are somebody newer's, and harmless.
        assertNotNull(heard(body(extra = ""","later":{"x":1}""")))

        assertNull(heard("null"))
        assertNull(heard("[1,2]"))
        assertNull(heard("\"reading\""))
        assertNull(heard(body(chapter = "\"6\"")))
        assertNull(heard(body(chapter = "6.5")))
        assertNull(heard(body(part = "null")))
        assertNull(heard(body(part = "\"0.5\"")))
        assertNull(heard(body(extra = ""","settled":"yes"""")))
        assertNull(heard(body(extra = ""","end":7""")))
        assertNull(heard(body(extra = ""","end":{"chapter":6}""")))
        assertNull(heard("""{"id":"nobody","source":"a","book":"MRK","chapter":6,"verse":12,"part":0.5}"""))
        assertNull(heard("""{"id":"$id","book":"MRK","chapter":6,"verse":12,"part":0.5}"""))
        // Out of range is held inside it, not dropped.
        assertEquals(1.0, heard(body(part = "3"))!!.at.part, 0.0)
    }

    @Test
    fun testHeartbeatRidesThePhoenixTopic() {
        val message = RoomChannelWire.heartbeat("8")
        assertEquals("phoenix", message.str("topic"))
        assertEquals("heartbeat", message.str("event"))
    }

    @Test
    fun testLeaveNamesTheRoomsTopic() {
        val message = RoomChannelWire.leave(room, "9")
        assertEquals("phx_leave", message.str("event"))
        assertEquals(RoomChannelWire.topic(room), message.str("topic"))
    }
}
