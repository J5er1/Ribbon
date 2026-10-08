package app.readribbon.data

import app.readribbon.core.PageFaces
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A state file written by an older build still decodes.
 *
 * The owner reported preferences not surviving an app update. `filesDir` and
 * `SharedPreferences` both survive one, so the suspect was the state file
 * failing to decode and `load()` quietly answering with a fresh [AppState] —
 * which is silent, total, and looks exactly like "my settings went".
 *
 * Every field added since launch carries a default, so an old file *should*
 * decode. "Should" is what this test is for: the next field added without a
 * default takes every reader's settings with it, and nothing else in the
 * build would notice.
 *
 * The JSON below is deliberately hand-written rather than produced by the
 * current model — a round-trip through today's encoder would prove only that
 * today agrees with itself.
 */
class StateSurvivesAnUpdateTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** A state file from before notifications, phrases or a room's version. */
    private val fromAnOlderBuild = """
        {
          "me": {
            "id": "6f3a1f3c-0f2e-4a5a-9a1e-2b7d5c8e1a01",
            "name": "Jonathan",
            "translation": "web"
          },
          "people": {},
          "rooms": [
            {
              "id": "1a2b3c4d-0000-4000-8000-000000000001",
              "name": null,
              "createdAt": "2026-01-01T00:00:00Z",
              "isPaused": false
            }
          ],
          "memberships": [],
          "readings": [
            {
              "id": "1a2b3c4d-0000-4000-8000-000000000002",
              "roomID": "1a2b3c4d-0000-4000-8000-000000000001",
              "bookID": "MRK",
              "startedAt": "2026-01-01T00:00:00Z",
              "handiwork": { "scale": "medium" }
            }
          ],
          "notes": [],
          "highlights": [
            {
              "id": "1a2b3c4d-0000-4000-8000-000000000003",
              "readingID": "1a2b3c4d-0000-4000-8000-000000000002",
              "authorID": "6f3a1f3c-0f2e-4a5a-9a1e-2b7d5c8e1a01",
              "range": {
                "bookID": "MRK",
                "chapter": 4,
                "startVerse": 9,
                "endVerse": 9
              },
              "ink": "teal",
              "createdAt": "2026-01-01T00:00:00Z"
            }
          ],
          "settings": {
            "scriptureSize": 24.0,
            "lineSpacingStep": 2,
            "redLetter": true,
            "quietHoursStart": 1380,
            "quietHoursEnd": 420
          },
          "hasSeenMarginHint": true
        }
    """.trimIndent()

    @Test fun anOlderStateFileStillDecodes() {
        val state = json.decodeFromString<AppState>(fromAnOlderBuild)

        // The preferences themselves, which are what the report was about.
        assertEquals("text size", 24.0, state.settings.scriptureSize, 0.0001)
        assertEquals("line spacing", 2, state.settings.lineSpacingStep)
        assertTrue("red letter", state.settings.redLetter)
        assertEquals("quiet hours start", 1380, state.settings.quietHoursStart)
        assertEquals("quiet hours end", 420, state.settings.quietHoursEnd)
        assertTrue("the margin hint stays seen", state.hasSeenMarginHint)
        assertEquals("the person", "Jonathan", state.me?.name)
    }

    /** Fields added since that file was written take their defaults. */
    @Test fun whatIsMissingTakesItsDefault() {
        val state = json.decodeFromString<AppState>(fromAnOlderBuild)

        assertTrue("a room with no version reads the launch one",
            state.rooms.single().translation == app.readribbon.core.TranslationID.bsb)
        assertTrue("a book with no version reads the launch one",
            state.readings.single().translation == app.readribbon.core.TranslationID.bsb)
        assertTrue("a highlight from before phrases marks whole verses",
            state.highlights.single().range.isWholeVerses)
        assertTrue("a highlight from before original words carries none",
            state.highlights.single().range.let { it.startWords == null && it.wordsSource == null })
        assertTrue("nothing has been notified about yet", state.notifiedThrough == null)
        // A build from before what's new had never shown it, and null is how
        // that reads: with a person here, an update that is owed the screen.
        assertTrue("no release has been shown yet", state.whatsNewSeen == null)
        // The page's settings from A68 start where the page already was.
        assertEquals("the weight is Book", 1, state.settings.weightStep)
        assertEquals("drawn at the weight it always was", 400, state.settings.weight(boldText = false))
        assertFalse("verses run on in their paragraphs", state.settings.versePerLine)
        assertFalse("the numbers stay quiet", state.settings.clearVerseNumbers)
        assertEquals("at the page's own ink", 0.45, state.settings.verseNumberAlpha, 0.0001)
        // And the sliders' and the face's from A69, where the steps already were.
        assertNull("no line spacing slid", state.settings.lineHeightHundredths)
        assertNull("no weight slid", state.settings.pageWeight)
        assertEquals("the letters as set", 0, state.settings.letterSpacingThousandths)
        assertEquals("no margin", 0, state.settings.marginPoints)
        assertEquals("in Literata", "literata", state.settings.typeface)
        assertEquals("in Literata", PageFaces.literata, state.settings.face)
        assertEquals("the old Open is still open", 1.9, state.settings.lineHeightMultiple, 0.0)
    }

    /**
     * A file written once the sliders exist (A69): every key, the sliders'
     * values and the steps written beside them, read back as they went in.
     */
    @Test fun aFileWithTheSlidersDecodes() {
        val state = json.decodeFromString<AppState>(
            """
            {
              "settings": {
                "scriptureSize": 22.5,
                "lineSpacingStep": 2,
                "redLetter": false,
                "quietHoursStart": 1320,
                "quietHoursEnd": 360,
                "roomNotifications": {},
                "weightStep": 1,
                "versePerLine": true,
                "clearVerseNumbers": false,
                "lineHeightHundredths": 184,
                "pageWeight": 420,
                "letterSpacingThousandths": 25,
                "marginPoints": 32,
                "typeface": "ebGaramond"
              }
            }
            """.trimIndent(),
        )
        val settings = state.settings

        assertEquals("text size", 22.5, settings.scriptureSize, 0.0)
        assertEquals("the step beside", 2, settings.lineSpacingStep)
        assertEquals("the slider's value", 184, settings.lineHeightHundredths)
        assertEquals("is the page", 1.84, settings.lineHeightMultiple, 0.0)
        assertEquals("the step beside", 1, settings.weightStep)
        assertEquals("the slider's value", 420, settings.pageWeight)
        assertEquals("is the page", 420, settings.weight(boldText = false))
        assertEquals("with Bold Text", 570, settings.weight(boldText = true))
        assertEquals("letter spacing", 0.025, settings.letterSpacingEm, 0.0)
        assertEquals("margins", 32.0, settings.marginRequested, 0.0)
        assertEquals("the face", PageFaces.ebGaramond, settings.face)
        assertTrue("a new line for every verse", settings.versePerLine)
    }

    /**
     * A slider writes its value and, beside it, the old step nearest it, so
     * that a build from before the sliders, which reads only the step, opens
     * on nearly the same page (A69).
     */
    @Test fun aSliderWritesTheOldStepBeside() {
        val spaced = AppSettings().withLineHeight(184)
        assertEquals(184, spaced.lineHeightHundredths)
        assertEquals(2, spaced.lineSpacingStep)
        assertEquals(165, AppSettings().withLineHeight(163).lineHeightHundredths)
        assertEquals(1, AppSettings().withLineHeight(163).lineSpacingStep)
        assertEquals(0, AppSettings().withLineHeight(150).lineSpacingStep)
        val lighter = AppSettings().withWeight(360)
        assertEquals(360, lighter.pageWeight)
        assertEquals(0, lighter.weightStep)
        assertEquals(510, lighter.weight(boldText = true))
        assertEquals(470, AppSettings().withWeight(999).pageWeight)
        assertEquals(2, AppSettings().withWeight(999).weightStep)
        assertEquals(1, AppSettings().withWeight(420).weightStep)
        // A value no slider writes is still held when the page reads it.
        val wild = AppSettings(marginPoints = 1000, letterSpacingThousandths = -5, typeface = "comicSans")
        assertEquals(48.0, wild.marginRequested, 0.0)
        assertEquals(0.0, wild.letterSpacingEm, 0.0)
        assertEquals(PageFaces.literata, wild.face)
    }

    /**
     * And the round trip: what today writes, today reads. This is the weaker
     * half of the pair and is here to catch an encoder that writes something
     * its own decoder rejects.
     */
    @Test fun todaysFileDecodesToTheSameThing() {
        val before = json.decodeFromString<AppState>(fromAnOlderBuild)
        val after = json.decodeFromString<AppState>(json.encodeToString(before))
        assertEquals("a round trip changes nothing", before, after)
    }

    /** A mark that knows its original words (A60) keeps them on the phone. */
    @Test fun aMarksOriginalWordsSurviveTheFile() {
        val before = json.decodeFromString<AppState>(fromAnOlderBuild)
        val marked = before.highlights.single().let {
            it.copy(
                range = app.readribbon.core.VerseRange(
                    bookID = "MRK", chapter = 4, startVerse = 9, endVerse = 10,
                    startChar = 4, endChar = 11, charTranslation = app.readribbon.core.TranslationID.web,
                    startWords = listOf(2, 3), endWords = listOf(0), wordsSource = "bsbt-5558512b",
                ),
            )
        }
        val state = before.copy(highlights = listOf(marked))
        val after = json.decodeFromString<AppState>(json.encodeToString(state))
        assertEquals(state.highlights, after.highlights)
    }
}
