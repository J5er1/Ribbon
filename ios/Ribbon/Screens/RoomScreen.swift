import SwiftUI
import RibbonCore

// S01 — the room. Where the app opens, and the only permanent destination.
// Its job is to show the fire, say who's here, and get you into the book in
// one pull (ledger A48). Its chrome is all at the foot, in the thumb's
// reach (I47): the book's ribbon, your rooms, and your own face.
//
// The hearth. The fire stands on the floor in its own light, its book's
// name under it — no card, no well (I47). Pulling the fire up opens the
// book: the fire swells and rises under the finger, and past a fifth of the
// travel (or on a flick) the page comes up from the foot of the screen.
// Pulling it down at the top of the room opens your rooms. The ribbon in
// the foot does the first with a pull or a tap, the pill beside it the
// second (§11).

struct RoomScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    /// Set from outside when "Start another" at a finishing should land
    /// in the chooser (S13).
    @Binding var chooserRequested: Bool
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onOpenRooms: () -> Void
    var onYou: () -> Void
    /// The pull on the fire, 0 where it sits and 1 at the end of its
    /// travel. Owned by the root, because the page rises on the same
    /// number the fire moves on (A48): one number, so the two cannot fall
    /// out of step.
    @Binding var bookPull: CGFloat
    /// The finger has taken hold of the fire: the page is built now, under
    /// the room, so the pull has something to lift.
    var onBeginOpening: (Reading) -> Void
    /// Let go past the commit, or flung: the root carries the page the rest
    /// of the way. The speed is the pull's own, in pulls per second, upward.
    var onFinishOpening: (Reading, CGFloat) -> Void
    /// Let go short of the commit: the page goes back down with the fire,
    /// at the speed the finger let go at.
    var onAbandonOpening: (CGFloat) -> Void
    /// The ribbon in the foot pulled: the book, and where the ribbon was,
    /// for the root to carry it up into the page (I47).
    var onPullRibbon: (Reading, CGRect) -> Void
    /// The foot's ribbon is up in the page, drawn by the root.
    var ribbonAway: Bool
    /// A name in the room's sentences: that person's page (S12).
    var onOpenPerson: (UUID) -> Void

    @State private var showChooser = false
    /// The book just chosen, begun, and waiting for the chooser to have
    /// gone before it opens (I45).
    @State private var chosen: Reading?
    /// What is behind the ribbon, held in the foot (I47), and the place
    /// chosen in it: the book opens there once the list has gone.
    @State private var showRibbonList = false
    @State private var ribbonPlace: VerseAddress?
    @State private var showInviteShare = false
    @State private var showInkPicker = false
    /// The first screen of the room, above the fold (I47): how tall it is,
    /// and how much of it the greeting takes. The fire stands in the rest.
    @State private var fold: CGFloat = 600
    @State private var greetingHeight: CGFloat = 120
    /// The foot's drawer is up: a quiet day, another book, the ribbon.
    @State private var footOpen = false

    private var reading: Reading? { model.openReading(in: room) }
    private var shelf: [Reading] { model.shelf(of: room) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Above the fold (I47): the greeting, the room in a few
                // sentences, and the fire in its own light. Everything else
                // is under it, for whoever scrolls.
                Greeting(
                    room: room, reading: reading,
                    onOpenReading: onOpenReading, onOpenPerson: onOpenPerson)
                    .padding(.horizontal, RoomMetrics.gutter)
                    .padding(.top, 36)
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { greetingHeight = $0 }

                HearthView(
                    room: room,
                    reading: reading,
                    pull: $bookPull,
                    onOpenReading: onOpenReading,
                    onBeginOpening: onBeginOpening,
                    onFinishOpening: onFinishOpening,
                    onAbandonOpening: onAbandonOpening,
                    onOpenRooms: onOpenRooms)
                .padding(.horizontal, RoomMetrics.gutter)
                // The fire in the middle of what the greeting leaves of the
                // first screen.
                .frame(maxWidth: .infinity, minHeight: max(0, fold - greetingHeight), alignment: .center)

                // Under the fold.
                VStack(alignment: .leading, spacing: 0) {
                    if reading == nil && shelf.isEmpty && !room.isPaused {
                        StarterShelf { bookID in
                            let started = model.startReading(bookID: bookID, in: room)
                            onOpenReading(started, nil)
                        }
                        .padding(.bottom, 4)
                        .transition(.opacity)
                    }

                    WaitingSection(
                        room: room,
                        reading: reading,
                        onOpenReading: onOpenReading,
                        onSendItAgain: { showInviteShare = true },
                        onPickAnInk: { showInkPicker = true })
                    .padding(.horizontal, RoomMetrics.gutter)

                }
                .padding(.top, 8)
                .padding(.bottom, 48)
            }
            .readableColumn()
            .animation(RibbonMotion.settle(still: reduceMotion), value: shelf.count)
            .animation(RibbonMotion.settle(still: reduceMotion), value: reading?.id)
        }
        // The first screen: what the scroll view shows between the clock and
        // the foot (its container is already inside both).
        .onScrollGeometryChange(for: CGFloat.self) { geometry in
            geometry.containerSize.height
        } action: { _, height in
            if height > 0 { fold = height }
        }
        // The foot's drawer up: the room dims under it, and a touch on the
        // room puts it away.
        .overlay {
            if footOpen {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()
                    .onTapGesture { footOpen = false }
                    .transition(.opacity)
                    .accessibilityHidden(true)
            }
        }
        // The room dims as the drawer comes up, and comes back as it goes:
        // a change of light, on its curve under reduce motion too.
        .animation(RibbonMotion.settle, value: footOpen)
        .scrollIndicators(.hidden)
        // The room's tiles are glass, as its foot is (I47).
        .environment(\.tileFinish, .glass)
        // The foot stands over the room, and the room scrolls under it.
        .safeAreaInset(edge: .bottom, spacing: 0) {
            RoomFoot(
                room: room, reading: reading, ribbonAway: ribbonAway,
                drawerOpen: $footOpen,
                onOpen: onPullRibbon,
                onHold: { _ in showRibbonList = true },
                onStartAnother: { showChooser = true },
                onPickABook: { showChooser = true },
                onOpenRooms: onOpenRooms,
                onYou: onYou)
        }
        .room()
        .onChange(of: chooserRequested) { _, requested in
            if requested {
                chooserRequested = false
                showChooser = true
            }
        }
        .sheet(isPresented: $showChooser, onDismiss: {
            // The chooser goes down, the new fire is lit on the hearth it
            // uncovers, and then the book comes up (I45): one movement and
            // then the next, where the sheet used to go down through a page
            // on its way up.
            guard let started = chosen else { return }
            chosen = nil
            onOpenReading(started, nil)
        }) {
            BookChooserSheet(room: room) { bookID in
                chosen = model.startReading(bookID: bookID, in: room)
                showChooser = false
            }
        }
        .sheet(isPresented: $showRibbonList, onDismiss: {
            guard let place = ribbonPlace, let reading else { return }
            ribbonPlace = nil
            onOpenReading(reading, .verse(place))
        }) {
            if let reading {
                RibbonListSheet(
                    room: room, reading: reading,
                    chapter: model.myPosition(in: reading).chapter,
                    translation: model.words(room: room)
                ) { address in
                    ribbonPlace = address
                    showRibbonList = false
                }
            }
        }
        .sheet(isPresented: $showInviteShare) {
            InviteSheet(room: room)
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
    }
}

