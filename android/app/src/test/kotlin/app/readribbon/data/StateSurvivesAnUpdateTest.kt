package app.readribbon.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
        assertTrue("nothing has been notified about yet", state.notifiedThrough == null)
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
}
