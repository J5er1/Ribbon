@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.Handiwork
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPosition
import app.readribbon.core.Room
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.RibbonTheme
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A version is chosen by reading it, and the size is shown on the page
 * (S20, A67).
 *
 * Each version's row carries the verse you are at in its own words, read
 * off the main thread from what the phone already holds, and the group's
 * footnote says which verse that is. The preview under the size is a piece
 * of the page: that verse and the next, the second numbered the way the
 * page numbers it — a thin space after the number, never a word space. With
 * no book open, all of it is the first verses of John. And the slider says
 * the size as a size, in points, rather than how far along a bar it is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class VersionsByReadingTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val room = Room(createdAt = now)

    private val mark = Reading(
        roomID = room.id,
        bookID = "MRK",
        startedAt = now,
        handiwork = Handiwork(scale = FireScale.medium),
    )

    private fun show(open: Boolean) {
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(Membership(roomID = room.id, personID = me.id, joinedAt = now)),
            readings = if (open) listOf(mark) else emptyList(),
            positions = if (open) {
                listOf(
                    ReadingPosition(
                        readingID = mark.id,
                        personID = me.id,
                        chapter = 4,
                        verse = 12,
                        updatedAt = now,
                    ),
                )
            } else {
                emptyList()
            },
            currentRoomID = room.id,
        )
        val model = AppModel(context, state, LocalStore(context), LocalPresenceService())
        compose.setContent {
            RibbonTheme { TextSettingsScreen(model = model, onBack = {}) }
        }
        // The words are read off the main thread, so they arrive a moment
        // after the screen does.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(Copy.specimenAt(if (open) "Mark 4:12" else "John 1:1"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun withNoBookOpenEverythingIsShownAtJohn() {
        show(open = false)

        compose.onNodeWithText("John 1:1").assertExists()
        compose.onNode(hasText("2 He was with God in the beginning.", substring = true)).assertExists()
    }

    @Test fun theVersionsAreShownWhereYouAre() {
        show(open = true)

        compose.onNodeWithText("Mark 4:12").assertExists()
        compose.onNode(hasText("13 Then Jesus said to them", substring = true)).assertExists()
    }

    @Test fun theSizeIsSaidInPoints() {
        show(open = false)

        val slider = compose.onNodeWithContentDescription(Copy.TEXT_SIZE).fetchSemanticsNode()
        assertEquals(Copy.textSizeValue(19.0), slider.config[SemanticsProperties.StateDescription])
    }
}
