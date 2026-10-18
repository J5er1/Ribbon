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
import app.readribbon.design.RibbonMotion
import app.readribbon.screens.LONG_LOOP_MS
import app.readribbon.screens.LOOP_MS
import app.readribbon.screens.STILL_AT
import app.readribbon.screens.body
import app.readribbon.screens.flyleafFrame
import app.readribbon.screens.followingFrame
import app.readribbon.screens.lightFrame
import app.readribbon.screens.loopTime
import app.readribbon.screens.lordFrame
import app.readribbon.screens.nightFrame
import app.readribbon.screens.notificationsFrame
import app.readribbon.screens.numbersFrame
import app.readribbon.screens.originalFrame
import app.readribbon.screens.ownVersionFrame
import app.readribbon.screens.quotedFrame
import app.readribbon.screens.readableFrame
import app.readribbon.screens.pageFrame
import app.readribbon.screens.typefaceFrame
import app.readribbon.screens.roomGroupsFrame
import app.readribbon.screens.selectionFrame
import app.readribbon.screens.shelfFrame
import app.readribbon.screens.staysFrame
import app.readribbon.screens.timeline
import app.readribbon.screens.title
import app.readribbon.screens.versionsFrame
import app.readribbon.screens.wavesFrame
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
        assertEquals(
            "told with the release it came in, not the newest",
            "2026-10-following",
            WhatsNew.releases.single { WhatsNewItem.originalReadable in it.items }.id,
        )
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

    // The third release's six (A67, and A68's page).

    @Test fun reduceMotionHoldsTheFrontOfTheBookAtItsEnd() {
        val flyleaf = flyleafFrame(WhatsNewItem.flyleaf.timeline.stillAt)
        assertTrue("every room's ribbon laid in", flyleaf.ribbons.all { it == 1f })
        assertEquals(1f, flyleaf.shown, 0f)

        val shelf = shelfFrame(WhatsNewItem.yourShelf.timeline.stillAt)
        assertTrue("every book on the shelf", shelf.embers.all { it == 1f })
        assertEquals(1f, shelf.shown, 0f)

        val versions = versionsFrame(WhatsNewItem.versionsByReading.timeline.stillAt)
        assertEquals("the ribbon in the version chosen by its words", 1f, versions.second, 0f)
        assertEquals("and out of the other", 0f, versions.first, 0f)

        val notifications = notificationsFrame(WhatsNewItem.notificationsByName.timeline.stillAt)
        assertEquals("the switch on", 1f, notifications.on, 0f)
        assertEquals("and the words it lets through written", 1f, notifications.written * notifications.said, 0f)

        val night = nightFrame(WhatsNewItem.quietHoursNight.timeline.stillAt)
        assertEquals("the night drawn", 1f, night.drawn * night.shown, 0f)
        assertEquals("from ten in the evening, as it was set", 0f, night.later, 0f)

        val page = pageFrame(WhatsNewItem.yourPage.timeline.stillAt)
        assertEquals("a verse to a line", 1f, page.lines, 0f)
        assertEquals("the numbers clear", 1f, page.clear, 0f)
        assertEquals("and the letters heavier", 1f, page.heavier, 0f)

        val faces = typefaceFrame(WhatsNewItem.typeface.timeline.stillAt)
        assertEquals("the ribbon come down to the last typeface", 4f, faces.row, 0f)
    }

    @Test fun eachFrontOfTheBookLoopBeginsAndEndsAtRest() {
        for (t in listOf(0, WhatsNewItem.flyleaf.timeline.loopMs - 1)) {
            val flyleaf = flyleafFrame(t)
            assertEquals("no ribbon showing", 0f, flyleaf.shown * flyleaf.ribbons.sum(), 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.yourShelf.timeline.loopMs - 1)) {
            val shelf = shelfFrame(t)
            assertEquals("an empty shelf", 0f, shelf.shown * shelf.embers.sum(), 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.versionsByReading.timeline.loopMs - 1)) {
            val versions = versionsFrame(t)
            assertEquals("the ribbon at rest in the first", 1f, versions.first, 0.001f)
            assertEquals(0f, versions.second, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.notificationsByName.timeline.loopMs - 1)) {
            val notifications = notificationsFrame(t)
            assertEquals("the switch off", 0f, notifications.on, 0.001f)
            assertEquals("and nothing said", 0f, notifications.written * notifications.said, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.quietHoursNight.timeline.loopMs - 1)) {
            val night = nightFrame(t)
            assertEquals("no night on the band", 0f, night.drawn * night.shown, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.yourPage.timeline.loopMs - 1)) {
            val page = pageFrame(t)
            assertEquals("one paragraph", 0f, page.lines, 0.001f)
            assertEquals("its numbers quiet", 0f, page.clear, 0.001f)
            assertEquals("at Book", 0f, page.heavier, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.typeface.timeline.loopMs - 1)) {
            assertEquals("the ribbon on Literata", 0f, typefaceFrame(t).row, 0.001f)
        }
    }

    /** Your rooms' ribbons, and the embers on the shelf, come one after another — never a later one ahead. */
    @Test fun theRibbonsAndTheEmbersComeInTurn() {
        for (t in 0 until LOOP_MS step 10) {
            val ribbons = flyleafFrame(t).ribbons
            assertEquals("the ribbons at $t", ribbons.sortedDescending(), ribbons)
            val embers = shelfFrame(t).embers
            assertEquals("the embers at $t", embers.sortedDescending(), embers)
        }
    }

    /**
     * The page is set again one way at a time (A68): a verse to a line, then
     * the numbers, then the weight — each arrived before the next begins, so
     * the eye is never asked to follow two changes at once.
     */
    @Test fun thePageIsSetAgainOneWayAtATime() {
        for (t in 0 until WhatsNewItem.yourPage.timeline.stillAt) {
            val page = pageFrame(t)
            if (page.clear > 0f) assertEquals("the lines are set before the numbers brighten, at $t", 1f, page.lines, 0f)
            if (page.heavier > 0f) assertEquals("the numbers are clear before the weight comes, at $t", 1f, page.clear, 0f)
        }
    }

    /** One ribbon marks the version you read: it lifts out of one row before it is laid into the other. */
    @Test fun theVersionsRibbonIsInOneRowAtATime() {
        for (t in 0 until WhatsNewItem.versionsByReading.timeline.loopMs) {
            val versions = versionsFrame(t)
            assertEquals("at $t", 0f, versions.first * versions.second, 0f)
        }
    }

    /**
     * The night's start is moved on an hour with the whole night drawn,
     * rests there as long as a picture rests, and is back at ten before the
     * night fades.
     */
    @Test fun theNightsStartIsMovedAnHourAndBack() {
        val loop = WhatsNewItem.quietHoursNight.timeline.loopMs
        val atEleven = (0 until loop).filter { nightFrame(it).later == 1f }
        assertTrue("it rests at eleven", atEleven.size >= 1600)
        assertTrue("with the night drawn", atEleven.all { nightFrame(it).drawn * nightFrame(it).shown == 1f })
        assertTrue(
            "and is back at ten before it fades",
            (0 until loop).filter { nightFrame(it).shown < 1f }.all { nightFrame(it).later == 0f },
        )
    }

    // The fourth release's four (A70).

    @Test fun reduceMotionHoldsTheFinerPointsAtTheirEnds() {
        assertEquals("the ember's light up round it", 1f, lightFrame(WhatsNewItem.softLight.timeline.stillAt).lit, 0f)

        val quoted = quotedFrame(WhatsNewItem.quotedMarks.timeline.stillAt)
        assertEquals("the verse quoted", 1f, quoted.quote * quoted.shown, 0f)
        assertEquals("and the line that stood in for it gone", 0f, quoted.waiting, 0f)

        val numbers = numbersFrame(WhatsNewItem.numbersStay.timeline.stillAt)
        assertEquals("the number gone down with its word", 1f, numbers.settled * numbers.shown, 0f)

        val wave = wavesFrame(WhatsNewItem.wavesFill.timeline.stillAt)
        assertEquals("the bars across the whole note", 1f, wave.spread * wave.shown, 0f)
        assertEquals("nothing played, and no finger on it", 0f, wave.played + wave.press, 0f)
    }

    @Test fun eachFinerPointsLoopBeginsAndEndsAtRest() {
        for (t in listOf(0, WhatsNewItem.softLight.timeline.loopMs - 1)) {
            assertEquals("the ember alone, its light down", 0f, lightFrame(t).lit, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.quotedMarks.timeline.loopMs - 1)) {
            val quoted = quotedFrame(t)
            assertEquals("the quiet line in the quote's place", 1f, quoted.waiting * quoted.shown, 0.001f)
            assertEquals("and no quote yet", 0f, quoted.quote, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.numbersStay.timeline.loopMs - 1)) {
            val numbers = numbersFrame(t)
            assertEquals("the number up on the full line", 0f, numbers.settled, 0.001f)
            assertEquals("and the page there", 1f, numbers.shown, 0.001f)
        }
        for (t in listOf(0, WhatsNewItem.wavesFill.timeline.loopMs - 1)) {
            val wave = wavesFrame(t)
            assertEquals("the bars where they used to stand", 0f, wave.spread, 0.001f)
            assertEquals("nothing played or pressed", 0f, wave.played + wave.press, 0.001f)
            assertEquals("and the wave there", 1f, wave.shown, 0.001f)
        }
    }

    /**
     * Light coming up, and a drag watched bar by bar, are on the fire's long
     * curve and so on loops of their own, as the iPhone's are; the quote and
     * the number keep the first release's clock.
     */
    @Test fun theLightAndTheDragTakeTheLongCurve() {
        fun span(values: List<Float>) = values.indexOfFirst { it == 1f } - values.indexOfFirst { it > 0f } + 1
        val light = (0 until WhatsNewItem.softLight.timeline.loopMs).map { lightFrame(it).lit }
        assertTrue("the light comes up on it (${span(light)} ms)", span(light) in 2400..RibbonMotion.BECOME_MS)
        val drag = (0 until WhatsNewItem.wavesFill.timeline.loopMs).map { wavesFrame(it).played }
        assertTrue("and the drag goes along on it (${span(drag)} ms)", span(drag) in 2400..RibbonMotion.BECOME_MS)
        assertEquals("the quote's loop is the first release's", LOOP_MS, WhatsNewItem.quotedMarks.timeline.loopMs)
        assertEquals("and the number's", LOOP_MS, WhatsNewItem.numbersStay.timeline.loopMs)
    }

    /**
     * The line that stands in for the quote and the quote cross in one place,
     * on one arrive, as the record brings a verse in: one going as the other
     * comes, so the place is never empty and never holds both at full.
     */
    @Test fun theQuoteTakesTheLinesPlaceOnOneArrive() {
        val frames = (0 until WhatsNewItem.quotedMarks.timeline.loopMs).map(::quotedFrame)
        for ((t, quoted) in frames.withIndex()) {
            assertEquals("at $t", 1f, quoted.waiting + quoted.quote, 0.0001f)
        }
        val coming = frames.indexOfFirst { it.quote > 0f }
        val come = frames.indexOfFirst { it.quote == 1f }
        assertTrue("on one arrive (${come - coming + 1} ms)", come - coming + 1 in 300..RibbonMotion.ARRIVE_MS)
    }

    /**
     * Nothing these pictures show goes back where it can be seen: the quote
     * does not turn back into its line, the number does not go back up, and
     * the bars neither gather again nor empty. Each is set back as it began
     * only while its picture has faded right out.
     */
    @Test fun nothingGoesBackWhereItCanBeSeen() {
        fun check(what: String, loop: Int, value: (Int) -> Float, shown: (Int) -> Float) {
            for (t in 0 until loop - 1) {
                if (value(t + 1) < value(t) - 0.0001f) {
                    assertEquals("$what goes back only out of sight, at $t", 0f, shown(t) + shown(t + 1), 0f)
                }
            }
        }
        val quoted = WhatsNewItem.quotedMarks.timeline.loopMs
        check("the quote", quoted, { quotedFrame(it).quote }, { quotedFrame(it).shown })
        val numbers = WhatsNewItem.numbersStay.timeline.loopMs
        check("the number", numbers, { numbersFrame(it).settled }, { numbersFrame(it).shown })
        val waves = WhatsNewItem.wavesFill.timeline.loopMs
        check("the bars", waves, { wavesFrame(it).spread }, { wavesFrame(it).shown })
        check("the ink", waves, { wavesFrame(it).played }, { wavesFrame(it).shown })
    }

    /**
     * The bars stand across the note before a fingertip comes, and it is on
     * the wave the whole time the ink is filling: the ink goes only where a
     * finger takes it.
     */
    @Test fun theWaveIsSpreadAndThenDragged() {
        for (t in 0 until WhatsNewItem.wavesFill.timeline.loopMs) {
            val wave = wavesFrame(t)
            if (wave.shown < 1f) continue
            if (wave.press > 0f) assertEquals("spread before the finger lands, at $t", 1f, wave.spread, 0f)
            if (wave.played > 0f && wave.played < 1f) {
                assertEquals("the finger on it while it fills, at $t", 1f, wave.press, 0f)
            }
        }
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
            WhatsNewItem.flyleaf to ::flyleafFrame,
            WhatsNewItem.yourShelf to ::shelfFrame,
            WhatsNewItem.versionsByReading to ::versionsFrame,
            WhatsNewItem.notificationsByName to ::notificationsFrame,
            WhatsNewItem.quietHoursNight to ::nightFrame,
            WhatsNewItem.yourPage to ::pageFrame,
            WhatsNewItem.typeface to ::typefaceFrame,
            WhatsNewItem.softLight to ::lightFrame,
            WhatsNewItem.quotedMarks to ::quotedFrame,
            WhatsNewItem.numbersStay to ::numbersFrame,
            WhatsNewItem.wavesFill to ::wavesFrame,
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
        assertEquals("7 October 2026", Copy.whatsNewReleased("2026-10-07"))
        assertEquals("17 October 2026", Copy.whatsNewReleased("2026-10-17"))
        assertEquals("Light, and the finer points", Copy.whatsNewTitle("2026-10-light"))
        assertEquals("The front of the book", Copy.whatsNewTitle("2026-10-flyleaf"))
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
