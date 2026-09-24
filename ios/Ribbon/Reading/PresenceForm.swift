import SwiftUI
import RibbonCore

// The presence form (§4.2, S07): a soft form half-emerged from the right
// edge. Left edge is the gutter and belongs to notes; the right edge
// belongs to people. Nothing crosses.
//
// Five states: absent (nobody here — not greyed, not a placeholder),
// someone here, several here (a short vertical stack, never a row of
// shrinking avatars), following (a ring in their ink and a chartreuse
// thread down the edge), and reading quietly (you see a small closed
// shape; others see nothing at all).

struct PresenceForm: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    /// A portrait tapped: follow them — or, if you already are, stop.
    var onFollow: (PresentPerson) -> Void
    /// The panel opened or closed. A page that follows holds still while
    /// it is open.
    var onExpand: (Bool) -> Void = { _ in }

    @State private var expanded = false
    @State private var holdTarget: UUID?
    @State private var holdProgress: CGFloat = 0
    /// The portrait just thought of: its ring stays full a moment and then
    /// lets go, so the hold ends on what it did instead of vanishing on the
    /// frame it succeeded.
    @State private var sentTo: UUID?
    /// The roster has been read once. Whoever is on it then was already in
    /// the book when you opened it — they are not arriving.
    @State private var rosterSeen = false
    /// "Ruth is with you" appears once per follower, then rests.
    @State private var announcedFollowers: Set<UUID> = []

    /// The roster as drawn: a person who has left stays for the length of
    /// the arrive curve, fading, rather than vanishing between two frames
    /// (A27). What is *announced* is still the socket's truth — the hold
    /// is a drawing, not a claim.
    @State private var shown: [PresentPerson] = []
    @State private var leaving: Set<UUID> = []
    private var people: [PresentPerson] { shown }

    /// Someone whose scroll is yours: their portrait tucks against the form.
    private var follower: PresentPerson? {
        guard let me = model.me?.id else { return nil }
        return people.first { $0.followingPersonID == me }
    }

    var body: some View {
        Group {
            if people.isEmpty && !model.readingQuietly {
                // Absence is the honest rendering of absence.
                EmptyView()
            } else if expanded {
                expandedPanel
                    .transition(fromTheEdge)
            } else {
                collapsedForm
                    .transition(fromTheEdge)
            }
        }
        .animation(RibbonMotion.open, value: expanded)
        .animation(RibbonMotion.arrive, value: people)
        .onChange(of: model.presentPeople, initial: true) { _, now in
            holdRoster(now)
        }
        .onChange(of: expanded) { _, open in
            onExpand(open)
        }
        .onDisappear {
            if expanded { onExpand(false) }
        }
    }

    /// The form eases out of the edge it lives on. Under reduce motion it
    /// fades there instead (§11: morphs become cross-fades) — it used to
    /// cut, which is not the same thing as holding still.
    private var fromTheEdge: AnyTransition {
        reduceMotion
            ? AnyTransition.opacity
            : AnyTransition.move(edge: .trailing).combined(with: .opacity)
    }

    /// Arrivals and changes land at once; departures are held for one
    /// arrive so the lozenge can fade out reading the name.
    private func holdRoster(_ now: [PresentPerson]) {
        let nowIDs = Set(now.map(\.id))
        // Someone arriving in the book: the one soft transient §9.3 and S07
        // give it, which had never been played. Not for the people already
        // here when the book opened, not for yourself, and not for somebody
        // coming back inside the fade — as far as the page knows they never
        // left.
        let shownIDs = Set(shown.map(\.id))
        let me = model.me?.id
        if rosterSeen, nowIDs.contains(where: { !shownIDs.contains($0) && $0 != me }) {
            Haptics.shared.someoneArrives()
        }
        rosterSeen = true
        // Somebody who came back inside the fade is not leaving.
        leaving.subtract(nowIDs)
        var next = now
        for person in shown where !nowIDs.contains(person.id) && !leaving.contains(person.id) {
            leaving.insert(person.id)
            next.append(person)
            let id = person.id
            DispatchQueue.main.asyncAfter(deadline: .now() + (reduceMotion ? 0 : RibbonMotion.arriveDuration)) {
                guard leaving.contains(id) else { return }
                leaving.remove(id)
                shown.removeAll { $0.id == id }
            }
        }
        for person in shown where leaving.contains(person.id) && !next.contains(where: { $0.id == person.id }) {
            next.append(person)
        }
        shown = next
    }

    // MARK: Collapsed — the lozenge

    @ViewBuilder
    private var collapsedForm: some View {
        VStack(spacing: 3) {
            if model.readingQuietly {
                // A small closed shape at the edge, so you never forget
                // you're invisible. Stays tappable even when you're alone.
                Capsule()
                    .fill(Palette.raised)
                    .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                    .frame(width: 20, height: 34)
                    .accessibilityLabel(Copy.readingQuietlySpoken)
            } else if let front = people.first {
                lozenge(front, stacked: people.count > 1)
            }
        }
        .padding(.trailing, -14)  // half off-screen
        .contentShape(Rectangle())
        .onTapGesture {
            if model.readingQuietly || people.count > 1 {
                withAnimation(RibbonMotion.open) { expanded = true }
            } else if let person = people.first {
                onFollow(person)
            }
        }
        .gesture(
            DragGesture(minimumDistance: 12)
                .onEnded { value in
                    if value.translation.width < -20 {
                        withAnimation(RibbonMotion.open) { expanded = true }
                    }
                })
        .onLongPressGesture(minimumDuration: 0.35) {
            withAnimation(RibbonMotion.open) { expanded = true }
        }
    }

    private func lozenge(_ person: PresentPerson, stacked: Bool) -> some View {
        VStack(alignment: .trailing, spacing: 6) {
            ZStack(alignment: .trailing) {
                if stacked {
                    // A hairline of the next one behind.
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Palette.raised)
                        .frame(width: 44, height: 64)
                        .offset(x: 5, y: 8)
                        .opacity(0.6)
                }
                portrait(person)
                    .frame(width: 44, height: 64)
                    .ribbonGlass(in: RoundedRectangle(cornerRadius: 20))
                // Being followed is visible but small: their portrait tucks
                // against yours (§4.2).
                if let follower {
                    PortraitView(
                        person: model.person(follower.id),
                        ink: model.membership(of: follower.id, in: room.id)?.ink,
                        size: 20,
                        image: model.portrait(follower.id))
                    .offset(x: -30, y: 24)
                }
            }
            if let follower, !announcedFollowers.contains(follower.id) {
                SmallCaps(
                    Copy.isWithYou(firstName(model.person(follower.id)?.name ?? follower.name)),
                    size: 11)
                    .padding(.trailing, 18)
                    .task {
                        try? await Task.sleep(for: .seconds(4))
                        withAnimation(RibbonMotion.arrive) {
                            _ = announcedFollowers.insert(follower.id)
                        }
                    }
            }
        }
        .accessibilityLabel(presenceLabel(person, othersCount: people.count - 1))
        // The one you follow is "selected", in the system's own word; tapping
        // it again stops following.
        .accessibilityAddTraits(model.followingPersonID == person.id ? .isSelected : [])
    }

    private func portrait(_ person: PresentPerson) -> some View {
        PortraitView(
            person: model.person(person.id),
            ink: model.membership(of: person.id, in: room.id)?.ink,
            size: 38,
            image: model.portrait(person.id))
        .opacity(person.isIdle ? 0.6 : 1)
        .overlay {
            if model.followingPersonID == person.id,
               let ink = model.membership(of: person.id, in: room.id)?.ink {
                Circle().strokeBorder(ink.color, lineWidth: 1.6)
                    .frame(width: 40, height: 40)
            }
        }
    }

    // MARK: Expanded — who's here, and the one gesture

    private var expandedPanel: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(people) { person in
                personRow(person)
            }
            if model.readingQuietly {
                HStack(spacing: 8) {
                    Capsule().fill(Palette.raised)
                        .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                        .frame(width: 14, height: 22)
                    SmallCaps(Copy.onlyYouCanSeeYou, size: 11)
                }
            }
            Divider().overlay(Palette.rule)
            Button {
                model.readingQuietly.toggle()
                withAnimation(RibbonMotion.open) { expanded = false }
            } label: {
                SmallCaps(Copy.readQuietly, size: 12,
                          color: model.readingQuietly ? Palette.chartreuse : Palette.muted)
                    .frame(minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(width: 200, alignment: .leading)
        .ribbonGlass(in: RoundedRectangle(cornerRadius: 22), interactive: true)
        .gesture(
            DragGesture(minimumDistance: 12)
                .onEnded { value in
                    if value.translation.width > 20 {
                        withAnimation(RibbonMotion.open) { expanded = false }
                    }
                })
        .onTapGesture {
            withAnimation(RibbonMotion.open) { expanded = false }
        }
    }

    private func personRow(_ person: PresentPerson) -> some View {
        HStack(spacing: 10) {
            portrait(person)
                .frame(width: 34, height: 34)
                .overlay {
                    if holdTarget == person.id || sentTo == person.id {
                        // Thinking of you (§4.3): the portrait fills with
                        // your ink over ~700 ms; release completes it.
                        Circle()
                            .trim(from: 0, to: sentTo == person.id ? 1 : holdProgress)
                            .stroke(myInk.color, lineWidth: 2.5)
                            .rotationEffect(.degrees(-90))
                            .frame(width: 38, height: 38)
                            .transition(.opacity)
                    }
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(model.person(person.id)?.name ?? person.name)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                SmallCaps(
                    person.isIdle ? Copy.hereButStill : (person.position?.chapterFormatted ?? ""),
                    size: 11)
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .onTapGesture { follow(person) }
        .onLongPressGesture(minimumDuration: 0.7) {
            thinkOf(person)
        } onPressingChanged: { pressing in
            if pressing {
                sentTo = nil
                holdTarget = person.id
                Haptics.shared.beginThinkingOfYouHold()
                if reduceMotion {
                    // An instant state change with the haptic intact (§11).
                    holdProgress = 1
                } else {
                    withAnimation(RibbonMotion.inkFill) { holdProgress = 1 }
                }
            } else if holdTarget == person.id {
                // Let go short: the ring lets go on the let-go curve
                // (it had a speed of its own no token names).
                Haptics.shared.cancelThinkingOfYouHold()
                withAnimation(RibbonMotion.release) {
                    holdTarget = nil
                    holdProgress = 0
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(presenceLabel(person, othersCount: 0))
        // Followed, the row is "selected" and activating it stops following
        // — so it no longer says it follows them. No new words.
        .accessibilityAddTraits(model.followingPersonID == person.id ? .isSelected : [])
        .accessibilityHint(model.followingPersonID == person.id ? "" : Copy.followsThem)
        // The hold, as an action (§11): a screen reader could hear that
        // holding would send it, and had nothing to do instead of holding.
        // Android has always published it this way.
        .accessibilityAction(named: Copy.thinkingOfYou) { thinkOf(person) }
    }

    /// A row used to follow someone, or to stop: the panel folds away as it
    /// does. The page holds still while the panel is open, so a follow
    /// started from it would never move the page until you closed it
    /// yourself — and a screen reader had no way to.
    private func follow(_ person: PresentPerson) {
        onFollow(person)
        withAnimation(RibbonMotion.open) { expanded = false }
    }

    /// The hold completed, or the action taken: the tap on the shoulder goes
    /// (§4.3).
    private func thinkOf(_ person: PresentPerson) {
        Haptics.shared.completeThinkingOfYouHold()
        model.thinkOf(person.id)
        // The ring stays full for a moment and then lets go, so what
        // the hold did is seen as well as felt.
        sentTo = person.id
        holdTarget = nil
        holdProgress = 0
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(600))
            withAnimation(RibbonMotion.settle) {
                if sentTo == person.id { sentTo = nil }
            }
        }
    }

    private var myInk: Ink {
        model.currentRoom.flatMap { model.myMembership(in: $0)?.ink } ?? model.lastUsedInk
    }

    private func presenceLabel(_ person: PresentPerson, othersCount: Int) -> String {
        let name = model.person(person.id)?.name ?? person.name
        let base = person.isIdle ? Copy.personIsHereButStill(name) : Copy.personIsReading(name)
        // Never a count of people — name who else is here instead.
        if othersCount > 0 {
            let others = people.dropFirst().compactMap { model.person($0.id)?.name ?? $0.name }
            return Copy.alsoHere(base, others)
        }
        return base
    }
}

/// The chartreuse thread down the screen's right edge while following.
struct FollowThread: View {
    var body: some View {
        Rectangle()
            .fill(Palette.chartreuse.opacity(0.7))
            .frame(width: 1)
            .ignoresSafeArea(edges: .vertical)
            .accessibilityHidden(true)
    }
}
