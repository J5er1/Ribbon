@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.FireScale
import app.readribbon.core.Handiwork
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.Ribbon
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.RibbonTheme
import app.readribbon.services.Destination
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * You, set as the front of the book (A67): a ribbon for each room you read
 * in, which takes you to that room; your shelf, once there is a book on it,
 * whose embers open their record inside You without offering to read the
 * book again; and the colophon at the end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class YouIsTheFrontOfTheBookTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val jo = Person(name = "Jo Marsh")

    /** A room of two, with no name of its own, reading Mark. */
    private val us = Room(createdAt = now - 40.hours)

    /** A room of your own, between books. */
    private val thursday = Room(name = "Thursday", createdAt = now - 200.hours)

    private val mark = Reading(
        roomID = us.id,
        bookID = "MRK",
        startedAt = now - 30.hours,
        handiwork = Handiwork(scale = FireScale.medium),
    )

    private val ruth = Reading(
        roomID = us.id,
        bookID = "RUT",
        startedAt = now - 300.hours,
        finishedAt = now - 100.hours,
        handiwork = Handiwork(scale = FireScale.small),
    )

    /** Where Jo marked Ruth, which the record quotes. */
    private val marked = VerseRange(bookID = "RUT", chapter = 1, startVerse = 16, endVerse = 16)

    private var dismissed = false
    private var switchedTo: Uuid? = null

    private fun model(finished: Boolean): AppModel {
        val state = AppState(
            me = me,
            people = mapOf(jo.id to jo),
            rooms = listOf(us, thursday),
            memberships = listOf(
                Membership(roomID = us.id, personID = me.id, joinedAt = now - 40.hours),
                Membership(roomID = us.id, personID = jo.id, joinedAt = now - 39.hours),
                Membership(
                    roomID = thursday.id,
                    personID = me.id,
                    ink = Ink.teal,
                    joinedAt = now - 200.hours,
                ),
            ),
            readings = if (finished) listOf(ruth, mark) else listOf(mark),
            highlights = if (finished) {
                listOf(
                    Highlight(
                        readingID = ruth.id,
                        authorID = jo.id,
                        range = marked,
                        ink = Ink.clay,
                        createdAt = now - 120.hours,
                    ),
                )
            } else {
                emptyList()
            },
            ribbons = listOf(
                Ribbon(
                    readingID = mark.id,
                    personID = jo.id,
                    chapter = 4,
                    verse = 12,
                    placedAt = now - 3.hours,
                ),
            ),
            currentRoomID = us.id,
        )
        return AppModel(context, state, LocalStore(context), LocalPresenceService())
    }

    private fun openYou(m: AppModel) {
        val appearance = Appearance(context).apply { wallpaperColour = false }
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                MenuScreen(
                    model = m,
                    entry = MenuEntry.YOU,
                    onDismiss = { dismissed = true },
                    onSwitch = { switchedTo = it },
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * Each ribbon says its room, where that room's ribbon lies (A30) and your
     * ink there where ink is yours; touching one goes to that room the way a
     * room row does, and the menu closes behind you.
     */
    @Test fun aRibbonSaysItsRoomAndGoesThere() {
        val m = model(finished = false)
        openYou(m)

        val ours = compose.onNodeWithContentDescription(
            Copy.ribbonSpoken(
                m.displayName(us),
                VerseAddress(bookID = "MRK", chapter = 4, verse = 12).chapterFormatted,
                null,
            ),
        )
        ours.assertExists()
        assertEquals(
            "what touching it does is said as the action",
            Copy.GOES_TO_THAT_ROOM,
            ours.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )

        compose.onNodeWithContentDescription(
            Copy.ribbonSpoken("Thursday", Copy.BETWEEN_BOOKS, Ink.teal.displayName),
        ).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("the book over the room is put down", thursday.id, switchedTo)
        assertEquals(thursday.id, m.state.currentRoomID)
        assertTrue("the menu closes behind you", dismissed)
    }

    /** No shelf until a book is finished; the colophon at the end, always. */
    @Test fun noShelfUntilABookIsFinishedAndTheColophonAtTheEnd() {
        openYou(model(finished = false))

        compose.onNodeWithText(Copy.YOUR_RIBBONS).assertExists()
        compose.onNodeWithText(Copy.YOUR_SHELF).assertDoesNotExist()
        compose.onNodeWithText(Copy.COLOPHON_SET_IN).performScrollTo().assertExists()
        compose.onNodeWithText(Copy.ORIGINAL_CREDIT).performScrollTo().assertExists()
    }

    /**
     * An ember is said by its book and who it was read with — never a count —
     * and opens its record inside You. The record does not offer to read the
     * book again, which belongs to the room; a verse quoted in it opens the
     * book over its own room, as a tapped notification does.
     */
    @Test fun anEmberOpensItsRecordAndAVerseOpensTheBook() {
        val m = model(finished = true)
        openYou(m)

        compose.onNodeWithText(Copy.YOUR_SHELF).assertExists()
        compose.onNodeWithContentDescription(
            Copy.emberSpoken("Ruth", Copy.shelfWith(listOf("Jo"), andOthers = false)),
        ).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ruth").assertExists()
        compose.onNodeWithText(Copy.READ_IT_AGAIN).assertDoesNotExist()

        compose.onNodeWithText(marked.formatted).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(
            Destination.Verse(roomID = us.id, readingID = ruth.id, verse = marked.start),
            m.pendingDestination,
        )
        assertTrue("the menu goes, so the book can open", dismissed)
    }
}