enum RoomMetrics {
    static let gutter: CGFloat = 24
    static let touch: CGFloat = 44
    /// How far the hearth follows the fire up, as a fraction of the pull.
    static let hearthFollow: CGFloat = 0.35
    /// How far the fire can be pulled down before it stops, and the
    /// distance that opens the rooms.
    static let roomSink: CGFloat = 28
    static let roomCommit: CGFloat = 96
}

// MARK: - The greeting and who is here

/// "Good evening, Ruth." — by the hour on this phone — and under it the
/// room, said in a few sentences (I47): who is here, how the fire is, where
/// the ribbon was left. In the book's own type, in the full ink, where they
/// were four lines of small grey type in four places; a name or a place in
/// them is underlined in the accent and goes there.
private struct Greeting: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let reading: Reading?
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onOpenPerson: (UUID) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The title has come up out of the dark: once each time the room is
    /// shown.
    @State private var shown = false
    @State private var linesShown = false

    /// A title card (I47): *"make the good evening and the fire is burning
    /// more cinimatic and in the background"*. The greeting set large and
    /// centred over the fire, as a film's title stands over its first
    /// shot, in a warm ivory with the fire's glow behind it — and the room's
    /// sentences under it in italic. It comes up out of a blur when the room
    /// does, and the sentences a moment after; under reduce motion both only
    /// fade. Your name is in the colour of your portrait (I47).
    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let hour = Calendar.current.component(.hour, from: context.date)
            VStack(spacing: 16) {
                Text(greeting(hour: hour))
                    .font(RibbonType.displayMedium(44))
                    .tracking(0.5)
                    .lineSpacing(-4)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Color(hex: "F1E6CF"))
                    .shadow(color: Palette.flameCore.opacity(0.12), radius: 26, y: 10)
                    .accessibilityAddTraits(.isHeader)
                    .contentTransition(.opacity)
                    .animation(RibbonMotion.settle, value: hour)
                    .opacity(shown ? 1 : 0)
                    .blur(radius: shown || reduceMotion ? 0 : 14)
                    .scaleEffect(shown || reduceMotion ? 1 : 1.05)
                RoomLines(
                    room: room, reading: reading,
                    onOpenReading: onOpenReading, onOpenPerson: onOpenPerson)
                    .opacity(linesShown ? 1 : 0)
                    .offset(y: linesShown || reduceMotion ? 0 : 6)
            }
            .frame(maxWidth: .infinity)
        }
        .onAppear {
            withAnimation(.easeOut(duration: reduceMotion ? 0.6 : 1.6)) { shown = true }
            withAnimation(.easeOut(duration: 1).delay(reduceMotion ? 0.2 : 0.7)) { linesShown = true }
        }
    }
}

