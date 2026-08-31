import SwiftUI
import RibbonCore

// Cold start goes to the room you were last in. No splash screen, no
// "welcome back," no interstitial — the app opening on the room is the app
// saying nothing, which is correct (§05). The fastest path from launch to
// Scripture is the product (§6.2).

@main
struct RibbonApp: App {
    @State private var model: AppModel?

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
                    model = await AppModel.load()
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
    @State private var showRooms = false
    @State private var showYou = false
    @State private var showNewRoom = false
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
                    onOpenReading: { reading, target in
                        openTarget = target
                        withAnimation(RibbonMotion.arrive) { openReading = reading }
                    },
                    onOpenRooms: { showRooms = true })
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
                            onOpenVerse: { verse in
                                if let reading = model.openReading(in: personRoom) {
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
                        })
                    .transition(.asymmetric(
                        insertion: .opacity,
                        removal: .move(edge: .bottom).combined(with: .opacity)))
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
                NewRoomSheet { _ in }
            }
            .sheet(isPresented: $showYou) {
                YouSheet()
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
}

extension AppModel {
    /// Onboarded enough to show the room: a person exists. (The room's own
    /// first-run state handles "no book yet," and a person with no rooms
    /// gets a fresh room of one, not a second onboarding.)
    var isOnboardedPerson: Bool { me != nil }
}
