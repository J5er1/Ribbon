import SwiftUI
import RibbonCore

// The book's ribbon (I47). It is the room's ribbon pulled up out of the
// foot, and it stays: it comes out from under the book's leather head at the
// top of the screen, as it comes out of the foot in the room, and hangs down
// the page in the gap between the notes' marks and the words, its tail on
// the reading line — the line your place is read from, so the ribbon is
// always at where you are. Pull it down, anywhere along it, and the page
// comes down with it, back to the room: the way in, reversed. Tap it, or
// hold the ribbon in the room, and what is behind it opens: where everyone
// is, and every note and highlight the room has left in the book, this
// chapter's first, each one tied to the ribbon by a knot in its ink.

/// The ribbon on the page.
struct PageRibbon: View {
    @Environment(AppModel.self) private var model
    /// Arriving from the foot, drawn by the root until it lands here.
    var hidden: Bool
    var columnWidth: CGFloat
    var margin: CGFloat
    /// A tap on it: what is behind the ribbon.
    var onOpen: () -> Void
    /// Pulled down, and how far past its slack: the page follows it.
    var onPull: (CGFloat) -> Void
    /// The pull's way across, and where it took hold: the page hangs from
    /// it.
    var onSway: (CGFloat, CGPoint) -> Void
    /// Let go, how far down and how fast: home, or back up.
    var onLetGo: (CGFloat, CGFloat) -> Void
    /// The same, for a screen reader: the book closes.
    var onClose: () -> Void

    @State private var pulling = false
    /// The tail dipped, once since the app opened, to show it pulls down
    /// (I47).
    @State private var dip: CGFloat = 0
    @MainActor private static var dipped = false
    /// Each time the ribbon comes to the page, counted: it swings to rest.
    @State private var landings = 0
    /// The ribbon taken hold of (I47): how far the finger has moved it,
    /// across and down, and how far down the screen it took hold. It
    /// follows the finger a little before the page does — *"make it so you
    /// can drag the ribbon a little and move it before pulling it"*.
    @State private var tug = CGSize.zero
    @State private var heldAt: CGFloat = 0

    /// How far down the ribbon gives before the page comes with it.
    static let slack: CGFloat = 34
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The room's ribbon, near enough: as wide as the gap between the
    /// notes' marks and the words allows (the owner found the first one, at
    /// 12, did not match the room's).
    static let width: CGFloat = 16

    /// Where the ribbon hangs on a screen this size: its centre across, in
    /// the page's gutter just before the words; its top, out from under the
    /// leather head; and its tail, at the reading line (the upper third,
    /// just above, as a landed verse rests). The root's flight lands on the
    /// same place.
    static func place(screen: CGSize, safeTop: CGFloat, columnWidth: CGFloat, margin: CGFloat) -> (x: CGFloat, top: CGFloat, tail: CGFloat) {
        let width = columnWidth > 0 ? columnWidth : min(screen.width, 680)
        let column = (screen.width - width) / 2
        return (column + margin + 25, LeatherHead.height(safeTop: safeTop) - 2, safeTop + screen.height * 0.3 - 6)
    }

