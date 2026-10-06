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
import app.readribbon.core.WhatsNewItem
import app.readribbon.screens.LONG_LOOP_MS
import app.readribbon.screens.LOOP_MS
import app.readribbon.screens.STILL_AT
import app.readribbon.screens.body
import app.readribbon.screens.followingFrame
import app.readribbon.screens.loopTime
import app.readribbon.screens.lordFrame
import app.readribbon.screens.originalFrame
import app.readribbon.screens.ownVersionFrame
import app.readribbon.screens.readableFrame
import app.readribbon.screens.roomGroupsFrame
import app.readribbon.screens.selectionFrame
import app.readribbon.screens.staysFrame
import app.readribbon.screens.timeline
import app.readribbon.screens.title
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

    // The second release's four (A65).

    @Test fun reduceMotionHoldsTheNewPicturesAtTheirEndStates() {
        val stays = staysFrame(WhatsNewItem.followingStays.timeline.stillAt)
        assertEquals("Ruth's line on the last words", 2f, stays.leader, 0f)
        assertEquals("and yours on the same ones", 2f, stays.follower, 0f)
        assertEquals(1f, stays.leaderShown * stays.followerShown * stays.shown, 0f)
        assertEquals("and the screen still awake", 1f, stays.awake, 0f)

        val selection = selectionFrame(WhatsNewItem.nativeSelection.timeline.stillAt)
        assertEquals("the whole verse taken", 1f, selection.verse, 0f)
        assertEquals(1f, selection.selected * selection.reach * selection.shown, 0f)
        assertEquals("no finger left on it", 0f, selection.wordPress + selection.numberPress, 0f)

        val room = roomGroupsFrame(WhatsNewItem.roomGroups.timeline.stillAt)
        assertEquals(1f, room.wash * room.others * room.shown, 0f)
        assertTrue("all three faces", room.faces.all { it == 1f })

        assertEquals("LORD as printed", 1f, lordFrame(WhatsNewItem.lordReadsLord.timeline.stillAt).name, 0f)
    }

    @Test fun eachNewLoopBeginsAndEndsAtRest() {
        for (t in listOf(0, WhatsNewItem.followingStays.timeline.loopMs - 1)) {
            val stays = staysFrame(t)
            assertEquals(0f, stays.shown * (stays.leaderShown + stays.followerShown), 0.001f)
            assertEquals("awake at rest", 1f, stays.awake, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.nativeSelection.timeline.loopMs - 1)) {
            val selection = selectionFrame(t)
            assertEquals(0f, selection.shown * selection.selected + selection.wordPress + selection.numberPress, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.roomGroups.timeline.loopMs - 1)) {
            val room = roomGroupsFrame(t)
            assertEquals(0f, room.shown * (room.wash + room.others + room.faces.sum()), 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.lordReadsLord.timeline.loopMs - 1)) {
            assertEquals(0f, lordFrame(t).name, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.originalReadable.timeline.loopMs - 1)) {
            val readable = readableFrame(t)
            assertEquals(0f, readable.press + readable.wash + readable.bare + readable.ground + readable.grow, 0.001f)
        }
    }

    // The release's fifth (A66): the held word's line, readable over the page.

    @Test fun theHeldLineRestsThenEndsOnItsGround() {
        val timeline = WhatsNewItem.originalReadable.timeline
        assertEquals("a press, a bare line, a mend: the longer loop", LONG_LOOP_MS, timeline.loopMs)

        val rest = readableFrame(0)
        assertEquals("nothing moved at rest", 0f, rest.press + rest.wash + rest.bare + rest.ground + rest.grow, 0f)

        val still = readableFrame(timeline.stillAt)
        assertEquals("the word held and selected", 1f, still.press * still.wash, 0f)
        assertEquals("its line there, on its ground", 1f, still.bare * still.ground, 0f)
        assertEquals("the Hebrew grown and at full strength", 1f, still.grow, 0f)

        assertEquals("settled back before it wraps", rest, readableFrame(timeline.loopMs - 1))
    }

    /**
     * The trouble is seen before it is mended: the line arrives bare over
     * the verse above, with the Hebrew small and muted, and is left there for
     * most of a second; then its ground and the larger, fuller Hebrew come
     * together, on one beat.
     */
    @Test fun theHeldLineIsSeenBareBeforeItsGroundComes() {
        val frames = (0 until LONG_LOOP_MS).map(::readableFrame)
        val selected = frames.indexOfFirst { it.wash == 1f }
        val bare = frames.indexOfFirst { it.bare == 1f }
        val mending = frames.indexOfFirst { it.ground > 0f }
        val mended = frames.indexOfFirst { it.ground == 1f }
        assertTrue("the word is taken before its line comes", selected <= bare)
        assertEquals("bare: no ground under it", 0f, frames[bare].ground, 0f)
        assertEquals("and the Hebrew at its old size, muted", 0f, frames[bare].grow, 0f)
        assertTrue("held bare for most of a second (${mending - bare} ms)", mending - bare in 800..1100)
        assertTrue("mended on one beat (${mended - mending} ms)", mended - mending + 1 in 320..480)
        assertTrue("the ground and the word together", frames.all { it.ground == it.grow })
    }

    /**
     * The follower's screen begins to dim between Ruth's second step and her
     * third, and is full again before she has finished it — kept on, not
     * woken.
     */
    @Test fun theFollowersScreenDimsAndIsKeptOn() {
        val dimmest = (0 until LONG_LOOP_MS step 20).minBy { staysFrame(it).awake }
        val frame = staysFrame(dimmest)
        assertTrue("it dims", frame.awake < 0.6f)
        assertTrue("never dark", frame.awake > 0.3f)
        assertTrue("after the second step", frame.leader >= 1f && frame.follower >= 1f)
        assertTrue("before the third", frame.leader < 1.5f)
        val awakeAgain = (dimmest until LONG_LOOP_MS step 20).first { staysFrame(it).awake == 1f }
        assertTrue("awake before the follower moves on", staysFrame(awakeAgain).follower <= 1f)
    }

    /**
     * The new pictures breathe as the first release's do: once everything
     * has arrived, nearly two seconds of nothing moving before the loop lets
     * go — and they are at rest again before it wraps.
     */
    @Test fun theNewPicturesBreatheAsLongAsTheFirst() {
        fun stillFor(loop: Int, stillAt: Int, frame: (Int) -> Any): Int {
            val end = frame(stillAt)
            val from = (stillAt downTo 1).first { frame(it - 1) != end }
            val to = (stillAt until loop).first { frame(it) != end }
            return to - from
        }
        for ((item, frame) in listOf<Pair<WhatsNewItem, (Int) -> Any>>(
            WhatsNewItem.followingStays to ::staysFrame,
            WhatsNewItem.nativeSelection to ::selectionFrame,
            WhatsNewItem.roomGroups to ::roomGroupsFrame,
            WhatsNewItem.lordReadsLord to ::lordFrame,
            WhatsNewItem.originalReadable to ::readableFrame,
        )) {
            val timeline = item.timeline
            val breath = stillFor(timeline.loopMs, timeline.stillAt, frame)
            assertTrue("$item holds still for $breath ms", breath in 1600..2400)
            assertEquals("$item is at rest before it wraps", frame(timeline.loopMs - 50), frame(timeline.loopMs - 1))
        }
    }

    @Test fun everyItemHasItsWordsAndALoop() {
        for (item in WhatsNewItem.entries) {
            assertTrue(item.title.isNotBlank())
            assertTrue(item.body.isNotBlank())
            assertTrue(item.timeline.stillAt in 1 until item.timeline.loopMs)
        }
    }

    @Test fun everyReleaseHasItsOwnTitleAndDay() {
        for (release in WhatsNew.releases) {
            assertFalse("${release.id} has its own title", Copy.whatsNewTitle(release.id) == Copy.WHATS_NEW_HEADING)
            assertTrue("${release.id} has a day", Copy.whatsNewReleased(release.released) != null)
        }
        assertEquals("6 October 2026", Copy.whatsNewReleased("2026-10-06"))
        assertEquals("Staying on the same page", Copy.whatsNewTitle("2026-10-following"))
        assertEquals("The words under the words", Copy.whatsNewTitle("2026-10-original"))
        assertEquals("an unknown release is headed by the screen", Copy.WHATS_NEW_HEADING, Copy.whatsNewTitle("gone"))
        assertNull(Copy.whatsNewReleased(""))
    }

    @Test fun theLoopIsAboutFourAndAHalfSecondsAndStaggered() {
        assertTrue(LOOP_MS in 4000..5000)
        assertEquals("a late vignette waits at rest", 0, loopTime(elapsedMs = 400, startAfter = 600))
        assertEquals(100, loopTime(elapsedMs = 700, startAfter = 600))
        assertEquals("and wraps", 100, loopTime(elapsedMs = 600L + LOOP_MS + 100, startAfter = 600))
        assertEquals("a longer loop wraps at its own length", 100, loopTime(600L + LONG_LOOP_MS + 100, 600, LONG_LOOP_MS))
    }
}
