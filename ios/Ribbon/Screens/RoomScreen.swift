import SwiftUI
import RibbonCore

// S01 — the room. Where the app opens, and the only permanent destination.
// Its job is to show the fire, say who's here, and get you into the book in
// one pull (ledger A48). The only chrome is the room's name, top-left, in
// small caps, and your own face, top-right.
//
// The hearth. The fire, the seats around it and the way in are one object
// on paper — a hearth — and the fire sits in a well in it. Pulling the fire
// up opens the book: the fire swells and rises under the finger, the hearth
// recedes, and past a fifth of the travel (or on a flick) the page comes up
// from the foot of the screen. Pulling it down at the top of the room opens
// your rooms. Both have a tap: the way-in capsule and the room's name (§11).

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

    @State private var showChooser = false
    @State private var showInviteShare = false
    @State private var showInkPicker = false

    private var reading: Reading? { model.openReading(in: room) }
    private var shelf: [Reading] { model.shelf(of: room) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                RoomHeader(room: room, onOpenRooms: onOpenRooms, onYou: onYou)

                Greeting(room: room)
                    .padding(.horizontal, RoomMetrics.gutter)
                    .padding(.top, 16)

                HearthView(
                    room: room,
                    reading: reading,
                    pull: $bookPull,
                    onOpenReading: onOpenReading,
                    onBeginOpening: onBeginOpening,
                    onFinishOpening: onFinishOpening,
                    onAbandonOpening: onAbandonOpening,
                    onPickABook: { showChooser = true },
                    onInvite: { showInviteShare = true },
                    onOpenRooms: onOpenRooms)
                .padding(.horizontal, RoomMetrics.gutter)
                .padding(.vertical, 22)

                if reading == nil && shelf.isEmpty && !room.isPaused {
                    StarterShelf { bookID in
                        let started = model.startReading(bookID: bookID, in: room)
                        onOpenReading(started, nil)
                    }
                    .padding(.top, 6)
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

                if !shelf.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel(Copy.theShelf)
                            .padding(.horizontal, RoomMetrics.gutter)
                        ShelfView(
                            room: room, readings: shelf,
                            onStartAnother: { showChooser = true },
                            onOpenNote: { reading, verse in onOpenReading(reading, .verse(verse)) })
                    }
                    .padding(.top, 40)
                    .transition(.opacity)
                }

                if !room.isPaused {
                    QuietDayFoot(room: room)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 52)
                        .padding(.bottom, 40)
                } else {
                    Air(40)
                }
            }
            .readableColumn()
            .animation(RibbonMotion.settle(still: reduceMotion), value: shelf.count)
            .animation(RibbonMotion.settle(still: reduceMotion), value: reading?.id)
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
            BookChooserSheet(room: room) { bookID in
                showChooser = false
                let started = model.startReading(bookID: bookID, in: room)
                onOpenReading(started, nil)
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
    static let headerPortrait: CGFloat = 28
    static let touch: CGFloat = 44
    static let seat: CGFloat = 38
    static let seatTouch: CGFloat = 48
    static let seatMinTouch: CGFloat = 44
    /// Faces 10 points apart, measured face to face — the touch targets
    /// overlap and that is fine, their centres are what a finger aims at.
    static let seatGap: CGFloat = 10 - (seatTouch - seat)
    static let ring: CGFloat = 1.5
    static let ringGap: CGFloat = 2
    static let hearthPadding: CGFloat = 22
    /// How far the hearth follows the fire up, as a fraction of the pull.
    static let hearthFollow: CGFloat = 0.35
    /// How far the fire can be pulled down before it stops, and the
    /// distance that opens the rooms.
    static let roomSink: CGFloat = 28
    static let roomCommit: CGFloat = 96
}

// MARK: - Header: the entire navigation bar

