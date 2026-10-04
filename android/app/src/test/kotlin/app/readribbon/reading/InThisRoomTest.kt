package app.readribbon.reading

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.Copy
import app.readribbon.core.Person
import app.readribbon.core.TranslationID
import app.readribbon.design.Appearance
import app.readribbon.design.RibbonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "In this room" to TalkBack (A62, §13.3): one element per block, reading
 * the words, then the versions, then every first name — the ones past "and
 * others" too — and nothing inside a block read on its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class InThisRoomTest {

    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val kjv = TranslationID("kjv")

    private fun seat(name: String, version: TranslationID) = RoomPerson(Person(name = name), version)

    private fun show(section: RoomSection) {
        val appearance = Appearance(context).apply { wallpaperColour = false }
        compose.setContent {
            RibbonTheme(appearance = appearance) { InThisRoom(section, wordsSize = 19f) }
        }
        compose.waitForIdle()
    }

    @Test fun eachBlockIsOneElement() {
        val section = roomSection(
            yours = TranslationID.bsb,
            me = seat("Jonathan", TranslationID.bsb),
            others = listOf(
                seat("Caleb", TranslationID.bsb), seat("Faith", TranslationID.bsb), seat("Isaac", TranslationID.bsb),
                seat("Ann", kjv), seat("Ben", kjv),
            ),
            saying = { if (it == kjv) RoomSaid("by him") else RoomSaid("Through Him") },
            versionName = { if (it == kjv) "King James" else "Berean Standard" },
        ) as RoomSection.Blocks
        show(section)

        val spoken = compose.onAllNodes(hasContentDescription("", substring = true))
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull() }
        assertEquals(section.blocks.map { it.spoken }, spoken)
        assertTrue(spoken[0].endsWith("you, Caleb, Faith and Isaac."))

        // The heading is a heading; the names beside the faces, the words
        // and the faces' own names are inside their block's one element.
        assertEquals(1, compose.onAllNodes(hasText(Copy.ORIGINAL_IN_THIS_ROOM)).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("you, Caleb, Faith and others")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasContentDescription("Caleb")).fetchSemanticsNodes().size)
    }

    @Test fun theAgreementIsOneElement() {
        val section = roomSection(
            yours = TranslationID.bsb,
            me = seat("Jonathan", TranslationID.bsb),
            others = listOf(seat("Ruth Alderman", TranslationID.web)),
            saying = { RoomSaid("the Word was with God") },
        )
        show(section)
        assertEquals(
            1,
            compose.onAllNodes(hasContentDescription("Ruth reads these words as you do.")).fetchSemanticsNodes().size,
        )
        assertEquals(0, compose.onAllNodes(hasText(Copy.ORIGINAL_IN_THIS_ROOM)).fetchSemanticsNodes().size)
    }
}
