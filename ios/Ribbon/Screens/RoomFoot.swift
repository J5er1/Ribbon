import SwiftUI
import RibbonCore

// The room's foot (I47): the way in, your rooms and you, all in the thumb's
// reach — the room's name and your face used to sit in the top corners,
// where a hand holding the phone does not go.
//
// The way in is the book's own ribbon, hanging from its title. Pull it up
// and the book opens where it lies; the ribbon goes up with the page (the
// root draws that flight, `RibbonFlight`) and stays on it. A tap does the
// same. Beside it, the room's people in a pill that switches rooms, and
// your face with the gear that is your settings.
//
// It is the foot of the book itself (I47, after the owner's *"take
// inspiration ... from 2010s apps with the skew morphism but blend it with
// what we have"*): a leather cover, stitched, its pages' gilt edge along
// the top, and the ribbon coming out over the gilt. Held — anywhere on it
// but your rooms and your face, after a tester reached for that first — a
// drawer rises out of it: a quiet day, another book, and what is behind the
// ribbon. Swiped down it rises too (up is the ribbon's, into the book), and
// the tab at its top edge says so, and takes a tap.
struct RoomFoot: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    let reading: Reading?
    /// The ribbon is up in the page, drawn by the root: its place here is
    /// empty until the book comes down.
    var ribbonAway = false
    /// The ribbon pulled, or the title tapped: the book, and where on the
    /// screen its ribbon was, for the flight up into the page.
    /// The drawer is up.
    @Binding var drawerOpen: Bool
    var onOpen: (Reading, CGRect) -> Void
    /// What is behind the ribbon (I47), from the drawer.
    var onHold: (Reading) -> Void
    /// "Start another book", from the drawer.
    var onStartAnother: () -> Void
    var onPickABook: () -> Void
    var onOpenRooms: () -> Void
    var onYou: () -> Void

    @State private var ribbonFrame: CGRect = .zero
    /// The tab dipped, once since the app opened, to show the cover swipes
    /// down for its drawer (I47).
    @State private var tabDip: CGFloat = 0
    @MainActor private static var dipped = false
    /// The ribbon is out of its slot, hanging in the foot.
    @State private var ribbonOut = true
    /// Each time it drops back out of its slot, counted: it swings to rest.
    @State private var ribbonDrops = 0

    var body: some View {
        VStack(spacing: 0) {
            tab
            if drawerOpen {
                drawer
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            HStack(alignment: .top, spacing: 0) {
                wayIn
                Spacer(minLength: 8)
                people
                    .padding(.top, 4)
            }
            // In from the cover's rounded corner, so the ribbon comes out
            // over the straight of its edge.
            .padding(.leading, 32)
            .padding(.trailing, 18)
            .frame(height: 66, alignment: .top)
            // Held on the bare leather, between the things on it: the
            // drawer. The ribbon's own press says the same when held.
            .background {
                Color.clear
                    .contentShape(Rectangle())
                    .onLongPressGesture(minimumDuration: Self.hold) { heldForTheDrawer() }
            }
        }
        .background(alignment: .top) { plate }
        // Down on the cover, anywhere: the drawer, or the drawer away.
        .simultaneousGesture(
            DragGesture(minimumDistance: 16)
                .onEnded { value in
                    let down = value.translation.height
                    guard abs(down) > abs(value.translation.width) else { return }
                    if down > 28 || value.velocity.height > 400 {
                        drawerOpen.toggle()
                    } else if drawerOpen, down < -28 || value.velocity.height < -400 {
                        drawerOpen = false
                    }
                })
        .animation(reduceMotion ? RibbonMotion.arrive : RibbonMotion.handled, value: drawerOpen)
        .task {
            guard !Self.dipped, !reduceMotion else { return }
            try? await Task.sleep(for: .seconds(3.8))
            guard !Task.isCancelled, !drawerOpen else { return }
            Self.dipped = true
            for _ in 0..<2 {
                withAnimation(.easeInOut(duration: 0.3)) { tabDip = 5 }
                try? await Task.sleep(for: .seconds(0.32))
                withAnimation(.easeOut(duration: 0.3)) { tabDip = 0 }
                try? await Task.sleep(for: .seconds(0.36))
            }
        }
    }

    // MARK: The drawer

    /// The tab at the top of the cover: tap it, or pull it up, for the
    /// drawer; pull it down, or tap it again, to put the drawer away.
    private var tab: some View {
        Capsule()
            .fill(Color.black.opacity(0.45))
            .overlay(alignment: .bottom) {
                Capsule().fill(Color(hex: "C9A86A").opacity(0.18)).frame(height: 1).offset(y: 1)
            }
            .frame(width: 34, height: 4)
            .offset(y: tabDip)
            .frame(maxWidth: .infinity)
            .frame(height: 18)
            .contentShape(Rectangle().inset(by: -10))
            .onTapGesture { drawerOpen.toggle() }
            .accessibilityElement()
            .accessibilityLabel(Copy.moreForThisBook)
            .accessibilityAddTraits(.isButton)
            .accessibilityValue(drawerOpen ? Copy.open : "")
            .accessibilityAction { drawerOpen.toggle() }
    }

    /// What the drawer holds: a quiet day for the room (§4.7), another
    /// book (S13), and what is behind the ribbon. Each closes it.
    private var drawer: some View {
        let markedToday = model.markedQuietDayToday(in: room)
        return VStack(spacing: 8) {
            if !room.isPaused {
                // The quiet day is an act of care for the room (§4.7), not
                // a setting: set apart from the rows under it, warm, with
                // the banked fire it makes, and held rather than tapped —
                // the owner: *"marking a quiet day needs to be a press and
                // hold"*.
                QuietDayHold(marked: markedToday) {
                    model.markQuietDay(in: room)
                    drawerOpen = false
                }
                .padding(.bottom, 8)
                DrawerRow(title: Copy.startAnotherBook, detail: Copy.startAnotherDetail) {
                    drawerOpen = false
                    onStartAnother()
                }
            }
            if let reading {
                DrawerRow(title: Copy.whatIsBehindTheRibbon, detail: Copy.whatIsBehindTheRibbonDetail) {
                    drawerOpen = false
                    onHold(reading)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.top, 4)
        .padding(.bottom, 14)
    }

    // MARK: The way in

    private var book: BibleBook? { reading.flatMap { Bible.book(id: $0.bookID) } }

    private var title: String {
        guard let reading, let book else { return Copy.pickABookControl }
        let hasRead = model.state.positions.contains {
            $0.readingID == reading.id && $0.personID == model.me?.id
        }
        return hasRead ? Copy.continueIn(book.name) : Copy.begin(book.name)
    }

    private var wayIn: some View {
        HStack(alignment: .top, spacing: 18) {
            // Light runs up the ribbon now and then — the way it goes —
            // where it used to tug (the owner: the tug *"sucks"*). Gone
            // while the book is open; back out of its slot when it closes.
            SatinRibbon(glint: glints)
                .frame(width: 18, height: 78)
                .opacity(reading == nil ? 0.35 : 1)
                .scaleEffect(x: 1, y: ribbonOut || reduceMotion ? 1 : 0.001, anchor: .top)
                // Faded only under reduce motion, where it does not drop:
                // on the drop's spring a fade passed 1 and came back under
                // it, and the ribbon flickered as it came out.
                .opacity(ribbonOut || !reduceMotion ? 1 : 0)
                // Dropped back out, it swings from the gilt and comes to
                // rest.
                .ribbonSwing(ribbonDrops, amplitude: 5.5, delay: 0.16)
                .onChange(of: ribbonAway, initial: true) { was, away in
                    // Gone at once, under the ribbon taking its place; back
                    // once the page has gone, whatever closed it — dropping
                    // out of its slot a little past its length, and back.
                    if away {
                        ribbonOut = false
                    } else {
                        withAnimation((reduceMotion ? RibbonMotion.settle : RibbonMotion.dropped).delay(0.12)) {
                            ribbonOut = true
                        }
                        if was { ribbonDrops += 1 }
                    }
                }
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { ribbonFrame = $0 }
                // Out over the gilt edge, from between the pages; tucked in
                // while the drawer is up, where it would hang from nothing.
                .opacity(drawerOpen ? 0 : 1)
                .padding(.top, drawerOpen ? 0 : -15)
            VStack(alignment: .leading, spacing: 5) {
                // Set into the leather: letterpress, a shadow under each
                // letter where the cover is pressed.
                Text(title)
                    .font(RibbonType.displayMedium(21))
                    .foregroundStyle(Palette.text)
                    .shadow(color: .black.opacity(0.75), radius: 0, y: -1)
                    .shadow(color: Color(hex: "C9A86A").opacity(0.18), radius: 0, y: 1)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                subtitle
            }
            .padding(.top, 2)
        }
        .contentShape(Rectangle())
        .gesture(press)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityHint(reading == nil ? "" : Copy.pullTheRibbonHint)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { open() }
        .accessibilityAction(named: Copy.theRibbon) {
            if let reading { onHold(reading) }
        }
    }

    @ViewBuilder
    private var subtitle: some View {
        if room.isPaused {
            SmallCaps(Copy.roomIsPausedShort, size: 11)
        } else if let reading {
            HStack(spacing: 8) {
                SmallCaps(Copy.chapterLine(model.myPosition(in: reading).chapter), size: 11, color: Palette.text.opacity(0.7))
                // Said until the way in has been used once; after that
                // the glint is enough.
                if !model.state.hasPulledTheFire {
                    Circle().fill(Palette.text.opacity(0.4)).frame(width: 3, height: 3)
                    SmallCaps(Copy.pullTheRibbon, size: 11, color: Palette.chartreuse)
                }
            }
        }
    }

    private var glints: Bool {
        !reduceMotion && reading != nil && !ribbonAway && !room.isPaused
    }

    /// How long the cover is held for its drawer.
    private static let hold = 0.45

    /// Held: the drawer rises, or goes, and the hand feels it.
    private func heldForTheDrawer() {
        Haptics.shared.footHeld()
        drawerOpen.toggle()
    }

    /// One gesture for the three things a finger does here: up from the
    /// ribbon or its title opens the book (a third of the way to the top of
    /// the foot, or a flick); a tap does the same; held still, it raises
    /// the drawer, as the rest of the cover does, as soon as it has been
    /// held.
    private var press: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if pressedAt == nil {
                    pressedAt = .now
                    held = false
                    holding = Task { @MainActor in
                        try? await Task.sleep(for: .seconds(Self.hold))
                        guard !Task.isCancelled, pressedAt != nil else { return }
                        held = true
                        heldForTheDrawer()
                    }
                }
                if hypot(value.translation.width, value.translation.height) > 10 { holding?.cancel() }
            }
            .onEnded { value in
                holding?.cancel()
                pressedAt = nil
                guard !held else { return }
                let up = -value.translation.height
                let still = hypot(value.translation.width, value.translation.height) < 10
                if up > 34 || -value.velocity.height > 500 || still { open() }
            }
    }

    @State private var pressedAt: Date?
    @State private var held = false
    @State private var holding: Task<Void, Never>?

    private func open() {
        guard let reading else {
            if !room.isPaused { onPickABook() }
            return
        }
        onOpen(reading, ribbonFrame)
    }

    // MARK: Your rooms, and you

    private var people: some View {
        let members = model.members(of: room)
        let present = room.isPaused ? Set<UUID>() : Set(model.presentPeople.filter { !$0.isIdle }.map(\.id))
        return HStack(spacing: 10) {
            Button(action: onOpenRooms) {
                HStack(spacing: 6) {
                    HStack(spacing: -9) {
                        ForEach(members.prefix(3)) { membership in
                            PortraitView(
                                person: model.person(membership.personID),
                                ink: membership.ink, size: 30,
                                image: model.portrait(membership.personID))
                            .overlay(Circle().stroke(Palette.ground, lineWidth: 1.5))
                            .background(
                                Circle()
                                    .stroke(Palette.chartreuse, lineWidth: 1.5)
                                    .padding(-3)
                                    .opacity(present.contains(membership.personID) ? 1 : 0))
                        }
                    }
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(Palette.muted)
                }
                .padding(.leading, 6)
                .padding(.trailing, 10)
                .frame(height: 44)
                .background(Pressed(shape: Capsule()))
                .contentShape(Capsule())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(Copy.yourRoomsSpoken(model.displayName(of: room)))

            Button(action: onYou) {
                PortraitView(
                    person: model.me, ink: nil, size: 38,
                    image: model.me.flatMap { model.portrait($0.id) })
                .overlay(Circle().stroke(Palette.text.opacity(0.35), lineWidth: 1))
                .shadow(color: .black.opacity(0.45), radius: 5, y: 4)
                .overlay(alignment: .bottomTrailing) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Palette.text.opacity(0.85))
                        .frame(width: 19, height: 19)
                        .background(Circle().fill(Color(hex: "16130F")))
                        .overlay(Circle().stroke(Palette.ground, lineWidth: 1.5))
                        .offset(x: 3, y: 2)
                }
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(Copy.youAndYourSettings)
        }
    }

    // MARK: The plate

    private var plate: some View {
        LeatherPlate()
    }
}

