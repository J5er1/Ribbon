@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.VerseAddress
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Every message the room's channel sends, built in one place.
 *
 * This is not a private choice. The Android client and the iOS client join
 * the *same* Phoenix topic on the *same* Supabase project, and a room of two
 * people on two platforms is the product. A topic spelled differently, or a
 * presence meta with a differently-shaped id, is not a compile error and not
 * a runtime error either — it is one person simply never appearing in the
 * other's presence line, which is indistinguishable from nobody being there.
 *
 * So the shapes live here, `RoomChannelWireTest` asserts them exactly, and
 * `ios/Ribbon/Services/RoomChannel.swift` builds the same ones by hand
 * beside a note pointing at this file.
 *
 * Ids are always the hex-and-dash form, lowercased — what Postgres stores
 * and what `UUID.uuidString.lowercased()` produces on the other side.
 * `Uuid.toHexString()` drops the dashes, and an id in that form is a room
 * the other platform never finds.
 */
internal object RoomChannelWire {

    fun id(value: Uuid): String = value.toString().lowercase()

    fun topic(roomID: Uuid): String = "realtime:room:${id(roomID)}"

    /**
     * The join. `private` asks Realtime to check `realtime.messages` RLS with
     * the token below, so a room you are not in refuses you; `presence.key`
     * makes the roster person-keyed rather than socket-keyed, so two devices
     * are one person; and `presence.enabled` is what opts the channel into
     * presence at all on current Realtime.
     */
    fun join(
        roomID: Uuid,
        personID: Uuid,
        private: Boolean,
        accessToken: String?,
        ref: String,
    ): JsonObject = buildJsonObject {
        put("topic", topic(roomID))
        put("event", "phx_join")
        putJsonObject("payload") {
            putJsonObject("config") {
                putJsonObject("broadcast") {
                    put("ack", false)
                    put("self", false)
                }
                putJsonObject("presence") {
                    put("key", id(personID))
                    put("enabled", true)
                }
                putJsonArray("postgres_changes") {}
                put("private", private)
            }
            if (accessToken != null) put("access_token", accessToken)
        }
        put("ref", ref)
        put("join_ref", ref)
    }

    fun leave(roomID: Uuid, ref: String): JsonObject = buildJsonObject {
        put("topic", topic(roomID))
        put("event", "phx_leave")
        putJsonObject("payload") {}
        put("ref", ref)
    }

    fun heartbeat(ref: String): JsonObject = buildJsonObject {
        put("topic", "phoenix")
        put("event", "heartbeat")
        putJsonObject("payload") {}
        put("ref", ref)
    }

    /**
     * Presence, as one reader sees another: a name, an address, how far down
     * the chapter, whether they have gone still, and who they are following.
     * Never a percentage of the book and never a duration (§13).
     *
     * `position` and `followingPersonID` are omitted when absent rather than
     * sent as null — the iOS side builds the dictionary the same way, and a
     * JSON null there would serialize to nothing at all.
     */
    fun track(
        roomID: Uuid,
        person: Person,
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Boolean,
        following: Uuid?,
        ref: String,
    ): JsonObject = buildJsonObject {
        put("topic", topic(roomID))
        put("event", "presence")
        putJsonObject("payload") {
            put("type", "presence")
            put("event", "track")
            putJsonObject("payload") {
                put("id", id(person.id))
                put("name", person.name)
                put("scrollFraction", scrollFraction)
                put("isIdle", isIdle)
                if (position != null) {
                    putJsonObject("position") {
                        put("book", position.bookID)
                        put("chapter", position.chapter)
                        put("verse", position.verse)
                    }
                }
                if (following != null) put("followingPersonID", id(following))
            }
        }
        put("ref", ref)
    }

    fun untrack(roomID: Uuid, ref: String): JsonObject = buildJsonObject {
        put("topic", topic(roomID))
        put("event", "presence")
        putJsonObject("payload") {
            put("type", "presence")
            put("event", "untrack")
            putJsonObject("payload") {}
        }
        put("ref", ref)
    }

    /** The contentless tap (§4.3). */
    fun thinkingOfYou(roomID: Uuid, fromName: String, to: Uuid, ref: String): JsonObject =
        broadcast(
            roomID,
            event = "thinking_of_you",
            payload = buildJsonObject {
                put("fromName", fromName)
                put("toPersonID", id(to))
            },
            ref = ref,
        )

    /**
     * "Something in this room moved." No content: the other phone pulls, and
     * the database stays the only copy of the truth.
     */
    fun roomChanged(roomID: Uuid, ref: String): JsonObject =
        broadcast(
            roomID,
            event = "room_changed",
            payload = buildJsonObject { put("roomID", id(roomID)) },
            ref = ref,
        )

    fun broadcast(roomID: Uuid, event: String, payload: JsonObject, ref: String): JsonObject =
        buildJsonObject {
            put("topic", topic(roomID))
            put("event", "broadcast")
            putJsonObject("payload") {
                put("type", "broadcast")
                put("event", event)
                put("payload", payload)
            }
            put("ref", ref)
        }
}