extension Greeting {
    /// "Good evening," and, under it, your name in its colour: your
    /// portrait's, or the one you chose on You.
    fileprivate func greeting(hour: Int) -> AttributedString {
        var text = AttributedString(
            Copy.greeting(model.me?.name, hour: hour).replacingOccurrences(of: ", ", with: ",\n"))
        if let me = model.me, let tint = model.nameTint,
           let name = text.range(of: firstName(me.name)) {
            text[name].foregroundColor = tint
        }
        return text
    }
}

/// The room in sentences (I47). Alone, with the fire out and no ribbon to
/// offer, nothing: never a line about being alone (§08).
private struct RoomLines: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let reading: Reading?
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onOpenPerson: (UUID) -> Void

    var body: some View {
        let said = lines()
        Group {
            if !said.characters.isEmpty {
                Text(said)
                    .font(RibbonType.scriptureItalic(17))
                    .foregroundStyle(Palette.text.opacity(0.78))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .tint(Palette.text)
                    .fixedSize(horizontal: false, vertical: true)
                    .contentTransition(.opacity)
                    .transition(.opacity)
            }
        }
        .animation(RibbonMotion.settle, value: String(said.characters))
        .environment(\.openURL, OpenURLAction { url in
            let parts = url.pathComponents.filter { $0 != "/" }
            switch url.host() {
            case "person":
                if let id = parts.first.flatMap(UUID.init(uuidString:)) { onOpenPerson(id) }
            case "verse":
                if let reading, parts.count == 2, let chapter = Int(parts[0]), let verse = Int(parts[1]) {
                    onOpenReading(reading, .verse(VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)))
                }
            default:
                return .discarded
            }
            return .handled
        })
    }

    private func lines() -> AttributedString {
        var said: [AttributedString] = []

        // Who is here, or who was last.
        let present = room.isPaused ? [] : model.presentPeople
        if let first = present.first {
            let name = firstName(first.name)
            let base = first.isIdle ? Copy.personIsHereButStill(name) : Copy.personIsReading(name)
            let others = present.dropFirst().map { firstName($0.name) }
            let line = others.isEmpty ? base : Copy.alsoHere(base, Array(others))
            said.append(linked(line + ".", name, to: "ribbon-room://person/\(first.id.uuidString)"))
        } else if !room.isPaused, let last = model.lastReader(in: room),
                  let person = model.person(last.personID) {
            said.append(linked(last.line + ".", firstName(person.name), to: "ribbon-room://person/\(last.personID.uuidString)"))
        }

        if let reading, !room.isPaused {
            // How the fire is, or who banked it today (§4.7).
            if let banked = model.activeQuietDay(in: room), let person = model.person(banked.personID) {
                let name = firstName(person.name)
                said.append(linked(Copy.bankedTheFire(name) + ".", name, to: "ribbon-room://person/\(person.id.uuidString)"))
            } else {
                said.append(AttributedString(Copy.fireIs(model.fireState(of: reading).displayName)))
            }
        }

        // What is waiting for you, under the fold: a note, or notes.
        let waiting = room.isPaused ? [] : model.waitingNotes(in: room)
        if let first = waiting.first, let author = model.person(first.authorID) {
            let authors = waiting.compactMap { model.person($0.authorID).map { firstName($0.name) } }
            let names = authors.reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
            let verse = first.verse.formatted
            if waiting.count == 1 {
                let line = first.kind == .voice
                    ? Copy.leftYouAVoiceNote(firstName(author.name), verse)
                    : Copy.leftYouANote(firstName(author.name), verse)
                said.append(linked(line + ".", verse, to: "ribbon-room://verse/\(first.verse.chapter)/\(first.verse.verse)"))
            } else {
                said.append(linked(Copy.leftYouNotes(names), "notes", to: "ribbon-room://verse/\(first.verse.chapter)/\(first.verse.verse)"))
            }
        }

        // The ribbon, where it is somewhere other than where you are.
        if let reading, let ribbon = model.ribbonWorthOffering(in: reading), let book = Bible.book(id: reading.bookID) {
            let reference = "\(book.chapterHeading(ribbon.chapter)):\(ribbon.verse)"
            let line = model.me?.id == ribbon.personID
                ? Copy.youLeftTheRibbonAt(reference)
                : Copy.ribbonIsAt(model.person(ribbon.personID).map { firstName($0.name) }, reference)
            said.append(linked(line, reference, to: "ribbon-room://verse/\(ribbon.chapter)/\(ribbon.verse)"))
        }

        if room.isPaused {
            // Scripture is never locked (§2.5): the way in stays, and this
            // says why the room is quiet.
            said.append(AttributedString(Copy.roomPaused))
        }

        var text = AttributedString()
        for (index, line) in said.enumerated() {
            if index > 0 { text.append(AttributedString(" ")) }
            text.append(line)
        }
        return text
    }

    /// A sentence with one phrase in it underlined in the accent and going
    /// somewhere.
    private func linked(_ sentence: String, _ phrase: String, to link: String) -> AttributedString {
        var text = AttributedString(sentence)
        if let range = text.range(of: phrase), let url = URL(string: link) {
            text[range].link = url
            text[range].foregroundColor = Palette.text
            text[range].underlineStyle = Text.LineStyle(pattern: .solid, color: Palette.chartreuse.opacity(0.6))
        }
        return text
    }
}

