package app.readribbon.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A state file that will not decode no longer takes the settings with it.
 *
 * [StateSurvivesAnUpdateTest] is the other half of this: it proves that an
 * old file *does* decode, which is what makes a schema break an unlikely
 * explanation for the owner's report. This half is about what happens when
 * one does not, whatever the reason — a truncated write, a half-restored
 * backup, a field added one day without a default.
 *
 * The asymmetry that makes this worth doing: almost everything in
 * `state.json` is a cache of the backend and comes back on the next sync, so
 * a total reset looks like nothing at all happened. The settings are the
 * exception — they are only ever written here — so a reset shows up as
 * exactly one symptom, *"preferences do not stay between updates"*, with the
 * much larger reset underneath it invisible. That is how this could happen
 * repeatedly and be reported as a small thing.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsSurviveADamagedFileTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val base = File(context.filesDir, "Ribbon")
    private val stateFile = File(base, "state.json")
    private val kept = File(base, "state.json.unreadable")

    @Before fun clean() {
        base.mkdirs()
        stateFile.delete()
        kept.delete()
    }

    /**
     * Settings a person actually set, plus one of the "asked once" flags,
     * inside a file whose `rooms` array is missing a field it cannot do
     * without. This is the realistic damage: the graph breaks, the settings
     * object beside it is perfectly readable.
     */
    private val damagedAroundGoodSettings = """
        {
          "rooms": [ { "id": "1a2b3c4d-0000-4000-8000-000000000001" } ],
          "settings": {
            "scriptureSize": 26.0,
            "lineSpacingStep": 2,
            "redLetter": true,
            "quietHoursStart": 1320,
            "quietHoursEnd": 480,
            "weightStep": 2,
            "versePerLine": true,
            "clearVerseNumbers": true,
            "lineHeightHundredths": 184,
            "pageWeight": 460,
            "letterSpacingThousandths": 15,
            "marginPoints": 24,
            "typeface": "alegreya"
          },
          "hasSeenMarginHint": true,
          "hasPulledTheFire": true,
          "whatsNewSeen": "2026-10-original",
          "invitesHandedOut": [ "1a2b3c4d-0000-4000-8000-000000000009" ]
        }
    """.trimIndent()

    @Test fun theSettingsSurviveAFileThatWillNotDecode() = runTest {
        stateFile.writeText(damagedAroundGoodSettings)

        val state = LocalStore(context).load()

        assertEquals("text size", 26.0, state.settings.scriptureSize, 0.0001)
        assertEquals("line spacing", 2, state.settings.lineSpacingStep)
        assertTrue("red letter", state.settings.redLetter)
        assertEquals("quiet hours start", 1320, state.settings.quietHoursStart)
        assertEquals("quiet hours end", 480, state.settings.quietHoursEnd)
        // And the page's settings from A68 with them.
        assertEquals("weight", 2, state.settings.weightStep)
        assertTrue("a new line for every verse", state.settings.versePerLine)
        assertTrue("clearer verse numbers", state.settings.clearVerseNumbers)
        // And the sliders' and the face's from A69.
        assertEquals("line spacing slid", 184, state.settings.lineHeightHundredths)
        assertEquals("weight slid", 460, state.settings.pageWeight)
        assertEquals("letter spacing", 15, state.settings.letterSpacingThousandths)
        assertEquals("margins", 24, state.settings.marginPoints)
        assertEquals("typeface", "alegreya", state.settings.typeface)
    }

    /**
     * A settings object with one value this build cannot read, as a later
     * build might write it: the file will not decode whole, and the bad
     * value costs only itself, as an unreadable field does on the iPhone
     * (I42, A69).
     */
    @Test fun aBadPageValueCostsOnlyItself() = runTest {
        stateFile.writeText(
            """
            {
              "settings": {
                "scriptureSize": 26.0,
                "lineSpacingStep": 2,
                "redLetter": true,
                "quietHoursStart": 1320,
                "quietHoursEnd": 480,
                "weightStep": 2,
                "versePerLine": true,
                "clearVerseNumbers": true,
                "lineHeightHundredths": 184,
                "pageWeight": "heavier",
                "letterSpacingThousandths": 15,
                "marginPoints": "wide",
                "typeface": 7
              },
              "hasSeenMarginHint": true
            }
            """.trimIndent(),
        )

        val state = LocalStore(context).load()

        // The bad ones take their defaults.
        assertEquals("an unreadable weight reads its step", null, state.settings.pageWeight)
        assertEquals("an unreadable margin is none", 0, state.settings.marginPoints)
        assertEquals("an unreadable face is Literata", "literata", state.settings.typeface)
        // And everything beside them is kept.
        assertEquals("text size", 26.0, state.settings.scriptureSize, 0.0001)
        assertEquals("line spacing", 2, state.settings.lineSpacingStep)
        assertTrue("red letter", state.settings.redLetter)
        assertEquals("quiet hours start", 1320, state.settings.quietHoursStart)
        assertEquals("quiet hours end", 480, state.settings.quietHoursEnd)
        assertEquals("weight", 2, state.settings.weightStep)
        assertTrue("a new line for every verse", state.settings.versePerLine)
        assertTrue("clearer verse numbers", state.settings.clearVerseNumbers)
        assertEquals("line spacing slid", 184, state.settings.lineHeightHundredths)
        assertEquals("letter spacing", 15, state.settings.letterSpacingThousandths)
        assertEquals("the page's weight is its step's", 470, state.settings.weight(boldText = false))
        assertTrue("the margin hint stays seen", state.hasSeenMarginHint)
        assertTrue("the unreadable file is kept aside", kept.exists())
    }

    /**
     * §6.1: the app asks once. A hint or a permission prompt that comes back
     * is worse than one that never appeared, so these ride out with the
     * settings.
     */
    @Test fun theAskedOnceFlagsSurviveToo() = runTest {
        stateFile.writeText(damagedAroundGoodSettings)

        val state = LocalStore(context).load()

        assertTrue("the margin hint stays seen", state.hasSeenMarginHint)
        assertTrue("the fire stays pulled", state.hasPulledTheFire)
        // A release told about once is not told about again (A61).
        assertEquals("what's new stays seen", "2026-10-original", state.whatsNewSeen)
        assertEquals(
            "an invite already handed out is still handed out",
            1, state.invitesHandedOut.size,
        )
    }

    /** What is a cache stays lost, deliberately: the next sync is the copy. */
    @Test fun whatTheBackendHasIsNotSalvaged() = runTest {
        stateFile.writeText(damagedAroundGoodSettings)

        val state = LocalStore(context).load()

        assertTrue("no half-decoded rooms are carried forward", state.rooms.isEmpty())
        assertTrue("and nothing has been notified about yet", state.notifiedThrough == null)
    }

    /**
     * The evidence. A save follows a load within moments, so the old
     * behaviour destroyed the broken file before anybody could look at it —
     * which is why this had no diagnosis, only a symptom.
     */
    @Test fun theUnreadableFileIsKept() = runTest {
        stateFile.writeText(damagedAroundGoodSettings)

        LocalStore(context).load()

        assertTrue("the broken file is kept aside", kept.exists())
        assertTrue(
            "and it is the file that was there",
            kept.readText().contains("\"scriptureSize\": 26.0"),
        )
    }

    /** Not even valid JSON — a truncated write. Nothing to salvage, but no crash. */
    @Test fun aTruncatedFileIsEmptyRatherThanFatal() = runTest {
        stateFile.writeText("""{"settings":{"scriptureSize":26.0,"lineSp""")

        val state = LocalStore(context).load()

        assertEquals(AppState(), state)
        assertTrue("and it is still kept", kept.exists())
    }

    /** The ordinary case is untouched: a good file loads, and nothing is kept. */
    @Test fun aGoodFileIsNotTreatedAsDamage() = runTest {
        val store = LocalStore(context)
        store.save(AppState(settings = AppSettings(scriptureSize = 21.0, redLetter = true)))

        val state = LocalStore(context).load()

        assertEquals("text size", 21.0, state.settings.scriptureSize, 0.0001)
        assertTrue("red letter", state.settings.redLetter)
        assertFalse("nothing was salvaged", kept.exists())
    }
}
