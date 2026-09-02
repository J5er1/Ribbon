import SwiftUI
import RibbonCore

// Cold start goes to the room you were last in. No splash screen, no
// "welcome back," no interstitial — the app opening on the room is the app
// saying nothing, which is correct (§05). The fastest path from launch to
// Scripture is the product (§6.2).

@main
struct RibbonApp: App {
    @State private var model: AppModel?
    /// A URL that arrived before the model finished loading — the normal
    /// case when tapping an invite link cold-starts the app (S16).
    @State private var bufferedURL: URL?
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if let model {
                    RootView()
                        .environment(\.appModel, model)
                } else {
                    // One frame of the unlit ground while state loads from
                    // disk — indistinguishable from the launch screen.
                    GrainBackground()
                }
            }
            .preferredColorScheme(.dark)
            .task {
                if model == nil {
                    let loaded = await AppModel.load()
                    if let bufferedURL {
                        loaded.handleInviteURL(bufferedURL)
                        self.bufferedURL = nil
                    }
                    model = loaded
                    // The room renders from local state instantly; the
                    // backend catches up behind it.
                    await loaded.foregroundSync()
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
                model.scenePhaseChanged(to: phase)
                if phase == .active {
                    Task { await model.foregroundSync() }
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
    @Environment(\.appModel) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var onboarding = false
    @State private var openReading: Reading?
    /// Where the reading should open, when a row or a quoted verse named a
    /// place (§6.3, S11). Nil means your own position.
    @State private var openTarget: VerseAddress?
    /// Open at a chapter's end — the card there (S08/S09).
    @State private var openAtPassageEnd: Int?
    @State private var showRooms = false
    @State private var showYou = false
    @State private var showNewRoom = false
    /// The finishing sequence's "Start another" lands in the chooser (S13).
    @State private var chooserRequested = false
    @State private var navigationPath = NavigationPath()
    /// The ember grows into its own record (§12.1 — the one place the
    /// system's zoom is exactly the right metaphor).
    @Namespace private var shelfNamespace

    var body: some View {
        Group {
            if onboarding || !model.isOnboardedPerson {
                OnboardingFlow { reading in
                    onboarding = false
                    if let reading {
                        // The thread ends in the book itself (§6.1).
                        openTarget = nil
                        openReading = reading
                    }
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
                    readingIsOpen: openReading != nil,
                    shelfNamespace: shelfNamespace,
                    onOpenReading: { reading, target in
                        openTarget = target
                        openAtPassageEnd = nil
                        withAnimation(RibbonMotion.arrive) { openReading = reading }
                    },
                    onOpenPassageEnd: { reading, chapter in
                        openTarget = nil
                        openAtPassageEnd = chapter
                        withAnimation(RibbonMotion.arrive) { openReading = reading }
                    },
                    onOpenRooms: { showRooms = true },
                    onYou: { showYou = true })
                // The room cross-fades when you switch rooms (S14, 320 ms).
                .id(room.id)
                .transition(.opacity)
                .navigationDestination(for: UUID.self) { readingID in
                    if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                        EmberRecordScreen(
                            reading: reading,
                            onOpenVerse: { verse in
                                // A quoted verse opens the reading at that
                                // verse (S11) — the finished book's own
                                // pages, not a copy.
                                openTarget = verse
                                openAtPassageEnd = nil
                                withAnimation(RibbonMotion.arrive) { openReading = reading }
                            },
                            onReadAgain: { bookID in
                                navigationPath = NavigationPath()
                                let new = model.startReading(bookID: bookID, in: room)
                                openTarget = nil
                                withAnimation(RibbonMotion.arrive) { openReading = new }
                            })
                        .navigationTransition(.zoom(sourceID: reading.id, in: shelfNamespace))
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
                                    openAtPassageEnd = nil
                                    withAnimation(RibbonMotion.arrive) { openReading = reading }
                                }
                            })
                    }
                }
                .toolbarVisibility(.hidden, for: .navigationBar)
            }
            .animation(RibbonMotion.arrive, value: room.id)
            .overlay {
                // The reading is a full-screen cover in spirit, but drawn
                // in-tree so the closing drag settles like a book (S02) —
                // sliding down over the room rather than a system sheet.
                if let reading = openReading {
                    ReadingScreen(
                        room: room,
                        reading: reading,
                        openAt: openTarget,
                        openAtPassageEnd: openAtPassageEnd,
                        onClose: {
                            withAnimation(RibbonMotion.settle) { openReading = nil }
                            openTarget = nil
                            openAtPassageEnd = nil
                        },
                        onFinished: {
                            withAnimation(RibbonMotion.settle) { openReading = nil }
                            openTarget = nil
                            openAtPassageEnd = nil
                        },
                        onStartAnother: {
                            withAnimation(RibbonMotion.settle) { openReading = nil }
                            openTarget = nil
                            openAtPassageEnd = nil
                            chooserRequested = true
                        })
                    .transition(.asymmetric(
                        insertion: .opacity,
                        removal: reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity)))
                }
            }
            .sheet(isPresented: $showRooms) {
                RoomsSheet(
                    onSwitch: { roomID in
                        withAnimation(RibbonMotion.arrive) { model.switchRoom(to: roomID) }
                    },
                    onStartRoom: {
                        showRooms = false
                        showNewRoom = true
                    },
                    onYou: {
                        showRooms = false
                        showYou = true
                    })
            }
            .sheet(isPresented: $showNewRoom) {
                // Naming and inviting are two steps that should feel like
                // one (S15) — one sheet, the invite following the name.
                NewRoomSheet { _ in }
            }
            .sheet(isPresented: $showYou) {
                YouSheet()
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
            // one, quietly — reading continues (§6.8). Not while a restore
            // is still bringing their rooms back (§6.10).
            GrainBackground()
                .task(id: model.restoringRooms) {
                    guard model.me != nil, !model.restoringRooms, model.liveRooms.isEmpty else { return }
                    model.createRoom(named: nil)
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
