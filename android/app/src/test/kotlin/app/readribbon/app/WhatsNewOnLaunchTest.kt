package app.readribbon.app

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.readribbon.carriesSomewhereToGo
import app.readribbon.isPlainLaunch
import app.readribbon.core.Membership
import app.readribbon.core.Person
import app.readribbon.core.Room
import app.readribbon.core.WhatsNew
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.screens.LOOP_MS
import app.readribbon.screens.STILL_AT
import app.readribbon.screens.followingFrame
import app.readribbon.screens.loopTime
import app.readribbon.screens.originalFrame
import app.readribbon.screens.ownVersionFrame
import app.readribbon.services.Notifications
import app.readribbon.services.LocalPresenceService
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What's new on launch (A61): the phone's half of `WhatsNew` — what it knew
 * before this launch, what one launch decides, and what it remembers after.
 * The rules themselves are the core's and tested there; these hold the
 * wiring, which is where a fresh install can be mistaken for an update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WhatsNewOnLaunchTest {

    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val room = Room(name = null, createdAt = now - 40.hours)
    private val latest = WhatsNew.releases.first()

    private fun onboarded(seen: String? = null) = AppState(
        me = me,
        people = mapOf(me.id to me),
        rooms = listOf(room),
        memberships = listOf(Membership(roomID = room.id, personID = me.id, joinedAt = now - 40.hours)),
        currentRoomID = room.id,
        whatsNewSeen = seen,
    )

    private fun model(state: AppState): AppModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return AppModel(
            context = context,
            initialState = state,
            store = LocalStore(context),
            presence = LocalPresenceService(),
        )
    }

    @Test fun anUpdateFromBeforeTheScreenIsShownItOnce() {
        val model = model(onboarded())
        model.decideWhatsNew(plainLaunch = true)
        assertEquals(latest, model.whatsNew)

        model.whatsNewShown()
        assertEquals("seen once it is in front of them", latest.id, model.state.whatsNewSeen)

        model.leaveWhatsNew()
        assertNull("and gone once they leave", model.whatsNew)

        val next = model(model.state)
        next.decideWhatsNew(plainLaunch = true)
        assertNull("the next launch goes straight to the room", next.whatsNew)
    }

    @Test fun everyWayOutRecordsItEvenIfTheMarkNeverLifted() {
        val model = model(onboarded())
        model.decideWhatsNew(plainLaunch = true)
        model.leaveWhatsNew()
        assertEquals(latest.id, model.state.whatsNewSeen)
    }

    @Test fun aFreshInstallIsNeverToldAndRecordsTheRelease() {
        val model = model(AppState())
        model.decideWhatsNew(plainLaunch = true)
        assertNull(model.whatsNew)
        assertEquals(latest.id, model.state.whatsNewSeen)
    }

    /**
     * The person the way in creates is not history. Asked after onboarding
     * had made one, a fresh install would read as an update and be toured.
     */
    @Test fun aPersonMadeDuringThisLaunchIsNotHistory() {
        val model = model(AppState())
        runBlocking { model.completeOnboarding(name = "Jonathan", portraitData = null) }
        assertTrue("the way in made a person", model.me != null)
        model.decideWhatsNew(plainLaunch = true)
        assertNull(model.whatsNew)
        assertEquals(latest.id, model.state.whatsNewSeen)
    }

    /**
     * Deleting the account takes the phone back to the four questions, and
     * the next person through them is a fresh install, not an update.
     */
    @Test fun aPhoneWipedBackToTheWayInIsAFreshInstall() {
        val model = model(onboarded())
        model.deleteAccount(keepNotesBehind = true)
        assertNull(model.me)
        assertEquals(latest.id, model.state.whatsNewSeen)

        val next = model(model.state.copy(me = me, people = mapOf(me.id to me)))
        next.decideWhatsNew(plainLaunch = true)
        assertNull("not told about an app they have only just met", next.whatsNew)
    }

    @Test fun aLaunchFromATapWaitsAndRecordsNothing() {
        val model = model(onboarded())
        model.decideWhatsNew(plainLaunch = false)
        assertNull(model.whatsNew)
        assertNull(model.state.whatsNewSeen)
        model.leaveWhatsNew()
        assertNull("leaving a screen that never came records nothing", model.state.whatsNewSeen)

        val plain = model(model.state)
        plain.decideWhatsNew(plainLaunch = true)
        assertEquals("the next plain launch is told", latest, plain.whatsNew)
    }

    @Test fun anOlderReleaseSeenShowsTheLatest() {
        val model = model(onboarded(seen = "2020-01-something-gone"))
        model.decideWhatsNew(plainLaunch = true)
        assertEquals(latest, model.whatsNew)
    }

    @Test fun aLaunchDecidesOnce() {
        val model = model(onboarded())
        model.decideWhatsNew(plainLaunch = false)
        model.decideWhatsNew(plainLaunch = true)
        assertNull(model.whatsNew)
    }

    @Test fun onlyALinkOrANotificationSendsALaunchSomewhere() {
        assertTrue("a tapped link", carriesSomewhereToGo(Intent.ACTION_VIEW))
        assertTrue("a notification or the home-screen fire", carriesSomewhereToGo(Notifications.ACTION_OPEN))
        assertFalse("the launcher", carriesSomewhereToGo(Intent.ACTION_MAIN))
        assertFalse("nothing at all", carriesSomewhereToGo(null))
    }

    /**
     * A rebuilt Activity is someone coming back to where they were, after
     * the process was reclaimed — not a launch. It waits, as a tap does.
     */
    @Test fun onlyANewActivityOpenedFromTheLauncherIsAPlainLaunch() {
        assertTrue("opened", isPlainLaunch(restored = false, action = Intent.ACTION_MAIN))
        assertFalse("rebuilt", isPlainLaunch(restored = true, action = Intent.ACTION_MAIN))
        assertFalse("sent by a link", isPlainLaunch(restored = false, action = Intent.ACTION_VIEW))
        assertFalse("sent by a notification", isPlainLaunch(restored = false, action = Notifications.ACTION_OPEN))
    }

    // The vignettes' clock (§12.3).

    @Test fun reduceMotionHoldsEachPictureAtItsEndState() {
        val word = originalFrame(STILL_AT)
        assertEquals(1f, word.press, 0f)
        assertEquals(1f, word.lift, 0f)
        assertEquals(1f, word.line, 0f)

        val versions = ownVersionFrame(STILL_AT)
        assertEquals(1f, versions.first, 0f)
        assertEquals(1f, versions.second, 0f)
        assertEquals(1f, versions.ink, 0f)

        val following = followingFrame(STILL_AT)
        assertEquals(1f, following.leader, 0f)
        assertEquals(1f, following.follower, 0f)
        assertEquals(1f, following.followerShown, 0f)
        assertEquals(1f, following.shown, 0f)
    }

    @Test fun eachLoopBeginsAndEndsAtRest() {
        for (t in listOf(0, LOOP_MS - 1)) {
            val word = originalFrame(t)
            assertEquals(0f, word.press + word.lift + word.line, 0.001f)
            val versions = ownVersionFrame(t)
            assertEquals(0f, versions.ink * (versions.first + versions.second), 0.001f)
            val following = followingFrame(t)
            assertEquals(0f, following.shown * (following.leader + following.followerShown), 0.001f)
        }
    }

    @Test fun theLoopIsAboutFourAndAHalfSecondsAndStaggered() {
        assertTrue(LOOP_MS in 4000..5000)
        assertEquals("a late vignette waits at rest", 0, loopTime(elapsedMs = 400, startAfter = 600))
        assertEquals(100, loopTime(elapsedMs = 700, startAfter = 600))
        assertEquals("and wraps", 100, loopTime(elapsedMs = 600L + LOOP_MS + 100, startAfter = 600))
    }
}
