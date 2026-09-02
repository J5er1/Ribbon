import SwiftUI
import RibbonCore

// The cards (§4.6, S08, S09): a question everyone answers before anyone
// reads the answers. Sealed: the question, your answer field, one line —
// "This opens when everyone has answered." — and "set it down". Never who
// hasn't answered, never how many have, no expiry, no nudge. Open: a slow
// turn over 480 ms, ease-out, no bounce, and every answer together. No
// ordering by time, no reactions, no replies: a moment, not a thread.

/// The card slot at a passage end (S03): sealed, open, or nothing. When
/// the last answer lands while the reader is looking, it turns live.
struct PassageCardSlot: View {
    @Environment(\.appModel) private var model
    let reading: Reading
    let chapter: Int

    var body: some View {
        Group {
            if reading.isFinished {
                // An ember's record shows its open cards (S11); the pages
                // of a finished book carry no sealed card to answer.
                if let card = model.card(for: reading, chapter: chapter), card.state == .open {
                    OpenCardView(card: card, roomID: reading.roomID, animateTurn: false)
                        .padding(.horizontal, 26)
                }
            } else if let card = model.card(for: reading, chapter: chapter) {
                switch card.state {
                case .sealed:
                    SealedCardView(card: card)
                        .padding(.horizontal, 26)
                case .open:
                    OpenCardView(card: card, roomID: reading.roomID, animateTurn: true)
                        .padding(.horizontal, 26)
                        .onAppear { model.markCardSeen(card) }
                case .setDown:
                    EmptyView()
                }
            }
        }
        // Minted on arrival at the passage end, never during a body.
        .onAppear { model.mintCardIfNeeded(for: reading, chapter: chapter) }
    }
}

/// S08 — a card, sealed.
struct SealedCardView: View {
    @Environment(\.appModel) private var model
    let card: ReflectionCard

    @State private var draft = ""
    @State private var editing = false
    @FocusState private var focused: Bool

    private var answered: String? { model.myAnswer(on: card) }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(card.question)
                .font(RibbonType.scripture(17))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)

            if let answered, !editing {
                // Answered: your words, visible only to you, editable until
                // it opens.
                Button {
                    draft = answered
                    editing = true
                    focused = true
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(answered)
                            .font(RibbonType.ui(16))
                            .foregroundStyle(Palette.text)
                            .fixedSize(horizontal: false, vertical: true)
                            .multilineTextAlignment(.leading)
                        SmallCaps(Copy.editAnswer, size: 11)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(Copy.yourAnswer): \(answered). \(Copy.editAnswer)")
            } else {
                // The field, open: nothing beyond a single hairline.
                VStack(alignment: .leading, spacing: 8) {
                    TextField("", text: $draft, axis: .vertical)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                        .lineLimit(1...8)
                        .focused($focused)
                        .accessibilityLabel(Copy.yourAnswer)
                    HairlineRule()
                    if !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        QuietControl(title: Copy.saveAnswer) {
                            model.answer(card, text: draft)
                            editing = false
                            focused = false
                        }
                    }
                }
            }

            HStack {
                SmallCaps(Copy.cardOpensWhenEveryoneHasAnswered, size: 11)
                Spacer()
                QuietControl(title: Copy.setItDown) {
                    withAnimation(RibbonMotion.settle) { model.setDown(card) }
                }
            }
        }
        .padding(18)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.rule, lineWidth: 1))
    }
}

/// S09 — a card, open: every answer, each with its author's portrait and
/// ink. The turn is a visual moment; no haptic (§9.3).
struct OpenCardView: View {
    @Environment(\.appModel) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let card: ReflectionCard
    let roomID: UUID
    var animateTurn: Bool

    @State private var turned = false

    /// A stable order that is not the order people answered in.
    private var answers: [(personID: UUID, text: String)] {
        card.answers
            .map { (personID: $0.key, text: $0.value) }
            .sorted { $0.personID.uuidString < $1.personID.uuidString }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(card.question)
                .font(RibbonType.scripture(17))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            ForEach(answers, id: \.personID) { answer in
                HStack(alignment: .top, spacing: 10) {
                    PortraitView(
                        person: model.person(answer.personID),
                        ink: model.inkForDisplay(answer.personID, in: roomID),
                        size: 22,
                        image: model.portrait(answer.personID))
                    Text(answer.text)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(model.person(answer.personID)?.name ?? ""): \(answer.text)")
            }
        }
        .padding(18)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.rule, lineWidth: 1))
        // The turn: 480 ms, ease-out, no bounce — a fade under Reduce
        // Motion (§11).
        .rotation3DEffect(
            .degrees(animateTurn && !reduceMotion && !turned ? -90 : 0),
            axis: (x: 1, y: 0, z: 0), perspective: 0.6)
        .opacity(animateTurn && !turned ? 0 : 1)
        .onAppear {
            guard animateTurn else { turned = true; return }
            withAnimation(RibbonMotion.open) { turned = true }
        }
    }
}