private struct RoomHeader: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onOpenRooms: () -> Void
    var onYou: () -> Void

    var body: some View {
        HStack {
            Button(action: onOpenRooms) {
                SmallCaps(model.displayName(of: room), size: 14)
                    .padding(.horizontal, 6)
                    .frame(minHeight: RoomMetrics.touch)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityHint(Copy.opensYourRooms)
            Spacer()
            // Your own portrait, top-right — settings one tap away, from
            // anywhere the room is (deviations 14).
            Button(action: onYou) {
                PortraitView(
                    person: model.me, ink: nil, size: RoomMetrics.headerPortrait,
                    image: model.me.flatMap { model.portrait($0.id) })
                .accessibilityHidden(true)
                .frame(width: RoomMetrics.touch, height: RoomMetrics.touch, alignment: .trailing)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(Copy.you)
            .accessibilityHint(Copy.opensYourAccount)
        }
        .padding(.horizontal, RoomMetrics.gutter)
        .padding(.top, 4)
    }
}

// MARK: - The greeting and who is here

/// "Good evening, Ruth." — by the hour on this phone, and under it, who is
/// here as a sentence. Never a count, never a list of pills.
private struct Greeting: View {
    @Environment(AppModel.self) private var model
    let room: Room

    var body: some View {
        TimelineView(.periodic(from: .now, by: 60)) { context in
            let hour = Calendar.current.component(.hour, from: context.date)
            VStack(alignment: .leading, spacing: 6) {
                Text(Copy.greeting(model.me?.name, hour: hour))
                    .font(RibbonType.display(30))
                    .foregroundStyle(Palette.text)
                    .accessibilityAddTraits(.isHeader)
                    .contentTransition(.opacity)
                    .animation(RibbonMotion.settle, value: hour)
                PresenceLine(room: room)
            }
        }
    }
}

private struct PresenceLine: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    /// The last sentence, held so the line can fade out reading it.
    @State private var held: (line: String, personID: UUID)?

    private var sentence: (line: String, personID: UUID, live: Bool)? {
        let present = room.isPaused ? [] : model.presentPeople
        if let first = present.first {
            let base = first.isIdle
                ? Copy.personIsHereButStill(firstName(first.name))
                : Copy.personIsReading(firstName(first.name))
            let others = present.dropFirst().map { firstName($0.name) }
            return (others.isEmpty ? base : Copy.alsoHere(base, Array(others)), first.id, true)
        }
        if !room.isPaused, let last = model.lastReader(in: room) {
            return (last.line, last.personID, false)
        }
        return nil
    }

    var body: some View {
        let now = sentence
        Group {
            if let shown = now.map({ (line: $0.line, personID: $0.personID) }) ?? held {
                NavigationLink(value: PersonRoute(personID: shown.personID, roomID: room.id)) {
                    Text(shown.line)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(now?.live == true ? Palette.text : Palette.muted)
                        .padding(.vertical, 4)
                        .frame(minHeight: RoomMetrics.touch, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                .opacity(now == nil ? 0 : 1)
                .frame(height: now == nil ? 0 : nil)
                .clipped()
            }
            // Alone: silence. Never a line about being alone (§08).
        }
        .animation(RibbonMotion.settle(still: reduceMotion), value: now?.line)
        .onChange(of: now?.line, initial: true) { _, _ in
            if let now { held = (now.line, now.personID) }
        }
    }
}

// MARK: - The hearth

private struct HearthView: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let reading: Reading?
    /// 0 where the fire sits, 1 at the end of its travel up.
    @Binding var pull: CGFloat
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onBeginOpening: (Reading) -> Void
    var onFinishOpening: (Reading, CGFloat) -> Void
    var onAbandonOpening: (CGFloat) -> Void
    var onPickABook: () -> Void
    var onInvite: () -> Void
    var onOpenRooms: () -> Void

    @State private var travel: CGFloat = 320

    var body: some View {
        VStack(spacing: 0) {
            Seats(room: room, onInvite: onInvite)
            Air(16)
            ZStack {
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
                        .padding(.vertical, 18)
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: .infinity)
            .well(.card)
            // A fire catching where the hearth was unlit is a change of
            // light: it cross-fades under reduce motion too.
            .animation(RibbonMotion.arrive, value: reading?.id)
            Air(18)
            WayIn(
                room: room, reading: reading,
                onOpenReading: onOpenReading, onPickABook: onPickABook)
        }
        .padding(RoomMetrics.hearthPadding)
        .paper(.group)
        .offset(y: -pull * travel * RoomMetrics.hearthFollow)
        .opacity(Double(max(CGFloat(0), min(CGFloat(1), 1 - pull * 1.25))))
        .background {
            GeometryReader { proxy in
                Color.clear.onAppear { travel = max(200, proxy.size.height * RibbonMotion.openTravel) }
                    .onChange(of: proxy.size.height) { _, height in travel = max(200, height * RibbonMotion.openTravel) }
            }
        }
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

    var body: some View {
        let state = model.fireState(of: reading)
        let book = Bible.book(id: reading.bookID)
        VStack(spacing: 10) {
            CampfireView(
                state: state,
                scale: reading.handiwork.scale,
                coalDepth: reading.handiwork.coalDepth,
                dimmed: !model.isOnline)
            .scaleEffect(1 + 0.10 * pull)
            .frame(maxWidth: .infinity)
            HairlineRule()
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 0)
                .scaleEffect(x: 0.62, y: 1)
                .padding(.bottom, 4)
            Text(book?.name ?? reading.bookID)
                .font(RibbonType.display(30))
                .foregroundStyle(Palette.text)
            SmallCaps(state.displayName, size: 13)
                .contentTransition(.opacity)
                .animation(RibbonMotion.settle, value: state.displayName)
        }
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity)
        .offset(y: sink - pull * travel)
        .contentShape(Rectangle())
        .highPriorityGesture(drag)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(book?.name ?? reading.bookID). \(Copy.fireIs(state.displayName))")
        .accessibilityAction(named: Copy.continueIn(book?.name ?? reading.bookID)) { onOpened() }
        .accessibilityAction(named: Copy.opensYourRooms) { onOpenRooms() }
    }

    /// Up opens the book; down, from the top of the room, opens your rooms.
    /// The fire follows the finger up at full speed and down with a
    /// square-root lean that runs out at 28 points — it is on a hearth, not
    /// a string.
    private var drag: some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .local)
            .onChanged { value in
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

// MARK: - Seats

/// Everybody in the room, as faces around the fire — present ones ringed
/// in the accent, idle ones with a half ring — and an open seat when
/// somebody is expected. A face goes to its person (S12).
private struct Seats: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    var onInvite: () -> Void

    var body: some View {
        let members = model.members(of: room)
        let present = room.isPaused ? Set<UUID>() : Set(model.presentPeople.map(\.id))
        let idle = room.isPaused ? Set<UUID>() : Set(model.presentPeople.filter(\.isIdle).map(\.id))
        let seatKept = model.somebodyIsExpected(room)
        let places = min(members.count, Room.capacity) + (seatKept ? 1 : 0)
        // A face taking its seat comes in from a little smaller; under
        // reduce motion it only fades in (§11).
        let seating = reduceMotion
            ? AnyTransition.opacity
            : AnyTransition.opacity.combined(with: .scale(scale: 0.82))

        GeometryReader { proxy in
            let touch: CGFloat = places <= 1
                ? RoomMetrics.seatTouch
                : ((proxy.size.width - RoomMetrics.seatGap * CGFloat(places - 1)) / CGFloat(places))
                    .clamped(to: RoomMetrics.seatMinTouch...RoomMetrics.seatTouch)
            let face = RoomMetrics.seat * (touch / RoomMetrics.seatTouch)
            HStack(spacing: RoomMetrics.seatGap) {
                ForEach(members.prefix(Room.capacity)) { membership in
                    Seat(
                        room: room, personID: membership.personID,
                        present: present.contains(membership.personID),
                        idle: idle.contains(membership.personID),
                        touch: touch, face: face)
                    .transition(seating)
                }
                if seatKept {
                    OpenSeat(touch: touch, face: face, onInvite: onInvite)
                        .transition(seating)
                }
            }
            .frame(maxWidth: .infinity)
            .animation(RibbonMotion.arrive, value: members.map(\.personID))
            .animation(RibbonMotion.arrive, value: seatKept)
        }
        .frame(height: RoomMetrics.seatTouch)
    }
}

