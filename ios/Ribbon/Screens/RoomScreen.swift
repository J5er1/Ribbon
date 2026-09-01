import SwiftUI
import RibbonCore

// S01 — the room. Where the app opens, and the only permanent destination.
// Its job is to show the fire, say who's here, and get you into the book in
// one tap. The only chrome is the room's name, top-left, in small caps.

struct RoomScreen: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onOpenReading: (Reading, VerseAddress?) -> Void
    var onOpenRooms: () -> Void

    @State private var showChooser = false
    @State private var showInviteShare = false

    private var reading: Reading? { model.openReading(in: room) }
    private var shelf: [Reading] { model.shelf(of: room) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                presenceLine
                    .padding(.top, 6)

                fireSection
                    .frame(maxWidth: .infinity)
                    .padding(.top, 18)

                wayIn
                    .padding(.top, 26)
                    .padding(.horizontal, 24)

                waitingRows
                    .padding(.top, 26)
                    .padding(.horizontal, 24)

                shelfSection
                    .padding(.top, 44)

                // Mark a quiet day — always present, never emphasised. The
                // room sees who banked the fire (§4.7): an act of care,
                // performed in public, above the control rather than in
                // place of it.
                VStack(spacing: 12) {
                    if let quiet = model.activeQuietDay(in: room),
                       let name = model.person(quiet.personID)?.name {
                        SmallCaps(Copy.bankedTheFire(firstName(name)), size: 12)
                    }
                    QuietControl(title: Copy.markAQuietDay) {
                        withAnimation(RibbonMotion.settle) {
                            model.markQuietDay(in: room)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 56)
                .padding(.bottom, 40)
            }
        }
        .scrollIndicators(.hidden)
        .room()
        .sheet(isPresented: $showChooser) {
            BookChooserSheet(room: room) { bookID in
                showChooser = false
                let reading = model.startReading(bookID: bookID, in: room)
                onOpenReading(reading, nil)
            }
        }
    }

    // MARK: Header — the entire navigation bar

    private var header: some View {
        HStack {
            Button(action: onOpenRooms) {
                SmallCaps(model.displayName(of: room), size: 14)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens your rooms")
            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
    }

    // MARK: Presence line

    @ViewBuilder
    private var presenceLine: some View {
        let present = room.isPaused ? [] : model.presentPeople
        HStack(spacing: 8) {
            if !present.isEmpty {
                // Tap a portrait → S12.
                ForEach(present.prefix(6)) { person in
                    NavigationLink(value: PersonRoute(personID: person.id, roomID: room.id)) {
                        PortraitView(
                            person: model.person(person.id),
                            ink: model.membership(of: person.id, in: room.id)?.ink,
                            size: 24,
                            image: model.portrait(person.id))
                    }
                    .buttonStyle(.plain)
                }
            } else if let last = model.lastReader(in: room) {
                NavigationLink(value: PersonRoute(personID: last.personID, roomID: room.id)) {
                    SmallCaps(last.line, size: 12)
                }
                .buttonStyle(.plain)
            }
            // Alone: silence. Never a line about being alone (§08).
        }
        .padding(.horizontal, 24)
        .frame(minHeight: 12)
    }

    // MARK: The fire

    @ViewBuilder
    private var fireSection: some View {
        if let reading, let book = Bible.book(id: reading.bookID) {
            let state = model.fireState(of: reading)
            VStack(spacing: 10) {
                // Deliberately inert: it is an object, not a button.
                CampfireView(
                    state: state,
                    scale: reading.handiwork.scale,
                    coalDepth: reading.handiwork.coalDepth)
                Text(book.name)
                    .font(RibbonType.display(26))
                    .foregroundStyle(Palette.text)
                SmallCaps(state.displayName, size: 13)
            }
        } else {
            // First run: the fire's place holds nothing; in its place, the
            // chooser. The shelf is absent, not empty-stated.
            VStack(spacing: 18) {
                Spacer().frame(height: 40)
                Text(Copy.pickABook)
                    .font(RibbonType.display(22))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 40)
        }
    }

    // MARK: The way in

    // Scripture is never locked (§2.5): a paused room keeps its way in —
    // the pause line is added, the waiting rows go.
    @ViewBuilder
    private var wayIn: some View {
        if let reading, let book = Bible.book(id: reading.bookID) {
            let hasRead = model.state.positions.contains {
                $0.readingID == reading.id && $0.personID == model.me?.id
            }
            WayInButton(title: hasRead ? Copy.continueIn(book.name) : Copy.begin(book.name)) {
                onOpenReading(reading, nil)
            }
            if room.isPaused {
                Text(Copy.roomPaused)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 14)
            }
        } else if room.isPaused {
            Text(Copy.roomPaused)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)
                .frame(maxWidth: .infinity)
        } else {
            WayInButton(title: Copy.pickABook) { showChooser = true }
        }
    }

    // MARK: What's waiting — rows, never a count, never a badge

    @ViewBuilder
    private var waitingRows: some View {
        // Paused: waiting rows gone (S01) — the pause line stands alone.
        let waiting = room.isPaused ? [] : model.waitingNotes(in: room)
        let memberCount = model.members(of: room).count
        VStack(alignment: .leading, spacing: 14) {
            ForEach(waiting.prefix(4)) { note in
                if let author = model.person(note.authorID) {
                    Button {
                        // Jumps to that note (§6.3): the reading opens at
                        // its verse, the mark breathing.
                        if let reading { onOpenReading(reading, note.verse) }
                    } label: {
                        HStack(spacing: 10) {
                            InkDot(ink: model.membership(of: note.authorID, in: room.id)?.ink ?? .clay)
                            Text(Copy.leftYouANote(firstName(author.name), note.verse.formatted))
                                .font(RibbonType.ui(15))
                                .foregroundStyle(Palette.text)
                            Spacer()
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            // Room of one, invite still out — the state, not the person.
            if memberCount == 1, !room.isPaused {
                HStack(spacing: 10) {
                    SmallCaps(Copy.inviteStillOut, size: 12)
                    QuietControl(title: Copy.sendItAgain) { showInviteShare = true }
                }
            }
        }
        .sheet(isPresented: $showInviteShare) {
            InviteSheet(room: room)
                .presentationDetents([.medium])
        }
    }

    // MARK: The shelf, below the fold (S10)

    @ViewBuilder
    private var shelfSection: some View {
        if !shelf.isEmpty {
            ShelfView(room: room, readings: shelf, onStartAnother: { showChooser = true })
        }
        // No shelf until the first book is finished — an empty shelf is a
        // reproach.
    }
}

func firstName(_ name: String) -> String {
    name.split(separator: " ").first.map(String.init) ?? name
}