    /// The window the page lies in: its size, and how far down the clock
    /// leaves its safe area. Read from the window, not the page: a page's
    /// safe area changes as it moves, and the head and the ribbon changed
    /// with it — shrinking as the page came up past its place, and jumping
    /// back as it came to rest, a flicker.
    @MainActor static func window() -> (size: CGSize, safeTop: CGFloat)? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        guard let window else { return nil }
        return (window.bounds.size, window.safeAreaInsets.top)
    }

    var body: some View {
        // The page's own frame begins under the clock; the head and the
        // ribbon are drawn from the top of the screen, above it, in the
        // screen's measure.
        GeometryReader { proxy in
            let window = Self.window()
            let safeTop = window?.safeTop ?? proxy.safeAreaInsets.top
            let screen = window?.size ?? CGSize(width: proxy.size.width, height: proxy.size.height + safeTop)
            let place = Self.place(
                screen: screen, safeTop: safeTop,
                columnWidth: columnWidth, margin: margin)
            // Held, it swings from the head toward the finger, and gives a
            // little to a pull before it is taut.
            let reach = max(60, heldAt - place.top)
            let toward = reduceMotion ? 0 : -atan2(tug.width, reach) * 180 / .pi
            let give = reduceMotion ? 0 : min(max(tug.height, 0), Self.slack) * 0.25
            ZStack(alignment: .topLeading) {
                SatinRibbon()
                    .frame(width: Self.width, height: place.tail + dip + give - place.top)
                    .rotationEffect(.degrees(min(14, max(-14, toward))), anchor: .top)
                    // Landed, it swings from the head and comes to rest,
                    // the way a ribbon let go of does.
                    .ribbonSwing(landings, amplitude: 2.8)
                    .offset(x: place.x - Self.width / 2, y: place.top)
                    .opacity(hidden ? 0 : 1)
                    .allowsHitTesting(false)
                // The head it comes out from under.
                LeatherHead()
                    .frame(width: screen.width, height: LeatherHead.height(safeTop: safeTop))
                // All of it is the handle, not just its tail: a band down
                // its length, clear of the notes' marks to one side and no
                // more than the first of the words on the other, and a wider
                // one at its tail, where a thumb reaches.
                if !hidden {
                    Color.clear
                        .contentShape(RibbonHold(x: place.x, top: place.top, tail: place.tail))
                        .onTapGesture { onOpen() }
                        // A pan of UIKit's own, as the Wave's sweep is: the
                        // page's scrolling waits for it, so a pull down the
                        // ribbon is never taken for a scroll.
                        .gesture(HomeSweep(
                            downward: true,
                            takesSideways: true,
                            onChanged: { down in
                                pulling = true
                                tug.height = down
                                onPull(max(0, down - Self.slack))
                            },
                            onEnded: { down, speed in
                                pulling = false
                                onLetGo(max(0, down - Self.slack), speed)
                                // Let go of, it swings back to where it
                                // hangs, through it, and back.
                                withAnimation(RibbonMotion.letGo) { tug = .zero }
                            },
                            onAcross: { across, at in
                                tug.width = across
                                heldAt = at.y
                                onSway(across, at)
                            }))
                        .accessibilityHidden(true)
                    // For a screen reader, the ribbon is its tail.
                    Color.clear
                        .frame(width: 44, height: 64)
                        .offset(x: place.x - 22, y: place.tail - 44)
                        .allowsHitTesting(false)
                        .accessibilityElement()
                        .accessibilityLabel(Copy.theRibbonSpoken)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction { onOpen() }
                        .accessibilityAction(named: Copy.closeTheBook) { onClose() }
                }
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(y: -safeTop)
        }
        .onAppear { if !hidden { landings += 1 } }
        .onChange(of: hidden) { _, now in if !now { landings += 1 } }
        .task(id: hidden) {
            guard !hidden, !Self.dipped, !reduceMotion else { return }
            try? await Task.sleep(for: .seconds(1.4))
            guard !Task.isCancelled, !pulling else { return }
            Self.dipped = true
            withAnimation(.easeInOut(duration: 0.35)) { dip = 16 }
            try? await Task.sleep(for: .seconds(0.38))
            withAnimation(RibbonMotion.handled) { dip = 0 }
        }
    }
}

/// Where the ribbon can be taken hold of: down its length, a little wider
/// than it is, and wider again at its tail.
private struct RibbonHold: Shape {
    var x: CGFloat
    var top: CGFloat
    var tail: CGFloat

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(CGRect(
            x: x - PageRibbon.width / 2 - 2, y: top,
            width: PageRibbon.width + 4, height: max(0, tail - top)))
        path.addRect(CGRect(x: x - 22, y: tail - 44, width: 44, height: 64))
        return path
    }
}