private struct Seat: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let personID: UUID
    let present: Bool
    let idle: Bool
    let touch: CGFloat
    let face: CGFloat

    var body: some View {
        let person = model.person(personID)
        let spoken: String? = person.map { name in
            if present && idle { return Copy.personIsHereButStill(firstName(name.name)) }
            if present { return Copy.personIsReading(firstName(name.name)) }
            return name.name
        }
        NavigationLink(value: PersonRoute(personID: personID, roomID: room.id)) {
            ZStack {
                PresenceRing(shown: present, half: idle)
                    .frame(width: face + (RoomMetrics.ring + RoomMetrics.ringGap) * 2,
                           height: face + (RoomMetrics.ring + RoomMetrics.ringGap) * 2)
                    // Presence appearing is the `arrive` curve (§9.1), and a
                    // change of light, so it fades under reduce motion too.
                    .animation(RibbonMotion.arrive, value: present)
                    .animation(RibbonMotion.settle, value: idle)
                PortraitView(
                    person: person,
                    ink: model.membership(of: personID, in: room.id)?.ink,
                    size: face,
                    image: model.portrait(personID))
                .accessibilityHidden(true)
            }
            .frame(width: touch, height: touch)
            .contentShape(Circle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(spoken ?? "")
    }
}

/// The accent ring around a present face: a full circle for someone
/// reading, the top half for someone here but still.
private struct PresenceRing: View {
    var shown: Bool
    var half: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if reduceMotion {
                // Held still, the whole ring and the half cross-fade.
                ring(from: 0).opacity(half ? 0 : 1)
                ring(from: 0.5).opacity(half ? 1 : 0)
            } else {
                // Going still unwinds the ring to its crown, and reading
                // again draws it back round — it was a jump between the two.
                ring(from: half ? 0.5 : 0)
            }
        }
        .opacity(shown ? 1 : 0)
        .accessibilityHidden(true)
    }

    // A path starts at three o'clock and runs clockwise, so the second half
    // is the top: nine o'clock over the crown to three.
    private func ring(from start: CGFloat) -> some View {
        Circle()
            .trim(from: start, to: 1)
            .stroke(Palette.chartreuse, lineWidth: RoomMetrics.ring)
    }
}

