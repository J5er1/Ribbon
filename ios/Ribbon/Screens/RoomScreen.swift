import SwiftUI
import RibbonCore

// S01 — the room. Where the app opens, and the only permanent destination.
// Its job is to show the fire, say who's here, and get you into the book in
// one tap. The only chrome is the room's name, top-left, in small caps.

struct RoomScreen: View {
    @Environment(\.appModel) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    /// Set from outside when "Start another" at a finishing should land
    /// in the chooser (S13).
    @Binding var chooserRequested: Bool
    /// The book is open over the room: the fire holds its breath.
    var readingIsOpen: Bool
    var shelfNamespace: Namespace.ID
    var onOpenReading: (Reading, VerseAddress?) -> Void
    var onOpenPassageEnd: (Reading, Int) -> Void
    var onOpenRooms: () -> Void
    var onYou: () -> Void

    @State private var showChooser = false
    @State private var showInviteShare = false
    @State private var showInkPicker = false
    @State private var confirmForget = false

    private var reading: Reading? { model.openReading(in: room) }
    private var shelf: [Reading] { model.shelf(of: room) }
    private var isOffline: Bool { !model.reachability.isOnline }

    var body: some View {
        ScrollView {
            // One readable column: the phone layout, centered, instead of
            // a way-in capsule as wide as an iPad.
            VStack(alignment: .leading, spacing: 0) {
                header
                presenceLine
                    .padding(.top, 6)

                fireSection
                    .frame(maxWidth: .infinity)
                    .padding(.top, 18)

                if room.isDeparted {
                    // A room you left: its fire or ember stays for looking
                    // at, and one line says where you stand (§6.8). The
                    // quiet way to let it go, for when the shelf has been
                    // exported or isn't wanted (deviation 26).
                    VStack(spacing: 14) {
                        Text(Copy.youLeftThisRoom)
                            .font(RibbonType.ui(15))
                            .foregroundStyle(Palette.muted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                        QuietControl(title: Copy.forgetThisRoom) { confirmForget = true }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 18)
                }

                wayIn
                    .padding(.top, 26)
                    .padding(.horizontal, 24)

                waitingRows
                    .padding(.top, 26)
                    .padding(.horizontal, 24)

                shelfSection
                    .padding(.top, 44)

                quietDaySection
                    .frame(maxWidth: .infinity)
                    .padding(.top, 56)
                    .padding(.bottom, 40)
            }
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .room()
        .onChange(of: chooserRequested) { _, requested in
            if requested {
                chooserRequested = false
                showChooser = true
            }
        }
        .sheet(isPresented: $showChooser) {
            BookChooserSheet(room: room) { bookID, address in
                showChooser = false
                open(bookID: bookID, at: address)
            }
        }
        .sheet(isPresented: $showInviteShare) {
            InviteSheet(room: room)
        }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
        .confirmationDialog(Copy.forgetRoomConfirm, isPresented: $confirmForget, titleVisibility: .visible) {
            Button(Copy.forgetIt, role: .destructive) {
                withAnimation(RibbonMotion.arrive) { model.forgetDepartedRoom(room) }
            }
        }
    }

    /// The chooser chose: a book (a fire starts, or resumes), or a verse
    /// in it (S23 — the hit opens where it matched).
    private func open(bookID: String, at address: VerseAddress?) {
        if let reading, reading.bookID == bookID {
            onOpenReading(reading, address)
        } else if let address, let ember = shelf.last(where: { $0.bookID == bookID }) {
            // A finished book's verse opens its own pages (S11).
            onOpenReading(ember, address)
        } else {
            let started = model.startReading(bookID: bookID, in: room)
            onOpenReading(started, address)
        }
    }

    // MARK: Header — the entire navigation bar

    private var header: some View {
        HStack {
            Button(action: onOpenRooms) {
                SmallCaps(model.displayName(of: room), size: 14)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(Copy.opensYourRooms)
            Spacer()
            // Your own portrait, top-right — settings one tap away, from
            // anywhere the room is. (A departure from S18's two-taps-deep;
            // written down in docs/deviations.md.)
            Button(action: onYou) {
                PortraitView(
                    person: model.me,
                    ink: model.me.map { model.inkForDisplay($0.id, in: room.id) },
                    size: 28,
                    image: model.me.flatMap { model.portrait($0.id) })
                .frame(width: 44, height: 44, alignment: .trailing)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Copy.you)
            .accessibilityHint(Copy.yourAccountAndSettings)
        }
        .padding(.horizontal, 24)
        .padding(.top, 4)
    }

    // MARK: Presence line

    @ViewBuilder
    private var presenceLine: some View {
        let present = (room.isPaused || room.isDeparted) ? [] : model.presentPeople
        HStack(spacing: 4) {
            if !present.isEmpty {
                // Tap a portrait → S12.
                ForEach(present.prefix(6)) { person in
                    NavigationLink(value: PersonRoute(personID: person.id, roomID: room.id)) {
                        PortraitView(
                            person: model.person(person.id),
                            ink: model.inkForDisplay(person.id, in: room.id),
                            size: 24,
                            image: model.portrait(person.id))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            } else if !room.isDeparted, let last = model.lastReader(in: room) {
                NavigationLink(value: PersonRoute(personID: last.personID, roomID: room.id)) {
                    SmallCaps(last.line, size: 12)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            // Alone: silence. Never a line about being alone (§08).
        }
        .padding(.horizontal, 20)
        .frame(minHeight: 12)
    }

    // MARK: The fire

    @ViewBuilder
    private var fireSection: some View {
        if let reading, let book = Bible.book(id: reading.bookID) {
            let state = model.fireState(of: reading)
            VStack(spacing: 10) {
                // Deliberately inert: it is an object, not a button. A
                // change of state breathes in rather than cutting (§9.1).
                ZStack {
                    CampfireView(
                        state: state,
                        scale: reading.handiwork.scale,
                        coalDepth: reading.handiwork.coalDepth,
                        dimmed: isOffline,
                        paused: readingIsOpen)
                    .id(state)
                    .transition(.opacity)
                }
                .animation(reduceMotion ? nil : .easeInOut(duration: 1.2), value: state)
                Text(book.name)
                    .font(RibbonType.display(26))
                    .foregroundStyle(Palette.text)
                SmallCaps(state.displayName, size: 13)
                    // The fire already says its state to VoiceOver.
                    .accessibilityHidden(true)
            }
        } else if let ember = model.latestEmber(in: room), let book = Bible.book(id: ember.bookID) {
            // After a finish (§6.5): the newest ember where the fire was,
            // and the one line that goes to everyone — until the next
            // book starts.
            VStack(spacing: 10) {
                EmberView(scale: ember.handiwork.scale, seed: ember.id)
                    .scaleEffect(1.3)
                    .padding(.vertical, 16)
                Text(book.name)
                    .font(RibbonType.display(26))
                    .foregroundStyle(Palette.text)
                SmallCaps(Copy.finishedTogether(book.name), size: 12)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("\(book.name). \(Copy.finishedTogether(book.name))")
        } else if room.isDeparted {
            EmptyView()
        } else {
            // First run: the fire's place holds nothing; in its place, the
            // way to the chooser. The shelf is absent, not empty-stated.
            VStack(spacing: 18) {
                Spacer().frame(height: 40)
                Text(Copy.pickSomethingToRead)
                    .font(RibbonType.display(22))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
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
            if isOffline, !bookIsOnThisDevice(reading) {
                // Offline, and the licensed text hasn't streamed yet: the
                // control says so and is inert (S01 offline, S25).
                Text(Copy.willFinishDownloading(book.name))
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .frame(maxWidth: .infinity)
            } else {
                let hasRead = model.hasPosition(in: reading)
                WayInButton(title: hasRead ? Copy.continueIn(book.name) : Copy.begin(book.name)) {
                    onOpenReading(reading, nil)
                }
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
        } else if room.isDeparted {
            EmptyView()
        } else if model.latestEmber(in: room) != nil {
            WayInButton(title: Copy.startAnother) { showChooser = true }
        } else {
            WayInButton(title: Copy.pickABook) { showChooser = true }
        }
    }

    /// Bundled translations are always on the phone; a licensed one is
    /// here only once its chapters have streamed.
    private func bookIsOnThisDevice(_ reading: Reading) -> Bool {
        let translation = model.me?.translation ?? .bsb
        guard let licensed = TranslationRegistry.translation(for: translation), !licensed.isBundled else {
            return true
        }
        let first = VerseAddress(bookID: reading.bookID, chapter: 1, verse: 1)
        return model.scripture.cachedRemoteChapter(first, translation: licensed) != nil
    }

    // MARK: What's waiting — rows, never a count, never a badge

    @ViewBuilder
    private var waitingRows: some View {
        // Paused: waiting rows gone (S01) — the pause line stands alone.
        let quiet = room.isPaused || room.isDeparted
        let waiting = quiet ? [] : model.waitingNotes(in: room)
        let openCards = quiet ? [] : model.waitingOpenCards(in: room)
        // Zero to four rows in all (S01): the cards and the ink take theirs
        // from the notes' share.
        let noteBudget = max(0, 4 - (openCards.isEmpty ? 0 : 1) - (!quiet && model.needsInkPick(in: room) ? 1 : 0))
        VStack(alignment: .leading, spacing: 14) {
            ForEach(waiting.prefix(noteBudget)) { note in
                if let author = model.person(note.authorID) {
                    Button {
                        // Jumps to that note (§6.3): the reading opens at
                        // its verse, the mark breathing.
                        if let reading { onOpenReading(reading, note.verse) }
                    } label: {
                        waitingRow(
                            ink: model.inkForDisplay(note.authorID, in: room.id),
                            text: Copy.leftYouANote(firstName(author.name), note.verse.formatted))
                    }
                    .buttonStyle(.plain)
                }
            }

            // The cards are open (§6.4): once per card, until seen.
            if let first = openCards.first, let reading {
                Button {
                    onOpenPassageEnd(reading, first.chapter)
                } label: {
                    waitingRow(ink: model.me.map { model.inkForDisplay($0.id, in: room.id) } ?? .ochre,
                               text: Copy.cardsAreOpen)
                }
                .buttonStyle(.plain)
            }

            // An ink to pick (§6.7): the invitation, not an interruption.
            if !quiet, model.needsInkPick(in: room) {
                Button { showInkPicker = true } label: {
                    waitingRow(ink: model.me.map { Ink.stable(for: $0.id) } ?? .ochre, text: Copy.anInkToPick)
                }
                .buttonStyle(.plain)
            }

            // Room of one: the invite's state, never the person. With no
            // invite handed out yet, the quiet way to hand one out.
            if !quiet, model.members(of: room).count == 1 {
                if model.liveInvite(for: room) != nil {
                    HStack(spacing: 10) {
                        SmallCaps(Copy.inviteStillOut, size: 12)
                        QuietControl(title: Copy.sendItAgain) { showInviteShare = true }
                    }
                } else {
                    QuietControl(title: Copy.inviteSomeone) { showInviteShare = true }
                }
            }
        }
    }

    private func waitingRow(ink: Ink, text: String) -> some View {
        HStack(spacing: 10) {
            InkDot(ink: ink)
            Text(text)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.leading)
            Spacer()
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }

    // MARK: The shelf, below the fold (S10)

    @ViewBuilder
    private var shelfSection: some View {
        if !shelf.isEmpty {
            ShelfView(
                room: room, readings: shelf, namespace: shelfNamespace,
                onStartAnother: (room.isPaused || room.isDeparted || reading != nil) ? nil : { showChooser = true })
        }
        // No shelf until the first book is finished — an empty shelf is a
        // reproach.
    }

    // MARK: Mark a quiet day (§4.7)

    // Always present while there is a fire to bank, never emphasised. The
    // room sees who banked it, and when, in the coarse voice of §4.9: an
    // act of care, performed in public — above the control, never in
    // place of it. Absent when there is no fire, and in a paused room
    // (read everything, write nothing).
    @ViewBuilder
    private var quietDaySection: some View {
        if reading != nil, !room.isPaused, !room.isDeparted {
            let quiet = model.activeQuietDay(in: room)
            // Whether *you* banked it today — not merely whoever banked it
            // first — so the control rests once you have, even after
            // someone else got there before you.
            let bankedByMe = model.quietDays(for: room).contains {
                $0.personID == model.me?.id && $0.bankedInterval?.contains(Date()) == true
            }
            VStack(spacing: 12) {
                if let quiet, let name = model.person(quiet.personID)?.name {
                    let phrase = RibbonClock.phrase(for: quiet.markedAt)
                    SmallCaps(
                        quiet.personID == model.me?.id
                            ? Copy.youBankedTheFire(phrase)
                            : Copy.bankedTheFire(firstName(name), phrase),
                        size: 12)
                }
                if bankedByMe {
                    SmallCaps(Copy.quietDayMarked, size: 12, color: Palette.muted.opacity(0.6))
                } else {
                    QuietControl(title: Copy.markAQuietDay) {
                        withAnimation(RibbonMotion.settle) {
                            model.markQuietDay(in: room)
                        }
                    }
                }
            }
        }
    }
}
