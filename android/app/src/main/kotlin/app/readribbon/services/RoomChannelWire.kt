@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.services

import app.readribbon.core.Person
import app.readribbon.core.ReadingPoint
import app.readribbon.core.VerseAddress
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToLong
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

    /**
     * Where the reader's line is (§4.2): the verse at the line a page reads
     * its own place from, and how far through it — finer than the verse
     * presence carries, so the page that follows can keep to it. Sent only
     * while somebody can be seen following, and never a time: no timestamp,
     * no rate, no duration (§13). The follower stamps it when it arrives.
     *
     * `end` is the same kind of point at the bottom of what the sender can
     * see, and `carried` says the sender's own page is being carried by a
     * follow of theirs. Both are omitted when absent or false, as presence's
     * optional keys are.
     *
     * The body is always an object. An older build of this app decodes any
     * broadcast's body as one before looking at its event name, and anything
     * else would throw on its socket's thread.
     */
    fun reading(
        roomID: Uuid,
        personID: Uuid,
        source: String,
        book: String,
        at: ReadingPoint,
        end: ReadingPoint?,
        settled: Boolean,
        carried: Boolean,
        ref: String,
    ): JsonObject =
        broadcast(
            roomID,
            event = "reading",
            payload = buildJsonObject {
                put("id", id(personID))
                put("source", source)
                put("book", book)
                put("chapter", at.chapter)
                put("verse", at.verse)
                put("part", part(at.part))
                if (end != null) {
                    putJsonObject("end") {
                        put("chapter", end.chapter)
                        put("verse", end.verse)
                        put("part", part(end.part))
                    }
                }
                put("settled", settled)
                if (carried) put("carried", true)
            },
            ref = ref,
        )

    /**
     * How far through a verse, as it travels: inside 0…1, to two places, and
     * always a number. A NaN would go out as a bare `NaN` — not JSON — and
     * would crash the rounding on the way; it is the start of the verse.
     */
    fun part(value: Double): Double {
        if (value.isNaN()) return 0.0
        val clamped = value.coerceIn(0.0, 1.0)
        return (clamped * 100).roundToLong() / 100.0
    }

    /** What a `reading` body said, once it has been checked. */
    data class Heard(
        val personID: Uuid,
        val source: String,
        val book: String,
        val at: ReadingPoint,
        val end: ReadingPoint?,
        val settled: Boolean,
        val carried: Boolean,
    )

    /**
     * A `reading` body, read defensively: anything missing or the wrong
     * shape drops the whole report rather than guessing at it. `settled`
     * missing means settled; `carried` missing means not.
     */
    fun heard(body: JsonElement?): Heard? {
        val fields = body as? JsonObject ?: return null
        val person = fields.text("id")?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: return null
        val source = fields.text("source")?.takeIf { it.isNotEmpty() } ?: return null
        val book = fields.text("book")?.takeIf { it.isNotEmpty() } ?: return null
        val at = point(fields) ?: return null
        val end = when (val raw = fields["end"]) {
            null -> null
            is JsonObject -> point(raw) ?: return null
            else -> return null
        }
        val settled = flag(fields, "settled") ?: return null
        val carried = flag(fields, "carried") ?: return null
        return Heard(
            personID = person,
            source = source,
            book = book,
            at = at,
            end = end,
            settled = settled.value ?: true,
            carried = carried.value ?: false,
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A whole number on the wire, not a string of one and not a fraction. */
    private fun JsonObject.whole(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun point(fields: JsonObject): ReadingPoint? {
        val chapter = fields.whole("chapter")?.takeIf { it > 0 } ?: return null
        val verse = fields.whole("verse")?.takeIf { it >= 0 } ?: return null
        val raw = (fields["part"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            ?: return null
        if (!raw.isFinite()) return null
        return ReadingPoint(chapter = chapter, verse = verse, part = raw.coerceIn(0.0, 1.0))
    }

    /** A flag, absent (a null inside), or — null outside — the wrong shape. */
    private class Flag(val value: Boolean?)

    private fun flag(fields: JsonObject, key: String): Flag? {
        val raw = fields[key] ?: return Flag(null)
        val primitive = raw as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.booleanOrNull?.let { Flag(it) }
    }

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