// MARK: - The hearth

private struct HearthView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    let reading: Reading?
    /// 0 where the fire sits, 1 at the end of its travel up.
    @Binding var pull: CGFloat
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onBeginOpening: (Reading) -> Void
    var onFinishOpening: (Reading, CGFloat) -> Void
    var onAbandonOpening: (CGFloat) -> Void
    var onOpenRooms: () -> Void

    @State private var travel: CGFloat = 320
    /// Which way the stone is turned (I47): 0 with the fire in front, 1
    /// with the jar; in between while a finger turns it.
    @State private var turned: Double = 0
    @State private var dragTurn: Double = 0
    @State private var turning = false
    /// The stone turned a little and back, once, to show that it turns and
    /// that something stands round the back (I47: *"micro animations that
    /// show where things are but not just to look pretty"*).
    @State private var peek: Double = 0
    /// The rooms whose stone has shown that, since the app opened.
    @MainActor private static var peeked: Set<UUID> = []

    /// The height of what stands on the stone, the tallest fire's.
    private static let stage: CGFloat = 230
    /// How far a finger drags for one whole turn from one thing to the
    /// next: most of the screen's width, a full swipe.
    private static let pointsPerTurn: CGFloat = 240


    // The hearth (I47): the stone the fire stands on, and on it the things
    // the room keeps — the fire, and, once a book has been put on the
    // shelf, the room's jar, out of sight until it is asked for (*"the jar
    // to the side needs to be hidden untill swipe"*). A full swipe, either
    // way, exchanges them: the fire slides a little with the finger and
    // fades, and the jar slides in from the other side and comes up, on the
    // stone, where a tap opens it; another brings the fire back, where the
    // pull opens the book. The stone stays still — it swung round, and the
    // owner found it *"jarring with the pedastel mobing and being cut off
    // on a side"*. Two small marks under the name say there is another.
    // Room on the stone for more, later.
    var body: some View {
        let shelf = model.shelf(of: room)
        let things = shelf.isEmpty ? 1 : 2
        let position = turned + dragTurn + peek
        let front = ((Int((turned + dragTurn).rounded()) % things) + things) % things
        let state = reading.map { model.fireState(of: $0) }
        VStack(spacing: 0) {
            ZStack(alignment: .bottom) {
                // The light, and the stone.
                ZStack {
                    if let state {
                        HearthLight(state: state, dimmed: !model.isOnline)
                            .offset(y: -10)
                            // Banked, or caught again: the light goes down,
                            // or comes up, over a moment. A change of light,
                            // so it keeps its curve under reduce motion.
                            .animation(.easeInOut(duration: 1.6), value: state)
                    }
                    Hearthstone(warmth: state == nil ? 0.2 : state == .banked ? 0.4 : 1)
                        .offset(y: -16)
                }
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
                .accessibilityHidden(true)

                fireSlot(isFront: front == 0)
                    .modifier(OnTheStone(position: position, index: 0, things: things))

                if !shelf.isEmpty {
                    jarSlot(shelf, isFront: front == 1)
                        .modifier(OnTheStone(position: position, index: 1, things: things))
                }
            }
            // A stage of one height whatever stands on it, so the stone
            // never moves when the jar, taller than a small fire, comes in;
            // taller only for the words of an unlit hearth.
            .frame(maxWidth: .infinity)
            .frame(minHeight: Self.stage, alignment: .bottom)
            .contentShape(Rectangle())
            .simultaneousGesture(turn(things: things), including: things > 1 ? .all : .subviews)

            // What is in front: the fire's book, or the shelf.
            Group {
                if front == 1 {
                    Text(Copy.theShelf)
                } else if let reading {
                    Text(Bible.book(id: reading.bookID)?.name ?? reading.bookID)
                }
            }
            .font(RibbonType.displayMedium(24))
            .foregroundStyle(Palette.text)
            .contentTransition(.opacity)
            .animation(RibbonMotion.settle, value: front)
            .padding(.top, 34)
            .accessibilityHidden(true)

            // Something round the back: a mark for each thing on the stone.
            if things > 1 {
                HStack(spacing: 7) {
                    ForEach(0..<things, id: \.self) { index in
                        Circle()
                            .fill(Palette.text.opacity(index == front ? 0.7 : 0.22))
                            .frame(width: 5, height: 5)
                    }
                }
                .padding(.top, 10)
                .animation(RibbonMotion.settle, value: front)
                .accessibilityHidden(true)
            }
        }
        // A fire catching where the hearth was unlit is a change of light:
        // it cross-fades under reduce motion too.
        .animation(RibbonMotion.arrive, value: reading?.id)
        .offset(y: -pull * travel * RoomMetrics.hearthFollow)
        .opacity(Double(max(CGFloat(0), min(CGFloat(1), 1 - pull * 1.25))))
        .background {
            GeometryReader { proxy in
                Color.clear.onAppear { travel = max(200, proxy.size.height * RibbonMotion.openTravel) }
                    .onChange(of: proxy.size.height) { _, height in travel = max(200, height * RibbonMotion.openTravel) }
            }
        }
        .onChange(of: shelf.isEmpty) { _, empty in
            if empty { turned = 0 }
        }
        .task(id: things) {
            guard things > 1, !reduceMotion, !Self.peeked.contains(room.id) else { return }
            // After the greeting has come up.
            try? await Task.sleep(for: .seconds(2.6))
            guard !Task.isCancelled, !turning, turned == 0, pull == 0 else { return }
            Self.peeked.insert(room.id)
            withAnimation(.easeInOut(duration: 0.6)) { peek = 0.25 }
            try? await Task.sleep(for: .seconds(0.75))
            withAnimation(RibbonMotion.handled) { peek = 0 }
        }
    }

    @ViewBuilder
    private func fireSlot(isFront: Bool) -> some View {
        Group {
            if let reading, Bible.book(id: reading.bookID) != nil {
                TheFire(
                    room: room, reading: reading, pull: $pull, travel: travel,
                    onBeginOpening: { onBeginOpening(reading) },
                    onOpened: { onOpenReading(reading, nil) },
                    onCommitted: { rate in onFinishOpening(reading, rate) },
                    onAbandoned: onAbandonOpening,
                    onOpenRooms: onOpenRooms)
                .transition(.opacity)
            } else {
                UnlitHearth(paused: room.isPaused)
                    .padding(.top, 18)
                    // Above the empty stone, not on it.
                    .padding(.bottom, 72)
                    .transition(.opacity)
            }
        }
        .allowsHitTesting(isFront)
    }

    private func jarSlot(_ shelf: [Reading], isFront: Bool) -> some View {
        NavigationLink(value: JarRoute(roomID: room.id)) {
            JarView(embers: shelf.map(JarEmber.init), width: 96)
                .flowSource(.jar(room.id), cornerRadius: 18)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .allowsHitTesting(isFront)
        .accessibilityLabel(Copy.theJarSpoken(shelf.map { Bible.book(id: $0.bookID)?.name ?? $0.bookID }))
        .accessibilityHint(Copy.opensTheJar)
        .accessibilityHidden(!isFront)
    }

    /// A sideways drag turns the stone under the finger; let go past half
    /// way, or flicked, and it goes on round to the next thing; short of
    /// that it settles back. Either way round.
    private func turn(things: Int) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                guard things > 1 else { return }
                if !turning {
                    // Sideways, or not at all: up and down are the fire's.
                    guard abs(value.translation.width) > abs(value.translation.height) * 1.3 else { return }
                    turning = true
                }
                dragTurn = -Double(value.translation.width / Self.pointsPerTurn)
            }
            .onEnded { value in
                guard turning else { return }
                turning = false
                // At rest the stone is always a whole turn round.
                let start = turned
                let landed = turned + dragTurn
                let flick = -Double(value.velocity.width) / 900
                // At most one thing on per swipe.
                let step = min(max((landed + flick - start).rounded(), -1), 1)
                turned = landed
                dragTurn = 0
                withAnimation(RibbonMotion.handled(still: reduceMotion)) { turned = start + step }
            }
    }

    private func turn(to index: Int) {
        let things = model.shelf(of: room).isEmpty ? 1 : 2
        let current = turned.rounded()
        let at = ((Int(current) % things) + things) % things
        guard at != index else { return }
        withAnimation(RibbonMotion.handled(still: reduceMotion)) { turned = current + Double(index - at) }
    }
}

