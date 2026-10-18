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

    /// Whose question the room's own card is (A75), in their ink: said in
    /// the open, as a banked fire is.
    private var asker: (line: String, ink: Ink)? {
        guard card.byTheRoom else { return nil }
        if let writer = card.writtenBy, writer == model.me?.id {
            return (Copy.yourQuestion, myInk)
        }
        let member = card.writtenBy.flatMap { writer in model.members(of: room).first { $0.personID == writer } }
        let name = firstName(card.writtenBy.flatMap { model.person($0)?.name } ?? Copy.someone)
        return (Copy.questionFrom(name), member?.ink ?? .ochre)
    }

    private func face(open: Bool) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            if let asker {
                SmallCaps(asker.line, size: 12, color: asker.ink.color)
                    .accessibilityHidden(true)
            }
            Text(card.question)
                .font(RibbonType.scripture(19))
                .foregroundStyle(Palette.text)
                .lineSpacing(5)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .accessibilityLabel(
                    (asker.map { "\($0.line). " } ?? "")
                        + (open ? Copy.cardOpenSpoken(card.question) : Copy.cardSealedSpoken(card.question)))
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

/// Asking the room its own question for a chapter (A75): a quiet control
/// under the cards that opens into a question set as a card's is — your
/// name over it, a hairline under it — with "Ask" and "Never mind". Once
/// asked, it is a card like the others, and the control is gone until it is
/// set down.
struct AskTheRoomView: View {
    @Environment(AppModel.self) private var model
    let reading: Reading
    let chapter: Int
    let room: Room

    @State private var asking = false
    @State private var draft = ""
    @State private var sending = false
    @State private var line: String?
    @FocusState private var focused: Bool

    private var question: String? { RoomQuestion.cleaned(draft) }

    private var myInk: Ink {
        model.myMembership(in: room)?.ink ?? model.lastUsedInk
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if asking {
                composer
                    .transition(.opacity)
            } else if model.roomMayAsk(reading, chapter: chapter) {
                QuietControl(title: Copy.askTheRoom) {
                    line = nil
                    withAnimation(RibbonMotion.arrive) { asking = true }
                    focused = true
                }
                .transition(.opacity)
            }
            if let line {
                Text(line)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var composer: some View {
        VStack(alignment: .leading, spacing: 14) {
            SmallCaps(Copy.yourQuestion, size: 12, color: myInk.color)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) {
                TextField("", text: $draft, axis: .vertical)
                    .font(RibbonType.scripture(19))
                    .foregroundStyle(Palette.text)
                    .tint(myInk.color)
                    .lineLimit(1...5)
                    .focused($focused)
                    .accessibilityLabel(Copy.askTheRoomField)
                    .onChange(of: draft) { _, typed in
                        if typed.count > RoomQuestion.maxLength {
                            draft = String(typed.prefix(RoomQuestion.maxLength))
                        }
                    }
                HairlineRule()
            }
            HStack(spacing: 18) {
                Button(action: ask) {
                    Text(Copy.ask)
                        .font(RibbonType.uiMedium(14))
                        .foregroundStyle(question != nil && !sending ? Palette.chartreuse : Palette.muted)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(question == nil || sending)
                QuietControl(title: Copy.neverMind) {
                    withAnimation(RibbonMotion.arrive) {
                        asking = false
                        draft = ""
                    }
                    focused = false
                }
                Spacer()
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .paper(.card)
    }

    private func ask() {
        guard let question else { return }
        sending = true
        Task {
            do {
                let outcome = try await model.askTheRoom(question, reading: reading, chapter: chapter)
                withAnimation(RibbonMotion.arrive) {
                    asking = false
                    draft = ""
                    line = outcome == .someoneAskedFirst ? Copy.someoneAskedFirst : nil
                }
                focused = false
            } catch {
                withAnimation(RibbonMotion.arrive) { line = Copy.serverUnreachable }
            }
            sending = false
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
