import SwiftUI
import RibbonCore

// The reflection card (§4.6, S08/S09, ledger A33): a question everyone
// answers before anyone reads the answers.
//
// Sealed: the question in Literata, generously set; your answer in ivory
// once you have given one, or the field — no prompt, no box, a hairline
// under the words and the cursor in your ink; "This opens when everyone has
// answered." The card never names who hasn't answered and never says how
// many have. "set it down" is quiet, at the right.
//
// Open: the card turns over — 480 ms, no overshoot — and every answer is
// there in membership order with a face and a first name in the author's
// ink; the answers themselves in ivory, because they are words and not
// marks. No timestamps, no reactions, no replies.

struct ReflectionCardView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let card: ReflectionCard
    let reading: Reading
    let room: Room

    @State private var answerDraft: String = ""
    @State private var isEditing = false
    /// 0 sealed face up, 1 open face up: the card's turn.
    @State private var turn: CGFloat = 0
    @FocusState private var isFieldFocused: Bool

    private var myAnswer: String? {
        guard let me = model.me else { return nil }
        return card.answers[me.id]
    }

    private var myInk: Ink {
        model.myMembership(in: room)?.ink ?? model.lastUsedInk
    }

    var body: some View {
        if card.state == .setDown {
            EmptyView()
        } else {
            let open = card.state == .open
            ZStack {
                if reduceMotion {
                    // The card turn becomes a fade (§11) — the open face
                    // cross-fades in where the sealed one was. It used to
                    // cut.
                    face(open: open)
                        .id(open)
                        .transition(.opacity)
                } else {
                    CardTurn(turn: turn, sealed: face(open: false), opened: face(open: true))
                }
            }
            .animation(reduceMotion ? RibbonMotion.open : nil, value: open)
            .onAppear {
                turn = open ? 1 : 0
                if let myAnswer { answerDraft = myAnswer }
            }
            .onChange(of: open) { _, isOpen in
                withAnimation(RibbonMotion.open(still: reduceMotion)) { turn = isOpen ? 1 : 0 }
            }
            .accessibilityElement(children: .contain)
        }
    }

    private func face(open: Bool) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(card.question)
                .font(RibbonType.scripture(19))
                .foregroundStyle(Palette.text)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityLabel(open ? Copy.cardOpenSpoken(card.question) : Copy.cardSealedSpoken(card.question))
            if open {
                openContent
            } else {
                sealedContent
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .paper(.card)
    }

    // MARK: - S08: Sealed

    private var typed: Bool {
        !answerDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    @ViewBuilder
    private var sealedContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let myAnswer, !isEditing {
                VStack(alignment: .leading, spacing: 8) {
                    Text(myAnswer)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityLabel("\(Copy.yourAnswer). \(myAnswer)")
                    QuietControl(title: Copy.editYourAnswer) {
                        answerDraft = myAnswer
                        withAnimation(RibbonMotion.arrive) { isEditing = true }
                        isFieldFocused = true
                    }
                }
                Text(Copy.cardOpensWhenEveryoneHasAnswered)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .padding(.top, 4)
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    // No prompt and no box: the words go where the words
                    // go, with a hairline under them and the cursor in
                    // your ink.
                    TextField("", text: $answerDraft, axis: .vertical)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                        .tint(myInk.color)
                        .lineLimit(1...8)
                        .focused($isFieldFocused)
                        .accessibilityLabel(Copy.yourAnswer)
                    HairlineRule()
                    if typed || isEditing {
                        HStack(spacing: 18) {
                            Button {
                                let text = answerDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                                guard !text.isEmpty else { return }
                                // The field gives way to the answer, and
                                // the answer to the field, by cross-fade.
                                withAnimation(RibbonMotion.arrive) {
                                    model.answerCard(card, answer: text, in: room)
                                    isEditing = false
                                }
                                isFieldFocused = false
                            } label: {
                                Text(Copy.answer)
                                    .font(RibbonType.uiMedium(14))
                                    .foregroundStyle(typed ? Palette.chartreuse : Palette.muted)
                                    .frame(minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .disabled(!typed)
                            if isEditing {
                                QuietControl(title: Copy.keepWhatIHad) {
                                    answerDraft = myAnswer ?? ""
                                    withAnimation(RibbonMotion.arrive) { isEditing = false }
                                    isFieldFocused = false
                                }
                            }
                            Spacer()
                        }
                        .transition(.opacity)
                    }
                }
                .animation(RibbonMotion.arrive, value: typed || isEditing)
                if !isEditing {
                    Text(Copy.cardOpensWhenEveryoneHasAnswered)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                }
            }

            HStack {
                Spacer()
                // Any member may set a sealed card down for the room; it
                // leaves without ceremony — fading, so the page closes up
                // behind it instead of jumping.
                QuietControl(title: Copy.setItDown) {
                    withAnimation(RibbonMotion.settle) { model.setDownCard(card) }
                }
            }
        }
    }

    // MARK: - S09: Open

    @ViewBuilder
    private var openContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            HairlineRule()
            ForEach(model.members(of: room), id: \.personID) { member in
                if let answer = card.answers[member.personID] {
                    let person = model.person(member.personID)
                    let ink = member.ink ?? .ochre
                    let name = firstName(person?.name ?? Copy.someone)
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            PortraitView(
                                person: person, ink: ink, size: 22,
                                image: person.flatMap { model.portrait($0.id) })
                            SmallCaps(name, size: 12, color: ink.color)
                            Spacer()
                        }
                        Text(answer)
                            .font(RibbonType.ui(16))
                            .foregroundStyle(Palette.text)
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.vertical, 4)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Copy.answerFrom(name, answer))
                }
            }
        }
    }
}

/// The card, turning over (S08 → S09): 480 ms about its upright, no
/// overshoot. Which face shows is decided by the angle the card has actually
/// reached — the animated value, not where it is going — so the sealed face
/// turns away, the card goes edge-on, and the open face turns in the right
/// way round. Deciding it from the destination swapped the faces on the
/// first frame, and the first half of every turn showed the open card
/// mirrored.
private struct CardTurn<Sealed: View, Opened: View>: View, Animatable {
    var turn: CGFloat
    let sealed: Sealed
    let opened: Opened

    var animatableData: CGFloat {
        get { turn }
        set { turn = newValue }
    }

    var body: some View {
        ZStack {
            if turn < 0.5 {
                sealed
                    .transition(.identity)
            } else {
                // Drawn already turned the other way, so that the words
                // come out the right way round.
                opened
                    .rotation3DEffect(.degrees(180), axis: (x: 0, y: 1, z: 0))
                    .transition(.identity)
            }
        }
        .rotation3DEffect(.degrees(Double(turn) * 180), axis: (x: 0, y: 1, z: 0), perspective: 0.6)
    }
}