/// Where a thing stands on the stone (I47), `position` of the way from
/// one thing to the next: the one in front at the middle; the others a
/// short slide to either side, faded out. As the finger moves, the one in
/// front slides that way and fades, and the next slides in from the other
/// side and comes up — on the stone, never off it. Animatable, so a let-go
/// settles rather than jumps.
private struct OnTheStone: ViewModifier, Animatable {
    var position: Double
    let index: Int
    let things: Int

    /// How far to the side a thing slides on its way out.
    private static let slide: CGFloat = 64

    var animatableData: Double {
        get { position }
        set { position = newValue }
    }

    func body(content: Content) -> some View {
        // Where this one is from the front, the short way round: 0 in front,
        // ±1 a whole step to either side.
        let away = remainder(Double(index) - position, Double(max(things, 1)))
        let distance = min(abs(away), 1)
        return content
            .scaleEffect(1 - 0.06 * distance, anchor: .bottom)
            .offset(x: Self.slide * CGFloat(away))
            .opacity(max(0, 1 - distance * 1.8))
            .zIndex(1 - distance)
            .allowsHitTesting(distance < 0.5)
    }
}

/// The fire, in its well, and the two gestures that live on it.
private struct TheFire: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    let reading: Reading
    @Binding var pull: CGFloat
    let travel: CGFloat
    var onBeginOpening: () -> Void
    /// The tap equivalent (§11): VoiceOver's "Continue in Mark".
    var onOpened: () -> Void
    /// The pull committed, and how fast it was going.
    var onCommitted: (CGFloat) -> Void
    var onAbandoned: (CGFloat) -> Void
    var onOpenRooms: () -> Void

    @State private var sink: CGFloat = 0
    @State private var opening = false
    /// This drag is sideways: the stone's, not the fire's.
    @State private var sideways = false
    /// When the phone was last shaken (I47): the fire flares and sways
    /// from then, and settles.
    @State private var shakenAt: Date?

    var body: some View {
        let state = model.fireState(of: reading)
        let book = Bible.book(id: reading.bookID)
        VStack(spacing: 4) {
            CampfireView(
                state: state,
                scale: reading.handiwork.scale,
                coalDepth: reading.handiwork.coalDepth,
                dimmed: !model.isOnline)
            .scaleEffect(1 + 0.10 * pull)
            .modifier(Shaken(since: shakenAt, still: reduceMotion))
            .frame(maxWidth: .infinity)
            // Its light, its stone and its name are the hearth's (I47).
        }
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .offset(y: sink - pull * travel)
        .contentShape(Rectangle())
        .highPriorityGesture(drag)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(book?.name ?? reading.bookID). \(Copy.fireIs(state.displayName))")
        .accessibilityAction(named: Copy.continueIn(book?.name ?? reading.bookID)) { onOpened() }
        .accessibilityAction(named: Copy.opensYourRooms) { onOpenRooms() }
        .onReceive(NotificationCenter.default.publisher(for: .ribbonShaken)) { _ in
            shakenAt = .now
        }
    }

    /// Up opens the book; down, from the top of the room, opens your rooms.
    /// The fire follows the finger up at full speed and down with a
    /// square-root lean that runs out at 28 points — it is on a hearth, not
    /// a string.
    private var drag: some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .local)
            .onChanged { value in
                // A sideways drag turns the stone (I47); it is not a pull.
                if !opening, sideways || abs(value.translation.width) > abs(value.translation.height) * 1.3 {
                    sideways = true
                    return
                }
                let dy = value.translation.height
                if dy < 0 {
                    if !opening {
                        opening = true
                        model.markFirePulled()
                        onBeginOpening()
                    }
                    sink = 0
                    pull = reduceMotion ? 0 : min(1, -dy / travel)
                } else {
                    pull = 0
                    sink = reduceMotion ? 0 : RoomMetrics.roomSink * (dy / RoomMetrics.roomCommit).squareRoot().clamped(to: 0...1)
                }
            }
            .onEnded { value in
                guard !sideways else {
                    sideways = false
                    return
                }
                let dy = value.translation.height
                // The finger's own speed as it lifted, in points per second
                // — not the distance a prediction adds on, which is a
                // length standing in for a speed and made the flick about
                // twice as hard to land as `openFling` says. As the pull
                // moves: pulls per second, upward positive.
                let speed = value.velocity.height
                let rate = -speed / max(1, travel)
                if dy < 0 {
                    let flung = -speed > RibbonMotion.openFling
                    if pull >= RibbonMotion.openCommit || flung || (reduceMotion && -dy > 40) {
                        // The root carries the pull the rest of the way,
                        // with the speed the finger let go at.
                        onCommitted(rate)
                    } else if opening {
                        // The root takes the fire and the page back down
                        // together, on one spring — the fire animating
                        // itself here as well left the page to be taken out
                        // of the tree before it had arrived.
                        onAbandoned(rate)
                    }
                } else {
                    if dy >= RoomMetrics.roomCommit { onOpenRooms() }
                    // A pull that started and came back down did not
                    // commit: the page the root built goes back with it.
                    if opening { onAbandoned(0) }
                    withAnimation(RibbonMotion.handled(still: reduceMotion)) { sink = 0 }
                }
                opening = false
            }
    }
}

