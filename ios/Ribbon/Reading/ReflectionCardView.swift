import SwiftUI
import RibbonCore

/// Reflection Card (S08 / S09):
/// A question everyone answers before anyone reads the answers.
///
/// Sealed:
/// - Question in Literata, generously set
/// - Answer field (or answered view with tap to edit)
/// - "This opens when everyone has answered."
/// - "set it down" button (small caps, low contrast)
/// - Rules: never names who hasn't answered, never shows count
///
/// Open:
/// - 480 ms slow ease-out turn
/// - Every answer with author's portrait, first name, and ink color
/// - No timestamps, no reactions, no replies
struct ReflectionCardView: View {
    @Environment(AppModel.self) private var model
    let card: ReflectionCard
    let reading: Reading
    let room: Room

    @State private var answerDraft: String = ""
    @State private var isEditing = false
    @FocusState private var isFieldFocused: Bool

    private var myAnswer: String? {
        guard let me = model.me else { return nil }
        return card.answers[me.id]
    }

    private var myMembership: Membership? {
        model.myMembership(in: room)
    }

    private var myInk: Ink {
        myMembership?.ink ?? .ochre
    }

    var body: some View {
        if card.state == .setDown {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 18) {
                // The question in Literata (§4.6, S08/S09)
                Text(card.question)
                    .font(RibbonType.scripture(19))
                    .foregroundStyle(Palette.text)
                    .lineSpacing(5)
                    .fixedSize(horizontal: false, vertical: true)

                if card.state == .open {
                    openContent
                        .transition(.asymmetric(
                            insertion: .opacity.combined(with: .scale(scale: 0.98)),
                            removal: .opacity
                        ))
                } else {
                    sealedContent
                }
            }
            .padding(22)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Palette.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Palette.rule, lineWidth: 1)
                    )
            )
            .animation(.easeOut(duration: 0.48), value: card.state)
            .onAppear {
                if let myAnswer {
                    answerDraft = myAnswer
                }
            }
        }
    }

    // MARK: - S08: Sealed Content

    @ViewBuilder
    private var sealedContent: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let myAnswer, !isEditing {
                // Answered state
                VStack(alignment: .leading, spacing: 8) {
                    Text(myAnswer)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(myInk.color)
                        .lineSpacing(4)
                        .fixedSize(horizontal: false, vertical: true)

                    Button {
                        answerDraft = myAnswer
                        isEditing = true
                        isFieldFocused = true
                    } label: {
                        SmallCaps("Edit your answer", size: 12)
                            .foregroundStyle(Palette.muted)
                    }
                    .buttonStyle(.plain)
                }

                // The quiet waiting line (§4.6, S08):
                // Never names who hasn't answered · never shows how many have
                Text("This opens when everyone has answered.")
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .padding(.top, 4)
            } else {
                // Unanswered (or editing) state
                VStack(alignment: .leading, spacing: 12) {
                    TextField("", text: $answerDraft, prompt: Text("Your thoughts...").foregroundColor(Palette.muted.opacity(0.5)), axis: .vertical)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                        .lineLimit(3...8)
                        .focused($isFieldFocused)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Palette.ground.opacity(0.5))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 6)
                                        .stroke(Palette.rule, lineWidth: 1)
                                )
                        )

                    HStack {
                        Button {
                            let text = answerDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !text.isEmpty else { return }
                            model.answerCard(card, answer: text, in: room)
                            isEditing = false
                            isFieldFocused = false
                        } label: {
                            Text("Answer")
                                .font(RibbonType.uiMedium(15))
                                .foregroundStyle(Palette.chartreuse)
                                .padding(.vertical, 6)
                                .padding(.horizontal, 14)
                                .background(
                                    Capsule()
                                        .stroke(Palette.chartreuse.opacity(0.4), lineWidth: 1)
                                )
                        }
                        .buttonStyle(.plain)
                        .disabled(answerDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                        if isEditing {
                            Button {
                                answerDraft = myAnswer ?? ""
                                isEditing = false
                                isFieldFocused = false
                            } label: {
                                Text("Cancel")
                                    .font(RibbonType.ui(15))
                                    .foregroundStyle(Palette.muted)
                                    .padding(.leading, 8)
                            }
                            .buttonStyle(.plain)
                        }

                        Spacer()
                    }
                }
            }

            // Set it down action (S08)
            HStack {
                Spacer()
                Button {
                    model.setDownCard(card)
                } label: {
                    SmallCaps("set it down", size: 12)
                        .foregroundStyle(Palette.muted.opacity(0.6))
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 6)
        }
    }

    // MARK: - S09: Open Content

    @ViewBuilder
    private var openContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            HairlineRule()

            let members = model.members(of: room)
            ForEach(members, id: \.personID) { member in
                if let answer = card.answers[member.personID] {
                    let person = model.person(member.personID)
                    let ink = member.ink ?? .ochre
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            PortraitView(
                                person: person,
                                ink: ink,
                                size: 22,
                                image: person.flatMap { model.portrait($0.id) }
                            )
                            SmallCaps(person?.name.split(separator: " ").first.map(String.init) ?? "Reader", size: 12)
                                .foregroundStyle(ink.color)
                            Spacer()
                        }

                        Text(answer)
                            .font(RibbonType.ui(16))
                            .foregroundStyle(ink.color)
                            .lineSpacing(4)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }
}
