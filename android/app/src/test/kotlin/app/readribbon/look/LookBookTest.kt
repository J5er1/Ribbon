package app.readribbon.look

import android.graphics.Bitmap
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.readribbon.R
import app.readribbon.app.AppModel
import app.readribbon.app.Copy
import app.readribbon.core.Bible
import app.readribbon.core.BlockStyle
import app.readribbon.core.CardState
import app.readribbon.core.FireScale
import app.readribbon.core.FireState
import app.readribbon.core.FuelEvent
import app.readribbon.core.Handiwork
import app.readribbon.core.Highlight
import app.readribbon.core.Ink
import app.readribbon.core.Membership
import app.readribbon.core.Note
import app.readribbon.core.NoteKind
import app.readribbon.core.Person
import app.readribbon.core.Reading
import app.readribbon.core.ReadingPosition
import app.readribbon.core.ReflectionCard
import app.readribbon.core.Ribbon
import app.readribbon.core.Room
import app.readribbon.core.ScriptureBlock
import app.readribbon.core.ScriptureChapter
import app.readribbon.core.ScriptureSpan
import app.readribbon.core.VerseAddress
import app.readribbon.core.VerseRange
import app.readribbon.data.AppState
import app.readribbon.data.LocalStore
import app.readribbon.design.Appearance
import app.readribbon.design.LAUNCH_MARK_MS
import app.readribbon.design.LaunchMark
import app.readribbon.design.LocalFlowRoot
import app.readribbon.design.RibbonMotion
import app.readribbon.design.RibbonTheme
import app.readribbon.design.rememberBookSheet
import app.readribbon.design.room
import app.readribbon.reading.ChapterText
import app.readribbon.reading.ChaptersContent
import app.readribbon.reading.LeaveToolbar
import app.readribbon.reading.ReadingScreen
import app.readribbon.reading.ReadingTheme
import app.readribbon.reading.ReflectionCardView
import app.readribbon.reading.VerseMark
import app.readribbon.screens.AppearanceScreen
import app.readribbon.screens.BookChooserContent
import app.readribbon.screens.InviteContent
import app.readribbon.screens.MenuEntry
import app.readribbon.screens.MenuScreen
import app.readribbon.screens.NotificationSettingsScreen
import app.readribbon.screens.OnboardingFlow
import app.readribbon.screens.PersonScreen
import app.readribbon.screens.PreviewStep
import app.readribbon.screens.RoomScreen
import app.readribbon.screens.ShelfView
import app.readribbon.screens.TextSettingsScreen
import app.readribbon.services.LocalPresenceService
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    /** Everywhere else in the book, and the ribbon at the top of it (A31). */
    @Test fun theChapters() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room),
            memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
            readings = listOf(open),
            positions = listOf(
                ReadingPosition(
                    readingID = open.id, personID = me.id,
                    chapter = 2, verse = 1, updatedAt = now - 6.hours,
                ),
            ),
            ribbons = listOf(
                Ribbon(
                    readingID = open.id, personID = ruth.id,
                    chapter = 4, verse = 9, placedAt = now - 2.hours,
                ),
            ),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("chapters") {
            ChaptersContent(model = m, reading = open, onGo = {})
        }
    }

    /** A room whose ribbon is somewhere you are not: the offer (A30). */
    @Test fun roomWithARibbon() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room),
            memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
            readings = listOf(open),
            positions = listOf(
                ReadingPosition(
                    readingID = open.id, personID = me.id,
                    chapter = 2, verse = 1, updatedAt = now - 6.hours,
                ),
            ),
            ribbons = listOf(
                Ribbon(
                    readingID = open.id, personID = ruth.id,
                    chapter = 4, verse = 9, placedAt = now - 2.hours,
                ),
            ),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("room-with-a-ribbon") { Room(m, m.state.rooms.first()) }
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

    /**
     * The same screen with nothing on it — the first-run state of every
     * person screen in the product (§6.1), and the one this pass existed to
     * fix. It used to be a face, a name and blank ground.
     */
    @Test fun aPersonWithNothing() {
        val open = reading("MRK", FireScale.medium)
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room),
            memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
            readings = listOf(open),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("person-empty") {
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
        shoot("menu-you") {
            MenuScreen(model = m, entry = MenuEntry.YOU, onDismiss = {}, onSwitch = {})
        }
    }

    /**
     * You → Appearance, caught in the middle (A47).
     *
     * Owner: *"when you're in your profile, going from Appearance, for
     * example, tapping works very well. Actually, not fully. There's a ton of
     * glitches and stuff."*
     *
     * Nothing in this book could see it. `menu-you` above draws `MenuScreen`
     * on its own, with no `SharedTransitionLayout` around it — so
     * `LocalFlowRoot` is null, `flowsAsWords` degrades to `this`, and the
     * settings-title flow that this transition is *made of* has never been
     * photographed. What was being checked was the two ends, which were fine;
     * the middle is where the complaint is.
     *
     * So this one wraps the menu the way `RibbonRoot` does, taps the row, and
     * stops the clock part of the way through.
     */
    @Test fun theSettingsFlow() {
        val open = reading("MRK", FireScale.medium)
        val m = model(
            AppState(
                me = me,
                people = mapOf(me.id to me),
                rooms = listOf(room),
                memberships = listOf(membership(me, Ink.teal)),
                readings = listOf(open),
                currentRoomID = room.id,
            ),
        )
        val appearance = Appearance(ApplicationProvider.getApplicationContext())
        appearance.wallpaperColour = false
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalFlowRoot provides this) {
                        MenuScreen(
                            model = m,
                            entry = MenuEntry.YOU,
                            onDismiss = {},
                            onSwitch = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(Copy.APPEARANCE).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // A third of the way through the push, which is where a title that is
        // travelling would be visibly travelling.
        compose.mainClock.advanceTimeBy(RibbonMotion.SETTLE_MS / 3L)
        File(out, "settings-flow-going.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.mainClock.advanceTimeBy(RibbonMotion.SETTLE_MS * 2L)
        File(out, "settings-flow-landed.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.mainClock.autoAdvance = true
    }

    /** The menu's other door: the room, its people and the rooms you are in. */
    @Test fun theRoomMenu() {
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
        shoot("menu-room") {
            MenuScreen(model = m, entry = MenuEntry.ROOMS, onDismiss = {}, onSwitch = {})
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

    /**
     * The launch mark, drawn from the drawable the window actually uses.
     *
     * The splash is a system window, not a composition, so nothing else in
     * this book can see it — and a vector whose clip-path is wrong shows up
     * as a blank launch on a real phone and as nothing at all in a compile.
     * This inflates the real `splash_wave` and draws it with the unfurl fully
     * open, which is the frame the animation ends on.
     */
    /**
     * The launch mark, mid-unfurl and at rest (A45).
     *
     * The mark is a composable now rather than an `AnimatedVectorDrawable` in
     * the launch theme, which is what makes it possible to photograph at all:
     * a system splash is drawn by the system, in another process, and nothing
     * in this book could ever see it. The clock is stopped so the ribbon can
     * be caught part of the way down — the frame the owner reported never
     * seeing.
     */
    @Test fun theLaunchMarkComingDown() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RibbonTheme {
                // `ready = false`: the store has not loaded, which is the
                // state the mark exists for and the one that holds it still.
                LaunchMark(ready = false, onDone = {})
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(160L)
        File(out, "launch-mark-coming-down.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // And where it lands, which is also what reduce motion shows at once.
        compose.mainClock.advanceTimeBy(LAUNCH_MARK_MS.toLong())
        File(out, "launch-mark-at-rest.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun theLaunchMark() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val drawable = checkNotNull(context.getDrawable(R.drawable.splash_wave)) {
            "splash_wave did not inflate"
        }
        val side = 432
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#0B0B0A"))
        drawable.setBounds(0, 0, side, side)
        // The drawable rests with the unfurl open, which is both the frame
        // the animation lands on and what shows if it never runs.
        drawable.draw(canvas)
        File(out, "launch-mark.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // The mark has to actually be there: a clip-path that does not match
        // the animator's rectangle draws nothing and compiles perfectly.
        val pixels = IntArray(side * side)
        bitmap.getPixels(pixels, 0, side, 0, 0, side, side)
        val ground = android.graphics.Color.parseColor("#0B0B0A")
        check(pixels.any { it != ground }) { "the launch mark drew nothing but ground" }
    }

    /**
     * The launch mark actually animates.
     *
     * The shot above proves the mark *draws*; it would pass just as happily
     * on a mark whose animation never runs, which is exactly what the owner
     * reported. So this one asserts two frames 160 ms apart are different
     * pictures — that the ribbon is on its way down rather than simply there.
     *
     * This used to drive the `AnimatedVectorDrawable` in the launch theme and
     * it passed, every time, which is the reason A45 moved the mark into the
     * app rather than trying to fix a drawable that was never broken: the
     * animation ran here and did not run on the phone, and a system splash is
     * drawn by the system, in another process, where nothing can reach it.
     * Here the mark is the app's own, so what this test drives is what the
     * phone runs.
     */
    @Test fun theLaunchMarkMoves() {
        compose.mainClock.autoAdvance = false
        compose.setContent { RibbonTheme { LaunchMark(ready = false, onDone = {}) } }
        compose.mainClock.advanceTimeByFrame()

        fun frame(): IntArray {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            return pixels
        }

        val first = frame()
        compose.mainClock.advanceTimeBy(160L)
        val later = frame()
        compose.mainClock.autoAdvance = true

        val ground = android.graphics.Color.parseColor("#FF0B0B0A")
        check(later.any { it != ground }) { "the launch mark drew nothing but ground" }
        check(!first.contentEquals(later)) {
            "the launch mark drew the same picture 160 ms apart — it is not coming down"
        }
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

    // MARK: the cards (S08/S09)

    /**
     * Both faces of a reflection card, which had never been in the look book
     * and are the hardest thing in the app to picture from source — the
     * sealed one because §4.6's whole design is what it *doesn't* say, and
     * the open one because it is the only place the eight inks appear beside
     * each other at a readable size.
     *
     * Two members, two answers, so the open card is a room and not a mirror.
     */
    private fun cardState(
        answers: Map<Uuid, String>,
        state: CardState,
    ): Pair<AppModel, ReflectionCard> {
        val open = reading("MRK", FireScale.medium)
        val card = ReflectionCard(
            readingID = open.id,
            chapter = 4,
            question = "What did you notice that the other one probably didn't?",
            answers = answers,
            state = state,
        )
        val m = model(
            AppState(
                me = me,
                people = mapOf(me.id to me, ruth.id to ruth),
                rooms = listOf(room),
                memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
                readings = listOf(open),
                cards = listOf(card),
                currentRoomID = room.id,
            ),
        )
        return m to card
    }

    /** Sealed, and already answered: your own words, the way back into them,
     *  and the one line that never names anybody. */
    @Test fun theCardSealed() {
        val (m, card) = cardState(
            answers = mapOf(me.id to "That he asked twice, and waited both times."),
            state = CardState.sealed,
        )
        shoot("card-sealed") {
            OnTheGround {
            ReflectionCardView(
                card = card,
                reading = m.state.readings.first(),
                room = m.state.rooms.first(),
                model = m,
                modifier = Modifier.padding(24.dp),
            )
            }
        }
    }

    /** Sealed and unanswered: the field, open, with no prompt in it (S08). */
    @Test fun theCardUnanswered() {
        val (m, card) = cardState(answers = emptyMap(), state = CardState.sealed)
        shoot("card-unanswered") {
            OnTheGround {
            ReflectionCardView(
                card = card,
                reading = m.state.readings.first(),
                room = m.state.rooms.first(),
                model = m,
                modifier = Modifier.padding(24.dp),
            )
            }
        }
    }

    /** Open: everyone's answers together, each with its author's ink (S09). */
    @Test fun theCardOpen() {
        val (m, card) = cardState(
            answers = mapOf(
                me.id to "That he asked twice, and waited both times.",
                ruth.id to "The crowd went quiet before he did.",
            ),
            state = CardState.open,
        )
        shoot("card-open") {
            OnTheGround {
            ReflectionCardView(
                card = card,
                reading = m.state.readings.first(),
                room = m.state.rooms.first(),
                model = m,
                modifier = Modifier.padding(24.dp),
            )
            }
        }
    }

    // MARK: the four screens that had never been in a picture

    /**
     * The page in use: two people's highlights, one verse carrying both, and
     * the marks in the gutter.
     *
     * `theBook` above draws a clean page with a single note far down it, so
     * the two things the reading surface is actually *for* — a wash and an
     * overlap — had never been photographed. The overlap especially: §4.5
     * says two inks on one verse make a third colour and that the colours are
     * never averaged, which is arithmetic nobody can check by reading it.
     */
    @Test fun theBookInUse() {
        val open = reading("MRK", FireScale.medium)
        fun mark(verse: Int, who: Person, kind: NoteKind) = Note(
            readingID = open.id,
            authorID = who.id,
            verse = VerseAddress(bookID = "MRK", chapter = 1, verse = verse),
            kind = kind,
            body = if (kind == NoteKind.written) "The wilderness, again." else null,
            createdAt = now - 3.hours,
        )
        fun wash(from: Int, to: Int, ink: Ink, who: Person) = Highlight(
            readingID = open.id,
            authorID = who.id,
            range = VerseRange(bookID = "MRK", chapter = 1, startVerse = from, endVerse = to),
            ink = ink,
            createdAt = now - 3.hours,
        )
        val state = AppState(
            me = me,
            people = mapOf(me.id to me, ruth.id to ruth),
            rooms = listOf(room),
            memberships = listOf(membership(me, Ink.teal), membership(ruth, Ink.crimson)),
            readings = listOf(open),
            notes = listOf(mark(2, ruth, NoteKind.written), mark(4, me, NoteKind.voice)),
            highlights = listOf(
                wash(3, 3, Ink.crimson, ruth),
                // Verse 3 twice, in two inks: the third colour (§4.5).
                wash(3, 4, Ink.teal, me),
                wash(6, 6, Ink.crimson, ruth),
            ),
            currentRoomID = room.id,
        )
        val m = model(state)
        shoot("reading-in-use") {
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

    /**
     * Your own highlight, caught half-way across the words.
     *
     * The stroke is the one thing on this surface the app can honestly show
     * as the movement of a hand, and a still frame is the only way to check
     * that it reveals along the *words* rather than wiping the whole block.
     * The clock is held and stepped to a little under half of a settle, so
     * the second line is part-marked and the third has not been reached.
     */
    @Test fun theStrokeTravelling() {
        val chapter = ScriptureChapter(
            n = 1,
            blocks = listOf(
                ScriptureBlock(
                    s = BlockStyle.p,
                    x = listOf(
                        ScriptureSpan(
                            v = 1,
                            t = "In the beginning was the Word, and the Word was with " +
                                "God, and the Word was God. ",
                        ),
                        ScriptureSpan(v = 2, t = "He was with God in the beginning."),
                    ),
                ),
            ),
        )
        val appearance = Appearance(ApplicationProvider.getApplicationContext())
        appearance.wallpaperColour = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                Box(Modifier.fillMaxSize().room()) {
                    ChapterText(
                        chapter = chapter,
                        runningHead = "John 1",
                        theme = ReadingTheme(
                            fontSize = 19f,
                            lineHeightMultiple = 1.62f,
                            redLetter = false,
                        ),
                        marks = listOf(VerseMark(1, null, null, Ink.teal)),
                        lifted = null,
                        justMarked = VerseRange("JHN", 1, 1, 1),
                        onMarkDrawn = {},
                        openNote = null,
                        isFirstChapter = true,
                        showMarginHint = false,
                        onLayout = {},
                        onLongPressVerse = {},
                        onDragToVerse = {},
                        onExtend = { _, _, _ -> },
                        onDragEnded = {},
                        onTapVerse = {},
                        onNoteSlot = {},
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
            }
        }
        // Let the first frame land, then step to part-way through the settle.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(RibbonMotion.SETTLE_MS * 45L / 100L)
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "stroke-travelling.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.mainClock.autoAdvance = true
    }

    /**
     * The instant your pen touches down on somebody else's mark.
     *
     * This is the frame that was broken. Only one wash is drawn per verse, and
     * it had already become the mixture of the two inks — so at the start of
     * the stroke, with nothing yet revealed, **their highlight was not on the
     * page at all**. It came back from the left as the mixture, which reads as
     * their mark being wiped away and replaced rather than yours being added
     * to theirs.
     *
     * Their ink now stays whole in front of the tip and the pen mixes it as it
     * passes. The travelling part of that is `theStrokeTravelling`, which
     * catches a pen mid-verse; this one holds the moment before it moves,
     * which is the one that used to be empty.
     */
    @Test fun theInksMeeting() {
        val chapter = ScriptureChapter(
            n = 1,
            blocks = listOf(
                ScriptureBlock(
                    s = BlockStyle.p,
                    x = listOf(
                        ScriptureSpan(
                            v = 1,
                            t = "In the beginning was the Word, and the Word was with " +
                                "God, and the Word was God. ",
                        ),
                        ScriptureSpan(v = 2, t = "He was with God in the beginning."),
                    ),
                ),
            ),
        )
        val appearance = Appearance(ApplicationProvider.getApplicationContext())
        appearance.wallpaperColour = false
        // Theirs is on the page first and has settled; yours goes on after.
        // The sequence is the whole point — a page that *opens* with both
        // inks has no "before" for the pen to mix out of.
        val inks = mutableStateOf(listOf(VerseMark(1, null, null, Ink.crimson)))
        val marking = mutableStateOf<VerseRange?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RibbonTheme(appearance = appearance) {
                Box(Modifier.fillMaxSize().room()) {
                    ChapterText(
                        chapter = chapter,
                        runningHead = "John 1",
                        theme = ReadingTheme(
                            fontSize = 19f,
                            lineHeightMultiple = 1.62f,
                            redLetter = false,
                        ),
                        marks = inks.value,
                        lifted = null,
                        justMarked = marking.value,
                        onMarkDrawn = {},
                        openNote = null,
                        isFirstChapter = true,
                        showMarginHint = false,
                        onLayout = {},
                        onLongPressVerse = {},
                        onDragToVerse = {},
                        onExtend = { _, _, _ -> },
                        onDragEnded = {},
                        onTapVerse = {},
                        onNoteSlot = {},
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
            }
        }
        // Their mark is simply what the page already shows; nothing has to
        // animate for that, so one frame is enough.
        compose.mainClock.advanceTimeByFrame()

        inks.value = listOf(
            VerseMark(1, null, null, Ink.crimson),
            VerseMark(1, null, null, Ink.teal),
        )
        marking.value = VerseRange("JHN", 1, 1, 1)
        // Stepped a frame at a time rather than jumped: an animation started
        // in the same batch as the state change takes its start time on the
        // next frame, and a single long jump lands on that frame with nothing
        // elapsed.
        repeat(12) { compose.mainClock.advanceTimeByFrame() }

        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "inks-meeting.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.mainClock.autoAdvance = true
    }

    /**
     * A mark on part of a verse, and two people marking different parts of one.
     *
     * The second is why the wash is cut into stretches rather than painted per
     * verse: with a wash per verse either mark would have coloured the whole
     * of it, and §4.5's overlap would have been claimed across words only one
     * person had touched. Here the middle is the third colour and the two ends
     * are each their own ink — which is only checkable in a picture.
     */
    @Test fun aMarkOnAPhrase() {
        val text = "In the beginning was the Word, and the Word was with God, " +
            "and the Word was God."
        val chapter = ScriptureChapter(
            n = 1,
            blocks = listOf(
                ScriptureBlock(
                    s = BlockStyle.p,
                    x = listOf(
                        ScriptureSpan(v = 1, t = "$text "),
                        ScriptureSpan(v = 2, t = "He was with God in the beginning."),
                    ),
                ),
            ),
        )
        // "the Word was with God" — hers; "was with God, and the Word" — his.
        val hers = text.indexOf("the Word was with God")
        val his = text.indexOf("was with God, and the Word")
        shoot("a-phrase") {
            OnTheGround {
                ChapterText(
                    chapter = chapter,
                    runningHead = "John 1",
                    theme = ReadingTheme(
                        fontSize = 19f,
                        lineHeightMultiple = 1.62f,
                        redLetter = false,
                    ),
                    marks = listOf(
                        VerseMark(1, hers, hers + "the Word was with God".length, Ink.crimson),
                        VerseMark(1, his, his + "was with God, and the Word".length, Ink.teal),
                        VerseMark(2, null, null, Ink.moss),
                    ),
                    lifted = null,
                    justMarked = null,
                    onMarkDrawn = {},
                    openNote = null,
                    isFirstChapter = true,
                    showMarginHint = false,
                    onLayout = {},
                    onLongPressVerse = {},
                    onDragToVerse = {},
                    onExtend = { _, _, _ -> },
                    onDragEnded = {},
                    onTapVerse = {},
                    onNoteSlot = {},
                    modifier = Modifier.padding(top = 60.dp),
                )
            }
        }
    }

    /**
     * A lifted selection with S06's two handles on it — the ends of the mark
     * you are about to make, which until now could not be adjusted at all.
     */
    @Test fun theSelectionHandles() {
        val chapter = ScriptureChapter(
            n = 1,
            blocks = listOf(
                ScriptureBlock(
                    s = BlockStyle.p,
                    x = listOf(
                        ScriptureSpan(
                            v = 1,
                            t = "In the beginning was the Word, and the Word was with " +
                                "God, and the Word was God. ",
                        ),
                        ScriptureSpan(
                            v = 2,
                            t = "He was with God in the beginning. ",
                        ),
                        ScriptureSpan(
                            v = 3,
                            t = "Through him all things were made.",
                        ),
                    ),
                ),
            ),
        )
        shoot("selection-handles") {
            OnTheGround {
                ChapterText(
                    chapter = chapter,
                    runningHead = "John 1",
                    theme = ReadingTheme(
                        fontSize = 19f,
                        lineHeightMultiple = 1.62f,
                        redLetter = false,
                    ),
                    marks = emptyList(),
                    lifted = VerseRange("JHN", 1, 1, 2),
                    justMarked = null,
                    onMarkDrawn = {},
                    openNote = null,
                    isFirstChapter = true,
                    showMarginHint = false,
                    onLayout = {},
                    onLongPressVerse = {},
                    onDragToVerse = {},
                    onExtend = { _, _, _ -> },
                    onDragEnded = {},
                    onTapVerse = {},
                    onNoteSlot = {},
                    modifier = Modifier.padding(top = 60.dp),
                )
            }
        }
    }

    /**
     * The long-press toolbar, in the case that has eight inks in it.
     *
     * A room of two picks an ink per highlight, so all eight are on the bar;
     * a room of three or more shows one. The eight-across case is the one
     * that has to scroll, and the one whose targets were 34 dp until A41a —
     * which is exactly the sort of thing that is obvious in a picture and
     * invisible in source.
     */
    @Test fun theLeaveToolbar() {
        val open = reading("MRK", FireScale.medium)
        val m = model(
            AppState(
                me = me,
                people = mapOf(me.id to me, ruth.id to ruth),
                rooms = listOf(room),
                memberships = listOf(membership(me, null), membership(ruth, null)),
                readings = listOf(open),
                currentRoomID = room.id,
            ),
        )
        shoot("leave-toolbar") {
            OnTheGround {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LeaveToolbar(
                        model = m,
                        room = m.state.rooms.first(),
                        range = VerseRange(
                            bookID = "MRK",
                            chapter = 1,
                            startVerse = 3,
                            endVerse = 3,
                        ),
                        roomPaused = false,
                        onHighlight = {},
                        onWrite = {},
                        onSpeak = {},
                    )
                }
            }
        }
    }

    /**
     * The invite, from the other end (S16).
     *
     * The build book calls this the most important conversion surface in the
     * product, and it is the last screen in the app that had never been
     * looked at — which is how it kept a monogram where S16's anatomy names a
     * portrait (A37) until somebody read the source.
     */
    @Test fun theWayIntoSomebodyElsesRoom() {
        shoot("join") {
            OnTheGround {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PreviewStep(
                        inviteLine = Copy.wantsToReadWithYou("Ruth"),
                        inviterName = "Ruth Alderman",
                        roomName = null,
                        joiningAs = null,
                        onJoin = {},
                        onJoinAsSomeoneElse = null,
                        onStartInstead = {},
                    )
                }
            }
        }
    }

    /**
     * The shelf (S10): every book this room has finished, as embers on a
     * shared baseline.
     *
     * Four of them, because one is the case the code special-cases and three
     * is the first that has to wrap — and because a shelf of one is a picture
     * of a component rather than of a shelf.
     */
    @Test fun theShelf() {
        val finished = listOf("RUT" to FireScale.small, "MRK" to FireScale.medium,
            "JON" to FireScale.small, "PHP" to FireScale.large)
            .map { (book, scale) ->
                reading(book, scale).copy(finishedAt = now - 20.hours)
            }
        val m = model(
            AppState(
                me = me,
                people = mapOf(me.id to me),
                rooms = listOf(room),
                memberships = listOf(membership(me, null)),
                readings = finished,
                currentRoomID = room.id,
            ),
        )
        shoot("shelf") {
            OnTheGround {
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    ShelfView(
                        room = m.state.rooms.first(),
                        readings = finished,
                        onStartAnother = {},
                        onOpenEmber = {},
                    )
                }
            }
        }
    }

    /**
     * The card, on the ground it actually sits on.
     *
     * Every other entry here renders a whole screen, which paints its own
     * `.room()`; a card on its own would otherwise be shot against the
     * theme's bare background and the grain under it — half of why a Ribbon
     * card reads as paper — would not be in the picture at all.
     */
    @Composable
    private fun OnTheGround(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().room()) { content() }
    }

    private companion object {
        init {
            // Bible's table is generated and loaded lazily; touching it here
            // keeps the first screen's measure honest.
            Bible.goodPlacesToStart
        }
    }
}