/// What is behind the ribbon: where everyone is, and what the room has left.
struct RibbonListSheet: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let reading: Reading
    /// The chapter you are in: its marks come first.
    let chapter: Int
    let translation: TranslationID
    var onGo: (VerseAddress) -> Void

    /// The ribbon down the list's side, and where its knots are tied.
    private static let spine: CGFloat = 30
    private static let indent: CGFloat = 58

    private var book: BibleBook? { Bible.book(id: reading.bookID) }

    var body: some View {
        let marks = marks()
        let here = marks.filter { $0.address.chapter == chapter }
        let elsewhere = marks.filter { $0.address.chapter != chapter }
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(book?.chapterHeading(chapter) ?? reading.bookID)
                        .font(RibbonType.display(28))
                        .foregroundStyle(Palette.text)
                        .accessibilityAddTraits(.isHeader)
                    Text(Copy.ribbonListLede)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                }
                .padding(.top, 30)
                .padding(.bottom, 26)

                label(Copy.whereEveryoneIs)
                people
                if let ribbon = model.ribbon(in: reading) { ribbonRow(ribbon) }

                label(Copy.inThis(book?.chapterHeading(chapter) ?? ""))
                    .padding(.top, 30)
                if here.isEmpty {
                    Text(Copy.nothingLeftInThisChapterYet)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                        .padding(.vertical, 10)
                } else {
                    ForEach(here) { markRow($0) }
                }

                if !elsewhere.isEmpty {
                    label(Copy.elsewhereIn(book?.name ?? ""))
                        .padding(.top, 30)
                    ForEach(elsewhere) { markRow($0) }
                }
            }
            .padding(.leading, Self.indent)
            .padding(.trailing, 24)
            .padding(.bottom, 48)
            // The ribbon the list hangs from, down its whole length.
            .background(alignment: .topLeading) {
                GeometryReader { proxy in
                    SatinRibbon()
                        .frame(width: 10, height: proxy.size.height)
                        .offset(x: Self.spine - 5)
                }
            }
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .background { Palette.ground.ignoresSafeArea() }
        .presentationBackground(Palette.ground)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func label(_ text: String) -> some View {
        SmallCaps(text, size: 12)
            .padding(.bottom, 6)
            .accessibilityAddTraits(.isHeader)
    }

    // MARK: Where everyone is

    private var people: some View {
        let present = Dictionary(
            room.isPaused ? [] : model.presentPeople.map { ($0.id, $0) },
            uniquingKeysWith: { first, _ in first })
        return ForEach(model.members(of: room)) { membership in
            let id = membership.personID
            let person = model.person(id)
            let live = present[id]
            let place = live?.position ?? position(of: id)
            let mine = id == model.me?.id
            Button {
                if let place { onGo(place) }
            } label: {
                HStack(spacing: 12) {
                    PortraitView(person: person, ink: membership.ink, size: 30, image: model.portrait(id))
                        .background(
                            Circle()
                                .stroke(Palette.chartreuse, lineWidth: 1.5)
                                .padding(-3)
                                .opacity(live != nil && live?.isIdle == false ? 1 : 0))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(mine ? Copy.you : person.map { firstName($0.name) } ?? "")
                            .font(RibbonType.ui(16))
                            .foregroundStyle(Palette.text)
                        SmallCaps(placeLine(place, reading: live.map { !$0.isIdle } ?? false), size: 11)
                    }
                    Spacer(minLength: 8)
                    if place != nil { chevron }
                }
                .frame(minHeight: 52)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .disabled(place == nil)
            .overlay(alignment: .leading) { knot(membership.ink?.color ?? Palette.muted) }
        }
    }

    private func ribbonRow(_ ribbon: Ribbon) -> some View {
        let mine = ribbon.personID == model.me?.id
        let by = mine ? nil : model.person(ribbon.personID).map { firstName($0.name) }
        let address = VerseAddress(bookID: reading.bookID, chapter: ribbon.chapter, verse: ribbon.verse)
        return Button { onGo(address) } label: {
            HStack(spacing: 12) {
                SatinRibbon()
                    .frame(width: 8, height: 22)
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text(Copy.theRibbon)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                    SmallCaps("\(reference(address)) · \(Copy.whereItWasLeft(by: by))", size: 11)
                }
                Spacer(minLength: 8)
                chevron
            }
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .overlay(alignment: .leading) { knot(Palette.chartreuse) }
    }

    private func position(of personID: UUID) -> VerseAddress? {
        model.state.positions
            .first { $0.readingID == reading.id && $0.personID == personID }
            .map { VerseAddress(bookID: reading.bookID, chapter: $0.chapter, verse: $0.verse) }
    }

    private func placeLine(_ place: VerseAddress?, reading now: Bool) -> String {
        guard let place else { return Copy.notStartedYet }
        return now ? "\(Copy.readingNow) · \(reference(place))" : reference(place)
    }

    private func reference(_ address: VerseAddress) -> String {
        "\(book?.chapterHeading(address.chapter) ?? ""):\(address.verse)"
    }

    // MARK: What the room has left

    private struct Mark: Identifiable {
        enum Kind {
            case note(Note)
            case highlight(Highlight)
        }
        var id: UUID
        var address: VerseAddress
        var kind: Kind
    }

    /// Every note and highlight in the book, in the book's order.
    private func marks() -> [Mark] {
        let notes = model.notes(in: reading).map { Mark(id: $0.id, address: $0.verse, kind: .note($0)) }
        let highlights = model.highlights(in: reading).map {
            Mark(id: $0.id, address: VerseAddress(bookID: reading.bookID, chapter: $0.range.chapter, verse: $0.range.startVerse), kind: .highlight($0))
        }
        return (notes + highlights).sorted { $0.address < $1.address }
    }

    private func ink(of personID: UUID, or fallback: Ink? = nil) -> Color {
        (model.membership(of: personID, in: room.id)?.ink ?? fallback)?.color ?? Palette.muted
    }

    private func name(of personID: UUID) -> String {
        personID == model.me?.id ? Copy.you : model.person(personID).map { firstName($0.name) } ?? ""
    }

    @ViewBuilder
    private func markRow(_ mark: Mark) -> some View {
        let verse = "\(mark.address.chapter):\(mark.address.verse)"
        switch mark.kind {
        case .note(let note):
            let me = model.me?.id
            let mine = note.authorID == me
            // A note left for you and not yet found keeps its words for the
            // verse to give you (§6.3): the list says it is there, and no
            // more.
            let found = mine || note.foundBy.contains(me ?? UUID())
            let words = note.kind == .written ? note.body : note.transcript
            Button { onGo(mark.address) } label: {
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    SmallCaps(verse, size: 12, color: Palette.chartreuse.opacity(0.85))
                        .frame(width: 40, alignment: .leading)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(Copy.noteBy(name(of: note.authorID), voice: note.kind == .voice))
                            .font(RibbonType.ui(15))
                            .foregroundStyle(Palette.text)
                        if found, let words, !words.isEmpty {
                            Text(words)
                                .font(RibbonType.scriptureItalic(15))
                                .foregroundStyle(Palette.muted)
                                .lineLimit(2)
                        } else if !found {
                            SmallCaps(Copy.waitingAtTheVerse, size: 11, color: ink(of: note.authorID))
                        }
                    }
                    Spacer(minLength: 8)
                    chevron
                }
                .padding(.vertical, 10)
                .frame(minHeight: 52)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .overlay(alignment: .leading) { knot(ink(of: note.authorID)) }
        case .highlight(let highlight):
            let words = model.scripture.verseText(mark.address, translation: translation)
            let color = highlight.ink.color
            Button { onGo(mark.address) } label: {
                HStack(alignment: .firstTextBaseline, spacing: 12) {
                    SmallCaps(verse, size: 12, color: Palette.chartreuse.opacity(0.85))
                        .frame(width: 40, alignment: .leading)
                    VStack(alignment: .leading, spacing: 3) {
                        if let words {
                            Text(words)
                                .font(RibbonType.scripture(15))
                                .foregroundStyle(Palette.text.opacity(0.9))
                                .lineLimit(2)
                                .padding(.horizontal, 4)
                                .background(
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(color.opacity(0.18)))
                                .padding(.horizontal, -4)
                        }
                        SmallCaps(Copy.highlightedBy(name(of: highlight.authorID)), size: 11)
                    }
                    Spacer(minLength: 8)
                    chevron
                }
                .padding(.vertical, 10)
                .frame(minHeight: 52)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .overlay(alignment: .leading) { knot(color) }
        }
    }

    // MARK: The ribbon's knots

    /// Each row tied to the ribbon: a bead in its ink, on the ribbon.
    private func knot(_ color: Color) -> some View {
        Circle()
            .fill(color)
            .overlay(Circle().stroke(Color.black.opacity(0.55), lineWidth: 1.5))
            .frame(width: 9, height: 9)
            .offset(x: Self.spine - Self.indent - 4.5)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    private var chevron: some View {
        Image(systemName: "chevron.right")
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(Palette.muted.opacity(0.7))
    }
}
