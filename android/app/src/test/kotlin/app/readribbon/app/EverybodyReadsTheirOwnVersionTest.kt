package app.readribbon.app

import androidx.test.core.app.ApplicationProvider
import app.readribbon.core.FireScale
import app.readribbon.core.Handiwork
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.Room
import app.readribbon.core.TranslationID
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Everybody in a room reads their own version again (A60, reversing A42).
 * The room and the book still carry a version, for the builds that set a
 * page from it; this one sets a page from the person, and choosing a version
 * changes nobody's page but yours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EverybodyReadsTheirOwnVersionTest {

    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan", translation = TranslationID.web)
    private val room = Room(name = null, createdAt = now - 40.hours, translation = TranslationID.bsb)
    private val open = Reading(
        roomID = room.id, bookID = "MRK", startedAt = now - 30.hours,
        handiwork = Handiwork(scale = FireScale.medium), translation = TranslationID.bsb,
    )

    private fun model(): AppModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AppModel(
            context = context,
            initialState = AppState(
                me = me,
                people = mapOf(me.id to me),
                rooms = listOf(room),
                memberships = listOf(Membership(roomID = room.id, personID = me.id, joinedAt = now - 40.hours)),
                readings = listOf(open),
                currentRoomID = room.id,
            ),
            store = LocalStore(context),
            presence = LocalPresenceService(),
        )
    }

    @Test fun thePageIsSetInYourVersionNotTheRooms() {
        assertEquals(TranslationID.web, model().words(room))
    }

    @Test fun choosingAVersionChangesOnlyYours() {
        val model = model()
        model.setTranslation(TranslationID.nkjv)
        assertEquals(TranslationID.nkjv, model.me?.translation)
        assertEquals(TranslationID.nkjv, model.words(room))
        assertEquals("the room keeps its own", TranslationID.bsb, model.state.rooms.single().translation)
        assertEquals("the open book keeps its own", TranslationID.bsb, model.state.readings.single().translation)
    }

    @Test fun aNewBookIsSeededFromThePersonForOlderBuilds() {
        val model = model()
        val reading = model.startReading("JHN", model.state.rooms.single())
        assertEquals(TranslationID.web, reading.translation)
    }
}
