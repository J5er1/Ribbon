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
                        .environment(model)
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
                if phase == .active, let model {
                    Task { await model.refreshFromRemote() }
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
                        withAnimation(RibbonMotion.arrive) { openReading = reading }
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
                    .transition(.asymmetric(
                        insertion: .opacity,
                        removal: .move(edge: .bottom).combined(with: .opacity)))
                }
            }
            // The menu, full screen. It is one screen rather than the
            // book's two sheets, and it holds the rooms, the room you are
            // in, and you — the whole of what used to be S14 and S18, with
            // the two doors that were missing from both (deviations 14).
            .fullScreenCover(item: $menu) { entry in
                MenuScreen(entry: entry)
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
                if pending != nil { menu = nil }
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