/// A fire shaken (I47: *"shaking the phone should ... effect the fire"*):
/// it flares up, swaying from its foot this way and that, and settles over
/// a second and a half, as a fire stirred does. Nothing under reduce
/// motion.
private struct Shaken: ViewModifier {
    var since: Date?
    var still: Bool

    func body(content: Content) -> some View {
        if let since, !still {
            TimelineView(.animation(minimumInterval: 1 / 60, paused: Date.now.timeIntervalSince(since) > 1.6)) { context in
                let t = context.date.timeIntervalSince(since)
                let fade = t < 1.6 ? exp(-t / 0.45) : 0
                content
                    .rotationEffect(.degrees(9 * fade * sin(t * 2 * .pi / 0.42)), anchor: .bottom)
                    .scaleEffect(x: 1 - 0.04 * fade, y: 1 + 0.22 * fade, anchor: .bottom)
                    .brightness(0.12 * fade)
            }
        } else {
            content
        }
    }
}

/// The fire's light on the room (I47). Not glass and not paper: light, so
/// it is drawn under everything and scrolls with the fire. It breathes on
/// the fire's own period, held still under reduce motion.
private struct HearthLight: View, Animatable {
    /// How brightly the fire lights the room: animatable, so that when the
    /// fire is banked its light dims over a moment, as a fire does, rather
    /// than going down between two frames (I47).
    var strength: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var animatableData: Double {
        get { strength }
        set { strength = newValue }
    }

