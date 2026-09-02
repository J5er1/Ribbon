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
// shape; others see nothing at all). The lozenge and the panel are one
// piece of glass morphing between two shapes (§12.1).

struct PresenceForm: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let room: Room
    /// Owned by the reading surface: the measure insets while the panel is
    /// open, so glass never lies over a verse (§12.1). The Wave's hold
    /// opens it too, which is how read quietly is reachable when alone.
    @Binding var expanded: Bool
    var onFollow: (PresentPerson) -> Void

    @State private var holdTarget: UUID?
    @State private var holdProgress: CGFloat = 0
    @State private var holdBegan: Date?
    /// "Ruth is with you" appears once per follower, then rests.
    @State private var announcedFollowers: Set<UUID> = []
    @State private var frontIndex = 0
    @Namespace private var glass

    private var people: [PresentPerson] { model.presentPeople }
    private var largeType: Bool { dynamicTypeSize.isAccessibilitySize }

    /// Someone whose scroll is yours: their portrait tucks against the form.
    private var follower: PresentPerson? {
        guard let me = model.me?.id else { return nil }
        return people.first { $0.followingPersonID == me }
    }

    var body: some View {
        GlassEffectContainer(spacing: 12) {
            Group {
                if people.isEmpty && !model.readingQuietly && !expanded {
                    // Absence is the honest rendering of absence.
                    EmptyView()
                } else if expanded {
                    expandedPanel
                } else {
                    collapsedForm
                }
            }
        }
        .animation(reduceMotion ? nil : RibbonMotion.open, value: expanded)
        .animation(reduceMotion ? nil : RibbonMotion.arrive, value: people)
    }

    // MARK: Collapsed — the lozenge

    @ViewBuilder
    private var collapsedForm: some View {
        VStack(spacing: 3) {
            if people.isEmpty {
                // Reading quietly, alone: a small closed shape at the edge,
                // fully on screen, so you never forget you're invisible —
                // and never stranded without the toggle.
                Capsule()
                    .fill(Palette.raised)
                    .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                    .frame(width: 22, height: 36)
                    .frame(width: 44, height: 56)
                    .contentShape(Rectangle())
                    .padding(.trailing, 4)
                    .accessibilityLabel(Copy.readingQuietly)
                    .accessibilityHint(Copy.onlyYouCanSeeYou)
            } else {
                lozenge(people[min(frontIndex, people.count - 1)], stacked: people.count > 1)
                    .padding(.trailing, -22)  // about half off-screen
            }
        }
        .glassEffectID("form", in: glass)
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
                    } else if people.count > 1, abs(value.translation.height) > 20 {
                        // Several here: flick through, one at a time.
                        let step = value.translation.height < 0 ? 1 : -1
                        frontIndex = (frontIndex + step + people.count) % people.count
                    }
                })
        .onLongPressGesture(minimumDuration: 0.35) {
            withAnimation(RibbonMotion.open) { expanded = true }
        }
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: Copy.tapToFollow) {
            if let person = people.first { onFollow(person) }
        }
        .accessibilityAction(named: Copy.readQuietly) {
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
                        ink: model.inkForDisplay(follower.id, in: room.id),
                        size: 20,
                        image: model.portrait(follower.id))
                    .offset(x: -30, y: 24)
                }
            }
            .opacity(person.isIdle ? 0.8 : 1)
            if let follower, !announcedFollowers.contains(follower.id) {
                SmallCaps(
                    Copy.isWithYou(firstName(model.person(follower.id)?.name ?? follower.name)),
                    size: 11)
                    .padding(.trailing, 26)
                    .task {
                        try? await Task.sleep(for: .seconds(4))
                        withAnimation(RibbonMotion.arrive) {
                            _ = announcedFollowers.insert(follower.id)
                        }
                    }
            }
        }
        .accessibilityLabel(presenceLabel(person, others: people.filter { $0.id != person.id }))
    }

    private func portrait(_ person: PresentPerson) -> some View {
        PortraitView(
            person: model.person(person.id),
            ink: model.inkForDisplay(person.id, in: room.id),
            size: 38,
            image: model.portrait(person.id))
        .opacity(person.isIdle ? 0.6 : 1)
        .overlay {
            // A ring in their ink while following — their display ink, so
            // a room of two draws it too.
            if model.followingPersonID == person.id {
                Circle().strokeBorder(model.inkForDisplay(person.id, in: room.id).color, lineWidth: 1.6)
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
            if !people.isEmpty || model.readingQuietly {
                Divider().overlay(Palette.rule)
            }
            // The toggle says what it does, both ways (§10.1).
            Button {
                model.readingQuietly.toggle()
                withAnimation(RibbonMotion.open) { expanded = false }
            } label: {
                SmallCaps(model.readingQuietly ? Copy.beSeenAgain : Copy.readQuietly, size: 12,
                          color: model.readingQuietly ? Palette.chartreuse : Palette.text)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(model.readingQuietly ? Copy.readingQuietly : "")
        }
        .padding(16)
        .frame(width: largeType ? 240 : 200, alignment: .leading)
        .ribbonGlass(in: RoundedRectangle(cornerRadius: 22), interactive: true)
        .glassEffectID("form", in: glass)
        .padding(.trailing, 8)
        .gesture(
            DragGesture(minimumDistance: 12)
                .onEnded { value in
                    if value.translation.width > 20 {
                        withAnimation(RibbonMotion.open) { expanded = false }
                    }
                })
        .accessibilityAction(named: Copy.close) {
            withAnimation(RibbonMotion.open) { expanded = false }
        }
    }

    private func personRow(_ person: PresentPerson) -> some View {
        HStack(spacing: 10) {
            portrait(person)
                .frame(width: 34, height: 34)
                .overlay {
                    if holdTarget == person.id {
                        // Thinking of you (§4.3): the portrait fills with
                        // your ink over ~700 ms; release completes it.
                        Circle()
                            .fill(myInk.color.opacity(0.85))
                            .scaleEffect(holdProgress)
                            .frame(width: 34, height: 34)
                    }
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(model.person(person.id)?.name ?? person.name)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                SmallCaps(
                    person.isIdle ? Copy.hereButStill : (person.position?.chapterFormatted ?? ""),
                    size: 11)
            }
            Spacer(minLength: 0)
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
        // One gesture for both: a tap follows; a hold of ~700 ms sends
        // thinking-of-you on release — never on a timer, never by mistake.
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard holdTarget != person.id else { return }
                    holdTarget = person.id
                    holdBegan = Date()
                    Haptics.shared.beginThinkingOfYouHold()
                    if reduceMotion {
                        // An instant state change with the haptic intact (§11).
                        holdProgress = 1
                    } else {
                        holdProgress = 0
                        withAnimation(RibbonMotion.inkFill) { holdProgress = 1 }
                    }
                }
                .onEnded { value in
                    let held = holdBegan.map { Date().timeIntervalSince($0) } ?? 0
                    let moved = abs(value.translation.width) > 12 || abs(value.translation.height) > 12
                    if held >= 0.7, !moved {
                        Haptics.shared.completeThinkingOfYouHold()
                        Task { await model.presence.sendThinkingOfYou(to: person.id) }
                    } else {
                        Haptics.shared.cancelThinkingOfYouHold()
                        if held < 0.35, !moved {
                            onFollow(person)
                            withAnimation(RibbonMotion.open) { expanded = false }
                        }
                    }
                    holdTarget = nil
                    holdBegan = nil
                    withAnimation(.easeOut(duration: 0.15)) { holdProgress = 0 }
                })
        .accessibilityElement(children: .combine)
        .accessibilityLabel(presenceLabel(person, others: []))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: Copy.tapToFollow) {
            onFollow(person)
            withAnimation(RibbonMotion.open) { expanded = false }
        }
        .accessibilityAction(named: Copy.thinkingOfThem) {
            Haptics.shared.completeThinkingOfYouHold()
            Task { await model.presence.sendThinkingOfYou(to: person.id) }
        }
    }

    private var myInk: Ink {
        model.me.map { model.inkForDisplay($0.id, in: room.id) } ?? model.lastUsedInk
    }

    private func presenceLabel(_ person: PresentPerson, others: [PresentPerson]) -> String {
        let name = model.person(person.id)?.name ?? person.name
        let where_ = person.isIdle ? Copy.hereButStill : (person.position?.chapterFormatted ?? "")
        var label = where_.isEmpty ? "\(name) is reading" : Copy.presenceRow(name, where_)
        if model.followingPersonID == person.id { label += ", following" }
        // Never a count of people — name who else is here instead.
        if !others.isEmpty {
            label += ", with " + others.map { model.person($0.id)?.name ?? $0.name }.joined(separator: " and ")
        }
        return label
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
