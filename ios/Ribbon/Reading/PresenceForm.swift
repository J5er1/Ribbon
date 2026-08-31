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
    let room: Room
    var onFollow: (PresentPerson) -> Void

    @State private var expanded = false
    @State private var frontIndex = 0
    @State private var holdTarget: UUID?
    @State private var holdProgress: CGFloat = 0

    private var people: [PresentPerson] { model.presentPeople }

    var body: some View {
        Group {
            if people.isEmpty && !model.readingQuietly {
                // Absence is the honest rendering of absence.
                EmptyView()
            } else if expanded {
                expandedPanel
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            } else {
                collapsedForm
                    .transition(.move(edge: .trailing).combined(with: .opacity))
            }
        }
        .animation(RibbonMotion.open, value: expanded)
        .animation(RibbonMotion.arrive, value: people)
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
                    .accessibilityLabel("Reading quietly. Only you can see you.")
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
        }
        .accessibilityLabel(presenceLabel(person, othersCount: people.count - 1))
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
                    if holdTarget == person.id {
                        // Thinking of you (§4.3): the portrait fills with
                        // your ink over ~700 ms; release completes it.
                        Circle()
                            .trim(from: 0, to: holdProgress)
                            .stroke(myInk.color, lineWidth: 2.5)
                            .rotationEffect(.degrees(-90))
                            .frame(width: 38, height: 38)
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
        .onTapGesture { onFollow(person) }
        .onLongPressGesture(minimumDuration: 0.7) {
            holdProgress = 1
            Haptics.shared.completeThinkingOfYouHold()
            Task { await model.presence.sendThinkingOfYou(to: person.id) }
            holdTarget = nil
            holdProgress = 0
        } onPressingChanged: { pressing in
            if pressing {
                holdTarget = person.id
                Haptics.shared.beginThinkingOfYouHold()
                withAnimation(RibbonMotion.inkFill) { holdProgress = 1 }
            } else if holdTarget == person.id {
                Haptics.shared.cancelThinkingOfYouHold()
                holdTarget = nil
                withAnimation(.easeOut(duration: 0.15)) { holdProgress = 0 }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(presenceLabel(person, othersCount: 0))
        .accessibilityHint("Tap to follow. Hold to let them know you're thinking of them.")
    }

    private var myInk: Ink {
        model.currentRoom.flatMap { model.myMembership(in: $0)?.ink } ?? model.lastUsedInk
    }

    private func presenceLabel(_ person: PresentPerson, othersCount: Int) -> String {
        let name = model.person(person.id)?.name ?? person.name
        let base = person.isIdle ? "\(name) is here, but still" : "\(name) is reading"
        // Never a count of people — name who else is here instead.
        if othersCount > 0 {
            let others = people.dropFirst().compactMap { model.person($0.id)?.name ?? $0.name }
            return base + ", with " + others.joined(separator: " and ")
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