    init(state: FireState, dimmed: Bool = false) {
        let base: Double = switch state {
        case .catching: 0.7
        case .burning: 1
        case .steady: 1.1
        case .banked: 0.35
        default: 0.8
        }
        strength = dimmed ? base * 0.8 : base
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 20, paused: reduceMotion)) { context in
            let t = context.date.timeIntervalSinceReferenceDate
            let breath = reduceMotion ? 1 : 0.92 + 0.08 * sin(t * 2 * .pi / RibbonMotion.flickerPeriod)
            ZStack {
                // The throw, across the room.
                Ellipse()
                    .fill(RadialGradient(
                        stops: [
                            .init(color: Palette.flameCore.opacity(0.09 * strength), location: 0),
                            .init(color: Palette.flameDeep.opacity(0.04 * strength), location: 0.38),
                            .init(color: Palette.coalDim.opacity(0.02 * strength), location: 0.7),
                            .init(color: .clear, location: 1),
                        ],
                        center: .center, startRadius: 0, endRadius: 460))
                    .frame(width: 900, height: 900)
                    .opacity(breath)
                // The pool on the floor.
                Ellipse()
                    .fill(EllipticalGradient(
                        stops: [
                            .init(color: Palette.flameCore.opacity(0.17 * strength), location: 0),
                            .init(color: Palette.flameDeep.opacity(0.05 * strength), location: 0.55),
                            .init(color: .clear, location: 1),
                        ],
                        center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5))
                    .frame(width: 420, height: 120)
                    .offset(y: 30)
                    .opacity(0.9 + 0.1 * breath)
            }
        }
        .frame(width: 0, height: 0)
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self { min(max(self, range.lowerBound), range.upperBound) }
}

/// The hearth with nothing on it yet: what to do, and what it becomes. A
/// paused room says nothing here — its line is under the way in.
private struct UnlitHearth: View {
    let paused: Bool

    var body: some View {
        VStack(spacing: 10) {
            Air(6)
            if !paused {
                Text(Copy.pickABook)
                    .font(RibbonType.display(24))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
            }
            HairlineRule()
                .scaleEffect(x: 0.62, y: 1)
                .padding(.vertical, 4)
            if !paused {
                Text(Copy.firstFireHint)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
            }
            Air(2)
        }
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - The way in

// MARK: - Good places to start (first run only)

private struct StarterShelf: View {
    var onChoose: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            SectionLabel(Copy.goodPlacesToStart)
                .padding(.horizontal, RoomMetrics.gutter)
            ScrollView(.horizontal) {
                HStack(spacing: 12) {
                    ForEach(Bible.goodPlacesToStart, id: \.self) { id in
                        if let book = Bible.book(id: id) {
                            Button { onChoose(book.id) } label: {
                                VStack(spacing: 8) {
                                    CampfireGlyph(state: .burning, scale: book.scale, height: 30)
                                    Text(book.name)
                                        .font(RibbonType.ui(15))
                                        .foregroundStyle(Palette.text)
                                }
                                .frame(width: 108, height: 96)
                                .contentShape(Rectangle())
                                .tile()
                            }
                            .buttonStyle(.pressable)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Copy.bookIsAFire(book.name, scale: book.scale.rawValue))
                        }
                    }
                }
                .padding(.horizontal, RoomMetrics.gutter)
                .padding(.vertical, 2)
            }
            .scrollIndicators(.hidden)
        }
    }
}

