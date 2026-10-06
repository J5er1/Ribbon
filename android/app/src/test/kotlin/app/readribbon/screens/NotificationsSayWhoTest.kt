@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import app.readribbon.data.AppSettings
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.RibbonTheme
import app.readribbon.services.LocalPresenceService
import java.util.Date
import java.util.TimeZone
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Notifications say who (S19, A66).
 *
 * In a room of two "they" is one person, so the switches name her — in the
 * words her notifications will use, the first word of her name — and the
 * two that carry a verse or a book show the notification itself under the
 * switch rather than describing it. A room of three or more reads exactly as
 * it did. The look book can only photograph one of these at a time and
 * cannot hear any of them, so what is asserted here is the part a picture
 * misses: which sentences are there, which are gone, that the example is
 * spoken as the words it is, and that the room's faces say nothing — read
 * aloud they would be a roll call, and a room is never counted (Law 2).
 *
 * And the quiet hours, which are one tile now with the night drawn under
 * it: its title says the two times as a sentence, or that there are none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class NotificationsSayWhoTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val ruth = Person(name = "Ruth Alderman")
    private val ann = Person(name = "Ann Brooke")
    private val room = Room(name = "Us", createdAt = now)

    private fun membership(person: Person) =
        Membership(roomID = room.id, personID = person.id, joinedAt = now)

    private val mark = Reading(
        roomID = room.id,
        bookID = "MRK",
        startedAt = now,
        handiwork = Handiwork(scale = FireScale.medium),
    )

    private fun show(
        people: List<Person>,
        open: Boolean = true,
        settings: AppSettings = AppSettings(),
    ) {
        val state = AppState(
            me = me,
            people = (listOf(me) + people).associateBy { it.id },
            rooms = listOf(room),
            memberships = (listOf(me) + people).map { membership(it) },
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
            settings = settings,
        )
        val model = AppModel(context, state, LocalStore(context), LocalPresenceService())
        compose.setContent {
            RibbonTheme { NotificationSettingsScreen(model = model, onBack = {}) }
        }
        compose.waitForIdle()
    }

    @Test fun aRoomOfTwoNamesTheOtherPerson() {
        show(people = listOf(ruth))

        compose.onNodeWithText(Copy.whenNameOpensTheBook("Ruth")).assertExists()
        compose.onNodeWithText(Copy.WHEN_THEY_OPEN_THE_BOOK).assertDoesNotExist()
        compose.onNodeWithText(Copy.cardsOpenSubNamed("Ruth")).assertExists()
        compose.onNodeWithText(Copy.thinkingOfYouSubNamed("Ruth")).assertExists()

        // The two notifications that carry a place are shown, spoken as the
        // words they are, and the sentences that described them are gone.
        compose.onNodeWithContentDescription(
            Copy.notificationExampleSpoken(Copy.notifNoteLeft("Ruth", "Mark 4:12")),
        ).assertExists()
        compose.onNodeWithContentDescription(
            Copy.notificationExampleSpoken(Copy.notifReading("Ruth", "Mark")),
        ).assertExists()
        compose.onNodeWithText(Copy.NOTES_LEFT_FOR_YOU_SUB).assertDoesNotExist()
        compose.onNodeWithText(Copy.WHEN_THEY_OPEN_THE_BOOK_SUB).assertDoesNotExist()
    }

    @Test fun theFacesSayNothingAndTheRoomIsStillAHeading() {
        show(people = listOf(ruth, ann))

        compose.onAllNodesWithContentDescription(ruth.name).assertCountEquals(0)
        compose.onAllNodesWithContentDescription(ann.name).assertCountEquals(0)
        compose.onAllNodesWithContentDescription(me.name).assertCountEquals(0)
        compose.onNode(hasText(room.name!!) and isHeading()).assertExists()
    }

    @Test fun withNoBookOpenARoomOfTwoStillNamesHer() {
        show(people = listOf(ruth), open = false)

        // Nothing to put in the notification yet, so the switch says what it
        // always said — under her name where the title is about her.
        compose.onNodeWithText(Copy.NOTES_LEFT_FOR_YOU_SUB).assertExists()
        compose.onNodeWithText(Copy.whenNameOpensTheBook("Ruth")).assertExists()
        compose.onNodeWithText(Copy.WHEN_THEY_OPEN_THE_BOOK_SUB).assertExists()
    }

    @Test fun aRoomOfThreeReadsAsItAlwaysDid() {
        show(people = listOf(ruth, ann))

        compose.onNodeWithText(Copy.NOTES_LEFT_FOR_YOU_SUB).assertExists()
        compose.onNodeWithText(Copy.CARDS_OPEN_SUB).assertExists()
        compose.onNodeWithText(Copy.WHEN_THEY_OPEN_THE_BOOK).assertExists()
        compose.onNodeWithText(Copy.WHEN_THEY_OPEN_THE_BOOK_SUB).assertExists()
        compose.onNodeWithText(Copy.THINKING_OF_YOU_SUB).assertExists()
        compose.onNodeWithText(Copy.whenNameOpensTheBook("Ruth")).assertDoesNotExist()
    }

    @Test fun theQuietHoursAreSaidAsASentence() {
        show(people = listOf(ruth))

        compose.onNodeWithText(Copy.quietHoursFromUntil(clock(22 * 60), clock(6 * 60))).assertExists()
        compose.onNodeWithText(Copy.NO_QUIET_HOURS).assertDoesNotExist()
    }

    @Test fun bothEndsOnOneMinuteIsNoQuietHours() {
        show(people = listOf(ruth), settings = AppSettings(quietHoursStart = 9 * 60, quietHoursEnd = 9 * 60))

        compose.onNodeWithText(Copy.NO_QUIET_HOURS).assertExists()
    }

    /** The phone's own clock, as the band speaks its ends: UTC, on day one. */
    private fun clock(minute: Int): String {
        val format = DateFormat.getTimeFormat(context)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(minute * 60_000L))
    }
}
