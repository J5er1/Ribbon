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
        RoomWatch.register()
        Task { @MainActor in
            RoomWatch.pull = {
                // The app may be awake with its model, or this may be a
                // wake from nothing: either way, one pull, and the merge
                // posts what arrived (S19).
                if let model = AppSession.model {
                    await model.refreshFromRemote()
                } else {
                    let model = await AppModel.load(forBackgroundPull: true)
                    await model.refreshFromRemote()
                }
            }
        }
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
                    onOpenReading: { reading, target in
                        openTarget = target
                        withAnimation(RibbonMotion.cover) { openReading = reading }
                    },
                    onOpenRooms: { menu = .rooms },
                    onYou: { menu = .you })
                .navigationDestination(for: UUID.self) { readingID in
                    if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                        EmberRecordScreen(
                            reading: reading,
                            onOpenVerse: { verse in
                                // A quoted verse opens the reading at that
                                // verse (S11) — the finished book's own
                                // pages, not a copy.
                                openTarget = verse
                                withAnimation(RibbonMotion.arrive) { openReading = reading }
                            },
                            onReadAgain: { bookID in
                                navigationPath = NavigationPath()
                                let new = model.startReading(bookID: bookID, in: room)
                                openTarget = nil
                                withAnimation(RibbonMotion.arrive) { openReading = new }
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
                                    openTarget = verse
                                    withAnimation(RibbonMotion.arrive) { openReading = reading }
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
                if let reading = openReading {
                    ReadingScreen(
                        room: room,
                        reading: reading,
                        openAt: openTarget,
                        onClose: {
                            withAnimation(RibbonMotion.settle) { openReading = nil }
                            openTarget = nil
                        },
                        onFinished: {
                            withAnimation(RibbonMotion.settle) { openReading = nil }
                            openTarget = nil
                        },
                        onStartAnother: {
                            withAnimation(RibbonMotion.settle) { openReading = nil }
                            openTarget = nil
                            chooserRequested = true
                        })
                    // The page comes up from the foot of the screen, the
                    // way the pull on the fire started it, and goes back
                    // down the same way when the book closes.
                    .transition(.asymmetric(
                        insertion: .move(edge: .bottom),
                        removal: .move(edge: .bottom).combined(with: .opacity)))
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
                        openTarget = verse
                        withAnimation(RibbonMotion.cover) { openReading = reading }
                    }
                case .cards(_, let readingID, let chapter):
                    if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                        openTarget = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: 1)
                        withAnimation(RibbonMotion.cover) { openReading = reading }
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