// MARK: - Left for you: rows, never a count, never a badge

private struct WaitingSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    let reading: Reading?
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onSendItAgain: () -> Void
    var onPickAnInk: () -> Void

    var body: some View {
        let waiting = room.isPaused ? [] : Array(model.waitingNotes(in: room).prefix(4))
        let alone = model.members(of: room).count == 1
        // The card the row is about: the one that opened last. The row goes
        // to it, at the foot of its chapter, rather than to your own place.
        let openCard = reading.flatMap { r in
            room.isPaused ? nil : model.state.cards
                .filter { $0.readingID == r.id && $0.state == .open }
                .max { ($0.openedAt ?? .distantPast) < ($1.openedAt ?? .distantPast) }
        }
        let cardsOpen = openCard != nil
        let anInkToPick = !room.isPaused && model.inkIsIdentity(in: room) && model.myMembership(in: room)?.ink == nil
        let hasRows = !waiting.isEmpty || cardsOpen || anInkToPick
        let hasInvite = !room.isPaused && alone && model.hasLiveInvite(room)

        VStack(alignment: .leading, spacing: 0) {
            if hasRows || hasInvite {
                rows(waiting: waiting, openCard: openCard, anInkToPick: anInkToPick, hasRows: hasRows, hasInvite: hasInvite)
                    .padding(.top, 30)
                    .transition(.opacity)
            }
        }
        // Everything that comes and goes here — a note left, the cards
        // opening, an ink to pick, the invite — settles in, and so does the
        // section around the first of them. The cards and the ink used to
        // arrive between two frames, and the section itself always did.
        .animation(RibbonMotion.settle(still: reduceMotion), value: waiting.map(\.id))
        .animation(RibbonMotion.settle(still: reduceMotion), value: cardsOpen)
        .animation(RibbonMotion.settle(still: reduceMotion), value: anInkToPick)
        .animation(RibbonMotion.settle(still: reduceMotion), value: hasInvite)
    }

    @ViewBuilder
    private func rows(waiting: [Note], openCard: ReflectionCard?, anInkToPick: Bool, hasRows: Bool, hasInvite: Bool) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if hasRows {
                SectionLabel(Copy.leftForYou)
                    .padding(.bottom, 2)
                    .transition(.opacity)
            }
            ForEach(waiting) { note in
                if let author = model.person(note.authorID) {
                    WaitingRow(
                        text: note.kind == .voice
                            ? Copy.leftYouAVoiceNote(firstName(author.name), note.verse.formatted)
                            : Copy.leftYouANote(firstName(author.name), note.verse.formatted),
                        action: { if let reading { onOpenReading(reading, .verse(note.verse)) } }
                    ) {
                        NoteMark(
                            kind: note.kind,
                            ink: model.membership(of: note.authorID, in: room.id)?.ink ?? .clay,
                            found: false, mine: false, pending: false)
                    }
                    .transition(.opacity)
                }
            }
            if let openCard, let reading {
                WaitingRow(text: Copy.notifCardsOpen, action: { onOpenReading(reading, .card(chapter: openCard.chapter)) }) {
                    Circle().fill(Palette.chartreuse).frame(width: 6, height: 6)
                }
                .transition(.opacity)
            }
            if anInkToPick {
                WaitingRow(text: Copy.pickAnInk, action: onPickAnInk) {
                    Circle().fill(Palette.text.opacity(0.55)).frame(width: 8, height: 8)
                }
                .transition(.opacity)
            }
            if hasInvite {
                // Room of one, invite still out — the state, not the person.
                HStack(spacing: 6) {
                    SmallCaps(Copy.inviteStillOut, size: 12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    QuietControl(title: Copy.sendItAgain, action: onSendItAgain)
                }
                .padding(.leading, 16)
                .padding(.trailing, 8)
                .padding(.vertical, 8)
                .frame(minHeight: RoomMetrics.touch + 8)
                .paper(.row)
                .padding(.top, hasRows ? 10 : 0)
                .transition(.opacity)
            }
        }
    }
}

private struct WaitingRow<Mark: View>: View {
    var text: String
    var action: () -> Void
    var mark: Mark

    init(text: String, action: @escaping () -> Void, @ViewBuilder mark: () -> Mark) {
        self.text = text
        self.action = action
        self.mark = mark()
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                mark
                Text(text)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(minHeight: RoomMetrics.touch + 8)
            .contentShape(Rectangle())
            .paper(.row)
        }
        .buttonStyle(.pressable)
    }
}