/// A row in the foot's drawer: a panel pressed into the cover, its words in
/// letterpress and what it does under them.
private struct DrawerRow: View {
    var title: String
    var detail: String?
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text.opacity(0.95))
                    .shadow(color: .black.opacity(0.7), radius: 0, y: -1)
                if let detail {
                    Text(detail)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(FootInk.detail)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(minHeight: 52)
            .background(Pressed(shape: RoundedRectangle(cornerRadius: 12, style: .continuous)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
    }
}

/// The quiet day, held (I47): a warm panel raised off the cover with the
/// banked coals it makes. Held, its warmth fills across it from the coals,
/// and when it is full the day is marked; let go early and it drains back.
/// A quiet day is marked once a day: then it says so, and holds nothing.
private struct QuietDayHold: View {
    var marked: Bool
    var onMarked: () -> Void

    @State private var fill: CGFloat = 0
    private static let hold = 1.0

    var body: some View {
        HStack(spacing: 14) {
            BankedCoals()
                .frame(width: 40, height: 30)
            VStack(alignment: .leading, spacing: 2) {
                Text(Copy.markAQuietDay)
                    .font(RibbonType.displayMedium(18))
                    .foregroundStyle(Color(hex: "E6CFA2").opacity(marked ? 0.55 : 0.95))
                    .shadow(color: .black.opacity(0.7), radius: 0, y: -1)
                Text(marked ? Copy.quietDayMarked : Copy.quietDayHold)
                    .font(RibbonType.ui(13))
                    .foregroundStyle(FootInk.detail)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(minHeight: 64)
        .background {
            let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
            ZStack(alignment: .leading) {
                shape.fill(Color(hex: "2F241B"))
                // The warmth, filling as it is held.
                GeometryReader { proxy in
                    Rectangle()
                        .fill(Palette.flameDeep.opacity(0.32))
                        .frame(width: proxy.size.width * fill)
                }
                .clipShape(shape)
                shape.strokeBorder(Color(hex: "8A6A4A").opacity(0.5), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.45), radius: 4, y: 2)
        }
        .contentShape(Rectangle())
        .onLongPressGesture(minimumDuration: Self.hold, maximumDistance: 24) {
            guard !marked else { return }
            onMarked()
        } onPressingChanged: { pressing in
            guard !marked else { return }
            if pressing {
                withAnimation(.linear(duration: Self.hold)) { fill = 1 }
            } else {
                // Let go early — a tap, most likely: the warmth shows how
                // far it got, at least a little of it, and drains, so the
                // row says it is a thing to hold.
                let shown = max(fill, 0.16)
                withAnimation(.easeOut(duration: 0.15)) { fill = shown }
                withAnimation(.easeIn(duration: 0.45).delay(0.15)) { fill = 0 }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: Copy.markAQuietDay) {
            if !marked { onMarked() }
        }
    }
}

/// The colours the cover's words come in.
enum FootInk {
    static let detail = Color(hex: "BFA982").opacity(0.8)
}

/// A fire banked for a quiet day (§4.7), small, for the quiet day's row
/// (I47): a mound of coals under ash, glowing between them, and no flame.
private struct BankedCoals: View {
    var body: some View {
        Canvas { context, size in
            let w = size.width, h = size.height
            // The glow they keep.
            context.fill(
                Path(ellipseIn: CGRect(x: w * 0.05, y: h * 0.45, width: w * 0.9, height: h * 0.5)),
                with: .color(Palette.flameCore.opacity(0.28)))
            // The coals, heaped.
            let coals: [CGRect] = [
                CGRect(x: w * 0.12, y: h * 0.58, width: w * 0.34, height: h * 0.28),
                CGRect(x: w * 0.52, y: h * 0.58, width: w * 0.36, height: h * 0.28),
                CGRect(x: w * 0.30, y: h * 0.40, width: w * 0.40, height: h * 0.30),
            ]
            for coal in coals {
                context.fill(Path(ellipseIn: coal), with: .color(Color(hex: "5A4D44")))
            }
            // Fire between them, under the ash.
            var cracks = Path()
            cracks.move(to: CGPoint(x: w * 0.36, y: h * 0.70))
            cracks.addLine(to: CGPoint(x: w * 0.50, y: h * 0.66))
            cracks.addLine(to: CGPoint(x: w * 0.62, y: h * 0.72))
            cracks.move(to: CGPoint(x: w * 0.44, y: h * 0.52))
            cracks.addLine(to: CGPoint(x: w * 0.56, y: h * 0.56))
            context.stroke(cracks, with: .color(Palette.flameBright.opacity(0.9)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
        }
        .accessibilityHidden(true)
    }
}

/// A panel pressed into the leather: darker than the cover, edged in its
/// shadow. Flat: the owner had *"too many gradients"*.
struct Pressed<S: InsettableShape>: View {
    var shape: S

    var body: some View {
        ZStack {
            shape.fill(Color.black.opacity(0.26))
            shape.strokeBorder(Color.black.opacity(0.45), lineWidth: 1)
        }
    }
}

/// A satin ribbon (I47): one colour, a soft sheen down it where the light
/// falls, the faint grain of the weave, and a swallowtail. It was shaded
/// harder — dark selvedges, a bright stripe, a fold darkening one side of
/// the tail — and the owner found it *"weird and too harsh"*.
///
/// With `glint`, a band of light runs up it every few seconds, from the
/// tail to where it comes out — the way it is pulled.
struct SatinRibbon: View {
    var glint = false

    /// A glint every so often, and how long it takes to run up.
    private static let period = 4.2
    private static let run = 1.1
    static let colour = Color(hex: "C2CF5A")

    var body: some View {
        Group {
            if glint {
                TimelineView(.animation(minimumInterval: 1 / 30)) { context in
                    let t = context.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: Self.period)
                    canvas(glintAt: t < Self.run ? t / Self.run : nil)
                }
            } else {
                canvas(glintAt: nil)
            }
        }
        .shadow(color: .black.opacity(0.45), radius: 1.5, x: 1, y: 2)
        .accessibilityHidden(true)
    }

    /// `glintAt`: how far up the ribbon the light has run, 0...1, or none.
    private func canvas(glintAt progress: Double?) -> some View {
        Canvas { context, size in
            let w = size.width, h = size.height
            let notch: CGFloat = min(9, w * 0.6)
            var shape = Path()
            shape.move(to: .zero)
            shape.addLine(to: CGPoint(x: w, y: 0))
            shape.addLine(to: CGPoint(x: w, y: h))
            shape.addLine(to: CGPoint(x: w / 2, y: h - notch))
            shape.addLine(to: CGPoint(x: 0, y: h))
            shape.closeSubpath()
            var satin = context
            satin.clip(to: shape)
            satin.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.colour))
            // The sheen: a soft band of light down it, a little left of the
            // middle, where satin catches it.
            satin.fill(
                Path(CGRect(x: w * 0.22, y: 0, width: w * 0.36, height: h)),
                with: .color(.white.opacity(0.13)))
            // The weave, faint.
            var weave = Path()
            var y: CGFloat = 1
            while y < h {
                weave.move(to: CGPoint(x: 0, y: y))
                weave.addLine(to: CGPoint(x: w, y: y))
                y += 2
            }
            satin.stroke(weave, with: .color(.black.opacity(0.05)), lineWidth: 0.5)
            // The glint, running up: quick from the tail, slowing as it
            // reaches the slot, and fading as it goes.
            if let progress {
                let eased = 1 - pow(1 - progress, 2)
                let band: CGFloat = 26
                let top = (h + band) * (1 - eased) - band
                satin.fill(
                    Path(CGRect(x: 0, y: top, width: w, height: band)),
                    with: .color(.white.opacity(0.22 * (1 - progress * 0.6))))
            }
        }
    }
}

/// The foot's ribbon, carried up into the book (I47): one movement, on the
/// page's own spring. As the page comes up from the foot, the ribbon is
/// drawn up with it out of its slot — its top to the book's leather head,
/// its tail to the reading line — and lands where the page's own ribbon
/// hangs (`PageRibbon`), which takes over from it there. The root draws it
/// over everything, and moves `rise` from 0 to 1 with the page.
struct RibbonFlight: View {
    @Environment(AppModel.self) private var model
    /// Where the foot's ribbon was, in global coordinates.
    let from: CGRect
    /// 0 in the foot, 1 on the page.
    var rise: CGFloat

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            GeometryReader { proxy in
                let origin = proxy.frame(in: .global).origin
                // The page's column and margin (A69), worked out as the
                // page works them out.
                let column = min(proxy.size.width, 680)
                let margin = ReadingTheme.margin(
                    requested: model.settings.marginRequested, columnWidth: column,
                    fontSize: CGFloat(model.settings.scriptureSize))
                let page = PageRibbon.place(
                    screen: proxy.size, safeTop: safeTop,
                    columnWidth: column, margin: margin)
                SatinRibbon()
                    .modifier(RibbonFlightPlacement(
                        rise: rise,
                        from: from.offsetBy(dx: -origin.x, dy: -origin.y),
                        toX: page.x, toTop: page.top, toTail: page.tail))
            }
            .ignoresSafeArea()
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// Where the ribbon is at `rise` of its way from the foot to the page:
/// animatable, so the root's spring moves it frame by frame.
private struct RibbonFlightPlacement: ViewModifier, Animatable {
    var rise: CGFloat
    let from: CGRect
    let toX: CGFloat
    let toTop: CGFloat
    let toTail: CGFloat

    var animatableData: CGFloat {
        get { rise }
        set { rise = newValue }
    }

    func body(content: Content) -> some View {
        let p = min(max(rise, 0), 1)
        let top = from.minY + (toTop - from.minY) * p
        let tail = from.maxY + (toTail - from.maxY) * p
        let length = max(from.height, tail - top)
        let width = from.width + (PageRibbon.width - from.width) * p
        let x = from.midX + (toX - from.midX) * p
        return content
            .frame(width: width, height: length)
            .position(x: x, y: top + length / 2)
    }
}
