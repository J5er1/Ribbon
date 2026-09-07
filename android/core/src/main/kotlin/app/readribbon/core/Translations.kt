package app.readribbon.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Translations. The two launch translations are public domain and ship in
// the app, whole. Licensed translations (NKJV first, two more undecided)
// arrive through API.Bible — build book horizon §15 / open question §16.8,
// decided: they stream through the room's own proxy and cache only the
// book being read, because their licenses forbid shipping the text.
//
// Translation is a personal setting, not a room setting (§2.6): notes pin
// to verse addresses, so a note lands on the same verse whichever
// translation each person reads.

/**
 * A translation's identity — a stable, lowercase key. Extensible: stored
 * state and the database carry the raw string, so adding a translation
 * never migrates anything.
 */
@Serializable(with = TranslationIDSerializer::class)
data class TranslationID(val rawValue: String) {
    companion object {
        val bsb = TranslationID(rawValue = "bsb")
        val web = TranslationID(rawValue = "web")
        val nkjv = TranslationID(rawValue = "nkjv")
        val niv = TranslationID(rawValue = "niv")
        val nasb = TranslationID(rawValue = "nasb")
    }
}

/**
 * Swift hand-writes TranslationID's Codable conformance over a single-value
 * container, so the id travels as a bare JSON string rather than as an
 * object with a `rawValue` field. The two apps share one backend and one
 * JSON, so the wire form must not drift: this serializer is that same
 * single-value encoding.
 */
object TranslationIDSerializer : KSerializer<TranslationID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TranslationID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TranslationID =
        TranslationID(rawValue = decoder.decodeString())

    override fun serialize(encoder: Encoder, value: TranslationID) {
        encoder.encodeString(value.rawValue)
    }
}

/** Name as it appears in S20: "Berean Standard", "World English". */
val TranslationID.displayName: String
    get() = TranslationRegistry.translation(this)?.displayName ?: rawValue.uppercase()

val TranslationID.fullName: String
    get() = TranslationRegistry.translation(this)?.fullName ?: rawValue.uppercase()

/** Everything the app knows about one translation. */
data class Translation(
    val id: TranslationID,
    val displayName: String,
    val fullName: String,
    val source: Source,
    /**
     * Whether the text data carries words-of-Jesus markup (S20's
     * red-letter setting).
     */
    val redLetter: Boolean
) {
    sealed interface Source {
        /**
         * Public domain, converted by tools/usfx_to_json.py, shipped in
         * the bundle, whole. Offline is first-class.
         */
        data object bundled : Source

        /**
         * Licensed, served by API.Bible through the backend proxy (the
         * key never ships in the app). `bibleID` is API.Bible's id for
         * the exact edition; empty until the license lands.
         */
        data class apiBible(val bibleID: String) : Source
    }

    val isBundled: Boolean
        get() = source is Source.bundled

    /** The proxy can only serve an edition that has been named. */
    val isConfigured: Boolean
        get() = when (val source = source) {
            is Source.bundled -> true
            is Source.apiBible -> source.bibleID.isNotEmpty()
        }
}

object TranslationRegistry {
    val bsb = Translation(
        id = TranslationID.bsb, displayName = "Berean Standard", fullName = "Berean Standard Bible",
        source = Translation.Source.bundled, redLetter = false)

    val web = Translation(
        id = TranslationID.web, displayName = "World English", fullName = "World English Bible",
        source = Translation.Source.bundled, redLetter = true)

    // The three licensed editions, live on the room's API.Bible account
    // (Open Book plan) and served through the bible-proxy. The bibleIDs
    // are catalog identifiers, not secrets. All three carry words-of-Jesus
    // markup.
    val nkjv = Translation(
        id = TranslationID.nkjv, displayName = "New King James", fullName = "New King James Version",
        source = Translation.Source.apiBible(bibleID = "63097d2a0a2f7db3-01"), redLetter = true)

    val niv = Translation(
        id = TranslationID.niv, displayName = "New International", fullName = "New International Version (2011)",
        source = Translation.Source.apiBible(bibleID = "78a9f6124f344018-01"), redLetter = true)

    val nasb = Translation(
        id = TranslationID.nasb, displayName = "New American Standard", fullName = "New American Standard Bible (1995)",
        source = Translation.Source.apiBible(bibleID = "b8ee27bcd1cae43a-01"), redLetter = true)

    /** Shipped in the app, whole. */
    val bundled: List<Translation> = listOf(bsb, web)

    /** Licensed, streamed. */
    val licensed: List<Translation> = listOf(nkjv, niv, nasb)

    val all: List<Translation> = bundled + licensed

    fun translation(id: TranslationID): Translation? =
        all.firstOrNull { it.id == id }

    fun isBundled(id: TranslationID): Boolean =
        translation(id)?.isBundled ?: false
}
