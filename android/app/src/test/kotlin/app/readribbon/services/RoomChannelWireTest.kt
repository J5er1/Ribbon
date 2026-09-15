@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.VerseAddress
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
