package app.readribbon.data

import android.content.Context
import app.readribbon.core.Bible
import app.readribbon.core.ScriptureBookText
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.TranslationID
import app.readribbon.core.TranslationRegistry
import app.readribbon.core.VerseAddress
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

// Scripture lives in the assets: both launch translations, whole, so offline
// reading is a first-class case and nothing is ever "locked" (§2.5). One
// JSON file per book per translation, loaded lazily and cached.
//
// (S21 shows download management for the day translations outgrow the app;
// at launch, everything is already on the phone.)

class ScriptureStore(context: Context) {

    private val assets = context.applicationContext.assets

    // Books are immutable once parsed and are read from every surface, so a
    // plain concurrent map is the whole cache. It is deliberately unbounded:
    // 66 books of parsed JSON is a few megabytes, and evicting the book
    // somebody is reading to save that would be the wrong trade.
    private val cache = ConcurrentHashMap<String, ScriptureBookText>()

    fun book(bookID: String, translation: TranslationID): ScriptureBookText? {
        val key = "${translation.rawValue}/$bookID"
        cache[key]?.let { return it }
        return runCatching {
            val text = assets.open("scripture/$key.json").use { it.readBytes().decodeToString() }
            json.decodeFromString<ScriptureBookText>(text)
        }.getOrNull()?.also { cache[key] = it }
    }

    fun chapter(address: VerseAddress, translation: TranslationID): ScriptureChapter? =
        book(address.bookID, translation)?.chapter(address.chapter)

    fun verseText(address: VerseAddress, translation: TranslationID): String? =
        chapter(address, translation)?.text(address.verse)

    /**
     * Scripture search (S23): matches book names, references, and text.
     * Lives in the chooser, where the thing being searched lives.
     */
    fun search(query: String, translation: TranslationID, limit: Int = 40): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val hits = mutableListOf<SearchHit>()

        // A reference like "Mark 4:9" or "Mark 4".
        parseReference(trimmed)?.let { hits.add(SearchHit.Reference(it)) }

        // Book names.
        for (book in Bible.books) {
            if (book.name.contains(trimmed, ignoreCase = true)) {
                hits.add(SearchHit.Book(book.id))
            }
        }

        // Text, across the books on this phone. Licensed translations keep
        // only the open book locally, so their text search runs over the
        // bundled Berean text instead — the hits are addresses, and an
        // address opens in the reader's own translation.
        val needle = trimmed.lowercase()
        val textTranslation =
            if (TranslationRegistry.isBundled(translation)) translation else TranslationID.bsb

        outer@ for (book in Bible.books) {
            val text = book(book.id, textTranslation) ?: continue
            for (chapter in text.chapters) {
                var verse = 0
                for (block in chapter.blocks) {
                    for (span in block.x) {
                        span.v?.let { verse = it }
                        if (verse > 0 && span.t.contains(needle, ignoreCase = true)) {
                            val address = VerseAddress(book.id, chapter.n, verse)
                            val last = hits.lastOrNull()
                            if (last is SearchHit.Verse && last.address == address) continue
                            hits.add(
                                SearchHit.Verse(address, chapter.text(verse) ?: span.t),
                            )
                            if (hits.size >= limit) break@outer
                        }
                    }
                }
            }
        }
        return hits
    }

    sealed interface SearchHit {
        data class Reference(val address: VerseAddress) : SearchHit
        data class Book(val bookID: String) : SearchHit
        data class Verse(val address: VerseAddress, val text: String) : SearchHit
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** "Mark 4:9", "mark 4", "1 john 3:2" */
        fun parseReference(query: String): VerseAddress? {
            val parts = query.split(" ").filter { it.isNotEmpty() }
            if (parts.size < 2) return null
            val numbers = parts.last().split(":")
            val chapter = numbers.firstOrNull()?.toIntOrNull() ?: return null
            var verse = 1
            when {
                numbers.size == 2 -> verse = numbers[1].toIntOrNull() ?: return null
                numbers.size > 2 -> return null
            }
            val name = parts.dropLast(1).joinToString(" ")
            if (name.isEmpty()) return null
            val book = Bible.books.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                    it.name.startsWith(name, ignoreCase = true)
            } ?: return null
            if (chapter < 1 || chapter > book.chapterCount) return null
            return VerseAddress(book.id, chapter, verse)
        }
    }
}
