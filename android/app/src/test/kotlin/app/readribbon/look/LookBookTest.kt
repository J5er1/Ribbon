package app.readribbon.look

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.readribbon.app.AppModel
import app.readribbon.core.Bible
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.FuelEvent
import app.readribbon.core.Handiwork
import app.readribbon.core.Ink
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPosition
import app.readribbon.core.Room
import app.readribbon.core.VerseAddress
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.RibbonTheme
import app.readribbon.design.rememberBookSheet
import app.readribbon.app.Copy
import app.readribbon.screens.AppearanceScreen
import app.readribbon.screens.BookChooserContent
import app.readribbon.screens.InviteContent
import app.readribbon.screens.OnboardingFlow
import app.readribbon.screens.MenuEntry
import app.readribbon.screens.MenuScreen
import app.readribbon.screens.NotificationSettingsScreen
import app.readribbon.screens.PersonScreen
import app.readribbon.reading.ReadingScreen
import app.readribbon.screens.RoomScreen
import app.readribbon.screens.TextSettingsScreen
import app.readribbon.services.LocalPresenceService
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The look book: every screen this pass touched, rendered to a PNG.
 *
 * Not an assertion suite — a way to *see* the app without a phone in the
 * room. It renders real screens against a real `AppModel` and writes the
 * frames to `ribbon.shots` (default `build/shots`), which is how the layout
 * work in this change was checked: the hearth's proportions, the seats and
 * the open seat, a settings tile with its subtitle, the segmented pill.
 *
 * It asserts only that each screen composes and draws something, because a
 * pixel assertion on a screen that is meant to be redesigned is a test that
 * has to be deleted every time the design is right.
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class LookBookTest {

    @get:Rule val compose = createComposeRule()

    private val out = File(System.getProperty("ribbon.shots") ?: "build/shots").apply { mkdirs() }

    private val now = Clock.System.now()
    private val me = Person(name = "Jonathan")
    private val ruth = Person(name = "Ruth Alderman")
    private val ann = Person(name = "Ann Brooke")
    private val room = Room(name = null, createdAt = now - 40.hours)

    private fun membership(person: Person, ink: Ink?) =
        Membership(roomID = room.id, personID = person.id, ink = ink, joinedAt = now - 40.hours)

    private fun reading(book: String, scale: FireScale) = Reading(
        roomID = room.id,
        bookID = book,
        startedAt = now - 30.hours,
        handiwork = Handiwork(
            scale = scale,
            coalDepth = 0.55,
            lastFuelAt = now - 2.hours,
            stateAtLastFuel = FireState.steady,
            recentFuel = listOf(FuelEvent(personID = ruth.id, at = now - 2.hours)),
        ),
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

    /**
     * Render a screen on both palettes: the wallpaper's, and Ribbon's own.
     *
     * Both, because they are genuinely two different-looking apps and the
     * card system has to hold in each — on Ribbon's own paint a card is a
     * drawn outline rather than a paler fill, and that is exactly the kind of
     * thing that is obvious in a picture and invisible in source.
     */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        val appearance = Appearance(ApplicationProvider.getApplicationContext())
        appearance.wallpaperColour = true
        // One composition, two palettes. A test rule takes `setContent` once,
        // so the switch happens inside the tree — which is also the honest
        // thing to draw, since that is what flipping the switch in Appearance
        // does to a running app.
        compose.setContent {
            RibbonTheme(appearance = appearance) { Box(Modifier.fillMaxSize()) { content() } }
        }
        capture(name)
        appearance.wallpaperColour = false
        capture("$name-ribbon")
    }

    /**
     * The same, for a screen that moves through itself: capture, click, capture.
     *
     * The tour is four cards behind one button, so a single frame of it is
     * not a picture of it. This walks the thread on the wallpaper's palette
     * and again on Ribbon's own, which is what the tour actually looks like.
     */
    private fun shootWalk(name: String, content: @Composable () -> Unit) {
        val appearance = Appearance(ApplicationProvider.getApplicationContext())
        appearance.wallpaperColour = true
        compose.setContent {
            RibbonTheme(appearance = appearance) { Box(Modifier.fillMaxSize()) { content() } }
        }
        capture("$name-mark")
        // The mark holds for 750 ms under its own `delay`, which the test
        // clock does not cross on its own.
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture("$name-0")
        for (i in 1..3) {
            compose.onNodeWithText(Copy.CONTINUE_TOUR).performClick()
            compose.waitForIdle()
            capture("$name-$i")
        }
        appearance.wallpaperColour = false
        capture("$name-3-ribbon")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        check(image.width > 0 && image.height > 0) { "$name drew nothing" }

        // The one assertion worth making automatically: the screen is not a
        // single flat colour. It catches the real failure — a screen that
        // composes, throws nothing, and paints only the ground because a
        // condition upstream went false — and it does not have to be
        // rewritten every time the design is right, which is what a pixel
        // comparison on a screen under redesign would mean.
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val distinct = pixels.asSequence().distinct().take(2).count()
        check(distinct > 1) { "$name is one flat colour — nothing drew over the ground" }
    }

    // MARK: the room

    @Test fun roomWithAFire() {
        val open = reading("MRK", FireScale.medium)
        val finished = reading("RUT", FireScale.small).copy(finishedAt = now - 20.hours)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth, ann.id to ann),
            rooms = listOf(room),
            memberships = listOf(
                membership(me, Ink.teal),
                membership(ruth, Ink.crimson),
                membership(ann, Ink.moss),
            ),
            readings = listOf(finished, open),
            notes = listOf(
                Note(
                    readingID = open.id,
                    authorID = ruth.id,
                    verse = VerseAddress(bookID = "MRK", chapter = 4, verse = 9),
                    kind = NoteKind.voice,
                    transcript = "This is the one I keep coming back to.",
                    createdAt = now - 3.hours,
                ),
                Note(
                    readingID = open.id,
                    authorID = ann.id,
                    verse = VerseAddress(bookID = "MRK", chapter = 6, verse = 31),
                    kind = NoteKind.written,
                    body = "Come away and rest a while.",
                    createdAt = now - 5.hours,
                ),
            ),
            positions = listOf(
                ReadingPosition(
                    readingID = open.id,
                    personID = me.id,
                    chapter = 4,
                    verse = 1,
                    updatedAt = now - 6.hours,
                ),
            ),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("room-with-a-fire") { Room(m, m.state.rooms.first()) }
    }

    /**
     * A full room on the phone most people are holding.
     *
     * 360 dp is the commonest Android width and the seats are the one thing
     * on this screen whose size is fixed rather than fluid, so this is the
     * frame where a room of six either fits or does not. It did not.
     */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun roomOfSix() {
        val others = listOf(ruth, ann) + (1..3).map { Person(name = "Person $it") }
        val open = reading("ISA", FireScale.large)
        val state = AppState(
            me = me,
            people = (listOf(me) + others).associateBy { it.id },
            rooms = listOf(room),
            memberships = (listOf(me) + others).map { membership(it, Ink.teal) },
            readings = listOf(open),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("room-of-six-360dp") { Room(m, m.state.rooms.first()) }
    }

    /** A paused room: presence off, waiting rows gone, no new fire. */
    @Test fun roomPaused() {
        val paused = room.copy(isPaused = true)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(paused),
            memberships = listOf(membership(me, null), membership(ruth, null))
                .map { it.copy(roomID = paused.id) },
            currentRoomID = paused.id,
        )
        val m = model(state)
        shoot("room-paused") { Room(m, m.state.rooms.first()) }
    }

    @Test fun roomFirstRun() {
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(membership(me, null)),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("room-first-run") { Room(m, m.state.rooms.first()) }
    }

    @Composable
    private fun Room(m: AppModel, r: app.readribbon.core.Room) {
        val sheet = rememberBookSheet()
        RoomScreen(
            model = m,
            room = r,
            sheet = sheet,
            chooserRequested = false,
            onChooserHandled = {},
            onOpenReading = { _, _ -> },
            onBeginOpening = {},
            onOpenRooms = {},
            onYou = {},
            onAbandonOpening = {},
            onOpenPerson = { _, _ -> },
            onOpenEmber = {},
        )
    }

    @Test fun theBook() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room),
            memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
            readings = listOf(open),
            notes = listOf(
                Note(
                    readingID = open.id,
                    authorID = ruth.id,
                    verse = VerseAddress(bookID = "MRK", chapter = 1, verse = 17),
                    kind = NoteKind.written,
                    body = "Follow me.",
                    createdAt = now - 3.hours,
                ),
            ),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("reading") {
            val sheet = rememberBookSheet()
            LaunchedEffect(sheet) { sheet.animate(open = true) }
            ReadingScreen(
                model = m,
                room = m.state.rooms.first(),
                reading = open,
                sheet = sheet,
                onClose = {},
                onDismissed = {},
                onFinished = {},
                onStartAnother = {},
            )
        }
    }

    @Test fun aPerson() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room),
            memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
            readings = listOf(open),
            notes = listOf(
                Note(
                    readingID = open.id,
                    authorID = ruth.id,
                    verse = VerseAddress(bookID = "MRK", chapter = 4, verse = 9),
                    kind = NoteKind.written,
                    body = "Whoever has ears to hear, let him hear.",
                    createdAt = now - 3.hours,
                ),
            ),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("person") {
            PersonScreen(
                model = m,
                personID = ruth.id,
                room = m.state.rooms.first(),
                onOpenVerse = { _, _ -> },
                onDismiss = {},
            )
        }
    }

    @Test fun theMenu() {
        val open = reading("MRK", FireScale.medium)
        val second = Room(name = "Thursday", createdAt = now - 200.hours)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room, second),
            memberships = listOf(
                membership(me, Ink.teal),
                membership(ruth, Ink.crimson),
                Membership(
                    roomID = second.id,
                    personID = me.id,
                    ink = Ink.moss,
                    joinedAt = now - 200.hours,
                ),
            ),
            readings = listOf(open),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("menu") {
            MenuScreen(model = m, entry = MenuEntry.YOU, onDismiss = {}, onSwitch = {})
        }
    }

    // MARK: the settings

    @Test fun textSettings() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(membership(me, null)),
            readings = listOf(open),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("settings-text") { TextSettingsScreen(model = m, onBack = {}) }
    }

    @Test fun notificationSettings() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(membership(me, null)),
            readings = listOf(open),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("settings-notifications") { NotificationSettingsScreen(model = m, onBack = {}) }
    }

    @Test fun appearanceSettings() {
        shoot("settings-appearance") { AppearanceScreen(onBack = {}) }
    }

    // MARK: the front door
    //
    // Everything a hesitant partner sees *before* the room. It had no picture
    // at all until now, which is why it had no pass either: the room got
    // looked at every time it changed and this did not.
    //
    // Driven by clicking rather than by calling the steps directly, because
    // `Step` is private and should stay that way — and walking the thread is
    // the honest way to see it anyway.

    /** The mark, and the four tour cards it dissolves into. */
    @Test fun theWayIn() {
        val m = model(AppState())
        shootWalk("way-in") { Onboarding(m) }
    }

    /** The question the tour ends on. */
    @Test fun theIntentStep() {
        val m = model(AppState())
        shoot("way-in-intent") {
            Onboarding(m)
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        // Four Continues walks the tour out and lands on the intent step.
        repeat(4) {
            compose.onNodeWithText(Copy.CONTINUE_TOUR).performClick()
            compose.waitForIdle()
        }
        capture("way-in-intent-ribbon")
    }

    /** Where a name and a face are asked for — the last step before the room. */
    @Test fun theNameStep() {
        val m = model(AppState())
        shoot("way-in-name") { Onboarding(m) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        // Four through the tour, one more off the intent step.
        repeat(5) {
            compose.onNodeWithText(Copy.CONTINUE_TOUR).performClick()
            compose.waitForIdle()
        }
        capture("way-in-name-ribbon")
    }

    @Composable
    private fun Onboarding(m: AppModel) {
        OnboardingFlow(model = m, onDone = {})
    }

    /** The chooser: the search field, the five good places to start. */
    @Test fun theChooser() {
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(membership(me, null)),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("chooser") {
            BookChooserContent(
                room = m.state.rooms.first(),
                model = m,
                onChoose = {},
            )
        }
    }

    /** The invite: what a room of one is actually looking at. */
    @Test fun theInvite() {
        val state = AppState(
            me = me,
            people = mapOf(me.id to me),
            rooms = listOf(room),
            memberships = listOf(membership(me, null)),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("invite") {
            InviteContent(room = m.state.rooms.first(), model = m)
        }
    }

    private companion object {
        init {
            // Bible's table is generated and loaded lazily; touching it here
            // keeps the first screen's measure honest.
            Bible.goodPlacesToStart
        }
    }
}
