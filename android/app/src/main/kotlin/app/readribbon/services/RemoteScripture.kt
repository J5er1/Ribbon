package app.readribbon.services

import android.content.Context
import app.readribbon.core.APIBibleContent
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.Translation
import app.readribbon.core.VerseAddress
import app.readribbon.data.ScriptureStore
import app.readribbon.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Licensed translations stream through Ribbon's own proxy — a Supabase
// Edge Function (`bible-proxy`) that holds the API.Bible key server-side,
// so the key never ships in the app and the license's terms are enforced
// in exactly one place. The client caches the book being read and nothing
// more; the bundled public-domain translations remain whole and offline.

/**
 * Swift marks this protocol `Sendable`: a provider is called from whatever
 * task needs a chapter and must be safe there. A `suspend` function carries
 * the same expectation — implementations own their dispatcher (the one
 * below confines its network work to [Dispatchers.IO]) and hold no
 * thread-affine state.
 */
interface RemoteScriptureProvider {
    suspend fun fetchChapter(
        bookID: String,
        chapter: Int,
        translation: Translation,
    ): ScriptureChapter
}

/**
 * Swift's `enum RemoteScriptureError: Error`. Kotlin errors are thrown
 * objects, so the three cases become singletons of one sealed class —
 * the same shape [SupabaseError] takes in this package.
 */
sealed class RemoteScriptureError(message: String) : Exception(message) {
    /** No API.Bible edition named for this translation, or the proxy has no key. */
    data object NotConfigured : RemoteScriptureError("Translation not configured") {
        private fun readResolve(): Any = NotConfigured
    }

    /** The proxy answered, but not with a chapter. */
    data object Unavailable : RemoteScriptureError("Scripture proxy unavailable") {
        private fun readResolve(): Any = Unavailable
    }

    /** A chapter body no verse text could be read out of. */
    data object BadPayload : RemoteScriptureError("Unreadable chapter payload") {
        private fun readResolve(): Any = BadPayload
    }
}

class APIBibleProvider(
    private val proxyURL: String = "${SupabaseConfig.URL}/functions/v1/bible-proxy",
) : RemoteScriptureProvider {

    override suspend fun fetchChapter(
        bookID: String,
        chapter: Int,
        translation: Translation,
    ): ScriptureChapter {
        val source = translation.source
        if (source !is Translation.Source.apiBible || source.bibleID.isEmpty()) {
            throw RemoteScriptureError.NotConfigured
        }
        val query = listOf(
            "bible" to source.bibleID,
            "chapter" to "$bookID.$chapter",
        ).joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }

        val data = withContext(Dispatchers.IO) {
            val connection = (URL("$proxyURL?$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
            }
            try {
                // Swift's `response as? HTTPURLResponse` guard has no
                // counterpart: an HttpURLConnection that reached the proxy
                // always has a status, and one that did not raises
                // IOException here — which, exactly like the URLError Swift
                // lets escape `data(for:)`, propagates to the caller rather
                // than becoming a RemoteScriptureError.
                val status = connection.responseCode
                if (status == 503) throw RemoteScriptureError.NotConfigured
                if (status !in 200..299) throw RemoteScriptureError.Unavailable
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
        return APIBibleContent.chapter(number = chapter, from = data)
            ?: throw RemoteScriptureError.BadPayload
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

// Swift adds the cache to ScriptureStore in an extension. Kotlin extensions
// carry no stored state, and ScriptureStore keeps its Context private, so
// the one thing the Swift static held — a directory — is threaded in as a
// Context instead. Everything else stays where the Swift put it, on the
// store, so the policy is still read next to the text it governs.

/**
 * The on-device cache policy for licensed text: the book being read,
 * and nothing older. Enforced here so a future provider can't
 * accidentally widen it.
 *
 * `cacheDir` is the Android caches directory — the same class of storage
 * iOS's `.cachesDirectory` is, evictable by the system under pressure,
 * which for licensed text is a feature rather than a risk.
 */
fun ScriptureStore.Companion.licensedCacheDirectory(context: Context): File =
    File(context.cacheDir, "LicensedScripture").apply { mkdirs() }

/** A chapter of a licensed translation, if it has already streamed. */
fun ScriptureStore.cachedRemoteChapter(
    context: Context,
    address: VerseAddress,
    translation: Translation,
): ScriptureChapter? {
    val file = ScriptureStore.licensedChapterFile(context, address, translation)
    val text = runCatching { file.readText() }.getOrNull() ?: return null
    return runCatching { licensedJson.decodeFromString<ScriptureChapter>(text) }.getOrNull()
}

/**
 * Stream a chapter through the proxy and keep it for this reading.
 * Chapters from other books of the same translation are dropped —
 * "kept on the phone for the current reading" is the whole policy.
 */
suspend fun ScriptureStore.ensureRemoteChapter(
    context: Context,
    address: VerseAddress,
    translation: Translation,
    provider: RemoteScriptureProvider = APIBibleProvider(),
): ScriptureChapter? {
    val cached = withContext(Dispatchers.IO) {
        cachedRemoteChapter(context, address, translation)
    }
    if (cached != null) return cached

    val chapter = runCatching {
        provider.fetchChapter(
            bookID = address.bookID, chapter = address.chapter, translation = translation,
        )
    }.getOrNull() ?: return null

    withContext(Dispatchers.IO) {
        pruneLicensedCache(context, keeping = address.bookID, translation = translation)
        runCatching {
            // Swift writes with `.atomic`; on Android that is write-beside
            // then rename, so a kill mid-write leaves the previous chapter
            // rather than half a one.
            val file = ScriptureStore.licensedChapterFile(context, address, translation)
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(licensedJson.encodeToString(chapter))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }
    return chapter
}

private fun ScriptureStore.Companion.licensedChapterFile(
    context: Context,
    address: VerseAddress,
    translation: Translation,
): File = File(
    ScriptureStore.licensedCacheDirectory(context),
    "${translation.id.rawValue}-${address.bookID}-${address.chapter}.json",
)

private fun ScriptureStore.pruneLicensedCache(
    context: Context,
    keeping: String,
    translation: Translation,
) {
    val keep = "${translation.id.rawValue}-$keeping-"
    val mine = "${translation.id.rawValue}-"
    val files = ScriptureStore.licensedCacheDirectory(context).listFiles() ?: emptyArray()
    for (file in files) {
        if (file.name.startsWith(mine) && !file.name.startsWith(keep)) {
            file.delete()
        }
    }
}

// Swift reads and writes the cache with a plain JSONEncoder/JSONDecoder
// pair. The chapter model is @Serializable, so this is the same:
// unknown keys tolerated (a cache written by an older build still reads),
// absent optionals left absent.
private val licensedJson = Json { ignoreUnknownKeys = true }