/// A dashed seat: somebody is expected. Tapping it invites them.
private struct OpenSeat: View {
    let touch: CGFloat
    let face: CGFloat
    var onInvite: () -> Void

    var body: some View {
        Button(action: onInvite) {
            Circle()
                .stroke(Palette.muted.opacity(0.5), style: StrokeStyle(lineWidth: 1.4, dash: [4, 4]))
                .frame(width: face, height: face)
                .frame(width: touch, height: touch)
                .contentShape(Circle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(Copy.anOpenSeat)
    }
}

// MARK: - The way in

private struct WayIn: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let reading: Reading?
    var onOpenReading: (Reading, ReadingPlace?) -> Void
    var onPickABook: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            if let reading, let book = Bible.book(id: reading.bookID) {
                let hasRead = model.state.positions.contains {
                    $0.readingID == reading.id && $0.personID == model.me?.id
                }
                WayInButton(title: hasRead ? Copy.continueIn(book.name) : Copy.begin(book.name)) {
                    onOpenReading(reading, nil)
                }
                if let ribbon = model.ribbonWorthOffering(in: reading) {
                    RibbonOffer(reading: reading, ribbon: ribbon, book: book) { address in
                        onOpenReading(reading, .verse(address))
                    }
                }
                if !model.state.hasPulledTheFire {
                    SmallCaps(Copy.pullTheFireUp, size: 11)
                }
            } else if !room.isPaused {
                WayInButton(title: Copy.pickABookControl, action: onPickABook)
            }
            if room.isPaused {
                // Scripture is never locked (§2.5): a paused room keeps its
                // way in — the pause line is added, the waiting rows go.
                Text(Copy.roomPaused)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// The ribbon, offered: one quiet sentence that goes there when tapped.
private struct RibbonOffer: View {
    @Environment(AppModel.self) private var model
    let reading: Reading
    let ribbon: Ribbon
    let book: BibleBook
    var onGo: (VerseAddress) -> Void

    var body: some View {
        let mine = model.me?.id == ribbon.personID
        let reference = "\(book.chapterHeading(ribbon.chapter)):\(ribbon.verse)"
        let line = mine
            ? Copy.youLeftTheRibbonAt(reference)
            : Copy.ribbonIsAt(model.person(ribbon.personID).map { firstName($0.name) }, reference)
        Button {
            onGo(VerseAddress(bookID: reading.bookID, chapter: ribbon.chapter, verse: ribbon.verse))
        } label: {
            Text(line)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .frame(minHeight: RoomMetrics.seatMinTouch)
                .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityHint(Copy.goThere)
    }
}

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

// MARK: - Mark a quiet day

/// Always present, never emphasised. The room sees who banked the fire
/// (§4.7): an act of care, performed in public, above the control rather
/// than in place of it.
private struct QuietDayFoot: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    @State private var lastBanked: String?

    var body: some View {
        let bankedBy = model.activeQuietDay(in: room).flatMap { model.person($0.personID)?.name }
        VStack(spacing: 12) {
            if let name = bankedBy ?? lastBanked {
                SmallCaps(Copy.bankedTheFire(firstName(name)), size: 12)
                    .opacity(bankedBy == nil ? 0 : 1)
                    .frame(height: bankedBy == nil ? 0 : nil)
                    .clipped()
            }
            QuietControl(title: Copy.markAQuietDay) {
                model.markQuietDay(in: room)
            }
        }
        .animation(RibbonMotion.settle(still: reduceMotion), value: bankedBy)
        .onChange(of: bankedBy, initial: true) { _, name in
            if let name { lastBanked = name }
        }
    }
}
