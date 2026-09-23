import SwiftUI
import RibbonCore

// Cold start goes to the room you were last in. No splash screen, no
// "welcome back," no interstitial — the app opening on the room is the app
// saying nothing, which is correct (§05). The fastest path from launch to
// Scripture is the product (§6.2).

/// The model, for the two things that run without a scene: a background
/// pull, and a tapped notification arriving before the window exists.
@MainActor
enum AppSession {
    static weak var model: AppModel?
}

/// What has to exist before the app finishes launching: the background
/// task's handler and the notification centre's delegate. Both refuse to be
/// set later, and neither needs a screen.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        NotificationRouter.shared.install()
        // The closure before the registration, and both before launch
        // finishes: a wake can be delivered the moment the handler exists.
        RoomWatch.pull = {
            // The app may be awake with its model, or this may be a wake
            // from nothing: either way, one pull, and the merge posts what
            // arrived (S19).
            if let model = AppSession.model {
                await model.refreshFromRemote()
            } else {
                let model = await AppModel.load(forBackgroundPull: true)
                await model.refreshFromRemote()
            }
        }
        RoomWatch.register()
        return true
    }
}

@main
struct RibbonApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model: AppModel?
    /// A URL that arrived before the model finished loading — the normal
    /// case when tapping an invite link cold-starts the app (S16).
    @State private var bufferedURL: URL?
    /// The mark's unfurl has run its course (ledger A28): the launch mark
    /// leaves when the model is loaded *and* the mark has settled, so a
    /// fast phone still sees the whole of it and a slow one never sees a
    /// spinner.
    @State private var markSettled = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ZStack {
                if let model {
                    RootView()
                        .environment(model)
                } else {
                    // The unlit ground while state loads from disk —
                    // indistinguishable from the launch screen.
                    GrainBackground()
                }
                if model == nil || !markSettled {
                    LaunchMark { markSettled = true }
                        .transition(.opacity)
                }
            }
            .animation(RibbonMotion.settle, value: model == nil || !markSettled)
            .preferredColorScheme(.dark)
            .task {
                if model == nil {
                    let loaded = await AppModel.load()
                    if let bufferedURL {
                        loaded.handleInviteURL(bufferedURL)
                        self.bufferedURL = nil
                    }
                    AppSession.model = loaded
                    loaded.visibleRoomID = loaded.currentRoom?.id
                    model = loaded
                    // The room renders from local state instantly; the
                    // backend catches up behind it.
                    await loaded.refreshFromRemote()
                }
            }
            // An invite link, tapped: readribbon.app/i/<token> via the
            // associated domain, ribbon://i/<token> as the plain-scheme
            // fallback (S16).
            .onOpenURL { url in
                if let model {
                    model.handleInviteURL(url)
                } else {
                    bufferedURL = url
                }
            }
            .onChange(of: scenePhase) { _, phase in
                guard let model else { return }
                switch phase {
                case .active:
                    // The room is on screen again: a note left here is not
                    // something the phone needs to tell you about (S19).
                    model.visibleRoomID = model.currentRoom?.id
                    Task {
                        await Notifications.refreshAllowed()
                        await model.refreshFromRemote()
                        // The room's live line comes back with the app, and
                        // only with it: a phone in a pocket is not present,
                        // and saying otherwise is the one lie presence must
                        // never tell (§4.2).
                        await model.openRoomChannel()
                    }
                case .background:
                    model.visibleRoomID = nil
                    Task { await model.closeRoomChannel() }
                default:
                    break
                }
            }
        }
    }
}

/// A push to someone in a room (S12) — from a portrait, anywhere.
struct PersonRoute: Hashable {
    var personID: UUID
    var roomID: UUID
}

struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var onboarding = false
    @State private var openReading: Reading?
    /// Where the reading should open, when a row or a quoted verse named a
    /// place (§6.3, S11). Nil means your own position.
    @State private var openTarget: VerseAddress?
    /// The menu, when it is open, and which of its two doors was used
    /// (S14 + S18 in one screen — MenuScreen.swift, deviations 14). Nil is
    /// the room, which is the only permanent destination.
    @State private var menu: MenuEntry?
    /// An invite that arrived while the menu was open, held until the menu
    /// has actually gone (see below).
    @State private var deferredInvite: PendingInvite?
    /// The finishing sequence's "Start another" lands in the chooser (S13).
    @State private var chooserRequested = false
    @State private var navigationPath = NavigationPath()
    /// The pull on the fire (A48): 0 where it sits, 1 at the end of its
    /// travel. The page rises on this number while `pulling` is set, and
    /// the root carries it to 1 on the cover spring when the pull commits.
    @State private var bookPull: CGFloat = 0
    /// The reading a pull has taken hold of — built under the room the
    /// moment the finger takes the fire, so there is a page to lift.
    @State private var pulling: Reading?
    /// Each hold on the fire, counted, so a page going back down from one
    /// pull is not taken out from under the next one: a quick re-grab
    /// lands while the last release is still settling.
    @State private var pullHold = 0

    var body: some View {
        Group {
            if onboarding || !model.isOnboardedPerson {
                OnboardingFlow {
                    onboarding = false
                }
                .onAppear { onboarding = true }
            } else {
                roomStack
            }
        }
        .animation(RibbonMotion.settle, value: onboarding)
    }

    @ViewBuilder
    private var roomStack: some View {
        if let room = model.currentRoom {
            NavigationStack(path: $navigationPath) {
                RoomScreen(
                    room: room,
                    chooserRequested: $chooserRequested,
                    onOpenReading: { reading, target in openBook(reading, at: target) },
                    onOpenRooms: { menu = .rooms },
                    onYou: { menu = .you },
                    bookPull: $bookPull,
                    onBeginOpening: { reading in
                        pullHold += 1
                        // Under reduce motion nothing moves under the
                        // finger, so there is no page to lift: it arrives,
                        // fading, when the pull commits.
                        if openReading == nil, !reduceMotion { pulling = reading }
                    },
                    onFinishOpening: { reading, rate in
                        guard pulling?.id == reading.id else {
                            openBook(reading, at: nil)
                            return
                        }
                        // The finger started this: the page finishes the
                        // movement from wherever the pull left it, at the
                        // speed the finger let go at.
                        openTarget = nil
                        withAnimation(RibbonMotion.cover(rate: rate, towards: 1 - bookPull)) {
                            bookPull = 1
                            openReading = reading
                        } completion: {
                            pulling = nil
                            bookPull = 0
                        }
                    },
                    onAbandonOpening: { rate in
                        // Let go short of the commit: the page goes back
                        // down with the fire on the one spring, carrying the
                        // finger's speed, and leaves the tree only once it
                        // has arrived — not the moment the finger lifts.
                        let hold = pullHold
                        guard bookPull > 0 else {
                            if openReading == nil { pulling = nil }
                            return
                        }
                        withAnimation(RibbonMotion.handled(rate: rate, towards: -bookPull)) {
                            bookPull = 0
                        } completion: {
                            if openReading == nil, pullHold == hold { pulling = nil }
                        }
                    })
                .navigationDestination(for: UUID.self) { readingID in
                    if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                        EmberRecordScreen(
                            reading: reading,
                            onOpenVerse: { verse in
                                // A quoted verse opens the reading at that
                                // verse (S11) — the finished book's own
                                // pages, not a copy.
                                openBook(reading, at: verse)
                            },
                            onReadAgain: { bookID in
                                navigationPath = NavigationPath()
                                let new = model.startReading(bookID: bookID, in: room)
                                openBook(new, at: nil)
                            })
                    }
                }
                .navigationDestination(for: PersonRoute.self) { route in
                    if let personRoom = model.state.rooms.first(where: { $0.id == route.roomID }) {
                        PersonScreen(
                            personID: route.personID,
                            room: personRoom,
                            onOpenVerse: { verse, readingID in
                                // The note names its reading — a finished
                                // book's note opens that book, not the
                                // open one.
                                if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                                    openBook(reading, at: verse)
                                }
                            })
                    }
                }
                .toolbarVisibility(.hidden, for: .navigationBar)
            }
            .overlay {
                // The reading is a full-screen cover in spirit, but drawn
                // in-tree so the closing drag settles like a book (S02) —
                // sliding down over the room rather than a system sheet.
                // While the fire is being pulled the page is already here,
                // under the room's foot, rising on the pull's own number;
                // opened any other way it comes up on the cover spring.
                GeometryReader { proxy in
                    if let reading = openReading ?? pulling {
                        ReadingScreen(
                            room: room,
                            reading: reading,
                            openAt: openTarget,
                            onClose: closeBook,
                            onFinished: closeBook,
                            onStartAnother: {
                                closeBook()
                                chooserRequested = true
                            })
                        // One page per book. A notification can open another
                        // book over this one (S19), and without its own
                        // identity the new book would inherit this one's
                        // typeset chapters, its landing, and where it opened.
                        .id(reading.id)
                        .offset(y: openReading == nil ? (1 - bookPull) * proxy.size.height : 0)
                        // The page goes back down the way it came when the
                        // book closes. Under reduce motion a page the size
                        // of the screen does not travel: it fades in where
                        // it will be read, and out the same way (§11).
                        .transition(reduceMotion
                            ? AnyTransition.opacity
                            : AnyTransition.asymmetric(
                                insertion: .move(edge: .bottom),
                                removal: .move(edge: .bottom).combined(with: .opacity)))
                    }
                }
            }
            // The menu, full screen. It is one screen rather than the
            // book's two sheets, and it holds the rooms, the room you are
            // in, and you — the whole of what used to be S14 and S18, with
            // the two doors that were missing from both (deviations 14).
            .fullScreenCover(item: $menu) { entry in
                MenuScreen(entry: entry, onSwitch: { _ in
                    // The book belongs to the room it was opened in.
                    openReading = nil
                    openTarget = nil
                })
            }
            .onChange(of: model.isOnboardedPerson) { _, stillHere in
                // Belt and braces on the menu's own dismissal: whatever
                // empties the store — deleting the account today, anything
                // else later — must not leave a menu about a person who is
                // no longer there standing over the room that replaces them.
                if !stillHere { menu = nil }
            }
            .onChange(of: model.pendingInvite) { _, pending in
                // A tapped invite link is the strongest possible statement
                // of intent (S16): it closes the menu rather than arriving
                // underneath it, the way it wins over onboarding's step.
                //
                // Closing the cover and presenting the sheet in one update
                // is one presentation change too many for SwiftUI, and the
                // sheet is the one that gets dropped. So the cover goes
                // first and the join follows it — which is also what it
                // looks like: the menu leaves, then the invite arrives.
                guard pending != nil, menu != nil else { return }
                deferredInvite = pending
                model.pendingInvite = nil
                menu = nil
            }
            .onChange(of: menu) { _, open in
                if open == nil, let held = deferredInvite {
                    deferredInvite = nil
                    model.pendingInvite = held
                }
            }
            .onChange(of: model.pendingDestination, initial: true) { _, destination in
                // A tapped notification (S19): the room it was about, then
                // the verse or the cards it named. The menu gets out of the
                // way; a book open over another room closes.
                guard let destination else { return }
                model.pendingDestination = nil
                menu = nil
                if destination.roomID != model.currentRoom?.id {
                    openReading = nil
                    openTarget = nil
                    model.switchRoom(to: destination.roomID)
                }
                switch destination {
                case .verse(_, let readingID, let verse):
                    if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                        openBook(reading, at: verse)
                    }
                case .cards(_, let readingID, let chapter):
                    if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                        openBook(reading, at: VerseAddress(bookID: reading.bookID, chapter: chapter, verse: 1))
                    }
                case .room:
                    break
                }
            }
            .sheet(item: pendingInviteBinding) { pending in
                // A tapped invite while already onboarded: the join flow
                // rides over the room (S16). id keeps a second link from
                // inheriting the first one's half-finished state.
                JoinFlow(token: pending.token, onDone: {
                    pendingInviteBinding.wrappedValue = nil
                })
                .id(pending.token)
                .presentationBackground(Palette.ground)
            }
        } else {
            // A person with no rooms (left their last one): a fresh room of
            // one, quietly — reading continues (§6.8).
            GrainBackground()
                .onAppear {
                    if model.me != nil {
                        model.createRoom(named: nil)
                    }
                }
        }
    }

    /// The book, opened by any door but the pull — the way in, a waiting
    /// row, a quoted verse, a note on somebody's page, a notification. It
    /// comes up from the foot of the room on the cover spring, the same
    /// movement from every door (a pushed screen used to send it up on a
    /// quicker curve than the room did); under reduce motion it fades in.
    private func openBook(_ reading: Reading, at target: VerseAddress?) {
        openTarget = target
        withAnimation(reduceMotion ? RibbonMotion.settle : RibbonMotion.cover) { openReading = reading }
    }

    /// The book closing, whichever way it was closed: settling like a book.
    private func closeBook() {
        withAnimation(RibbonMotion.settle) { openReading = nil }
        openTarget = nil
    }

    /// The pending invite, bindable for the sheet without dragging
    /// @Bindable through the environment.
    private var pendingInviteBinding: Binding<PendingInvite?> {
        Binding(
            get: { model.pendingInvite },
            set: { model.pendingInvite = $0 })
    }
}

extension AppModel {
    /// Onboarded enough to show the room: a person exists. (The room's own
    /// first-run state handles "no book yet," and a person with no rooms
    /// gets a fresh room of one, not a second onboarding.)
    var isOnboardedPerson: Bool { me != nil }
}
