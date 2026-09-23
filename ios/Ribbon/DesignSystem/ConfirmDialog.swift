import SwiftUI

// A question with its answers (ledger A35). Ribbon's own, not the system's:
// the words §6.8 and §6.1 specify are Ribbon's, and a system sheet can only
// carry the platform's. One card on paper over the dimmed room, a question
// in the interface face, and the answers as quiet rows under a hairline.
// A question that changes — "Leave this room?" becoming "Leave your notes
// behind?" — cross-fades in place rather than stacking a second sheet.

struct ConfirmChoice: Identifiable {
    var id: String { title }
    var title: String
    var destructive = false
    var action: () -> Void

    init(_ title: String, destructive: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.destructive = destructive
        self.action = action
    }
}

struct ConfirmState: Equatable {
    var question: String
    var choices: [ConfirmChoice]

    static func == (lhs: ConfirmState, rhs: ConfirmState) -> Bool {
        lhs.question == rhs.question && lhs.choices.map(\.title) == rhs.choices.map(\.title)
    }
}

extension View {
    /// Present the question when the state is non-nil. Tapping outside, or
    /// the quiet way out, sets it to nil.
    func confirm(_ state: Binding<ConfirmState?>, dismissTitle: String = Copy.stay) -> some View {
        modifier(ConfirmModifier(state: state, dismissTitle: dismissTitle))
    }
}

private struct ConfirmModifier: ViewModifier {
    @Binding var state: ConfirmState?
    var dismissTitle: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The last state, held so the card can leave still reading it.
    @State private var held: ConfirmState?

    func body(content: Content) -> some View {
        content
            .overlay {
                ZStack {
                    if state != nil, let shown = state ?? held {
                        Palette.ground.opacity(0.6)
                            .ignoresSafeArea()
                            .onTapGesture { state = nil }
                            .accessibilityHidden(true)
                        ConfirmCard(state: shown, dismissTitle: dismissTitle) { choice in
                            state = nil
                            choice?.action()
                        }
                        .padding(.horizontal, 28)
                        .readableColumn(maxWidth: 420)
                        // A screen reader stays on the question until it
                        // is answered, as a finger does.
                        .accessibilityAddTraits(.isModal)
                        // The card comes up out of the room a breath; under
                        // reduce motion it only fades, and the question
                        // still cross-fades in place (§11).
                        .transition(reduceMotion
                            ? AnyTransition.opacity
                            : AnyTransition.opacity.combined(with: .scale(scale: 0.97)))
                    }
                }
                .animation(RibbonMotion.arrive, value: state == nil)
                .animation(RibbonMotion.settle, value: state?.question)
            }
            .onChange(of: state, initial: true) { _, now in
                if let now { held = now }
            }
    }
}

private struct ConfirmCard: View {
    let state: ConfirmState
    let dismissTitle: String
    var onChoose: (ConfirmChoice?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(state.question)
                .font(RibbonType.ui(18))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, RibbonShape.textInset)
                .padding(.top, 24)
                .padding(.bottom, 18)
                .contentTransition(.opacity)
                .accessibilityAddTraits(.isHeader)
            HairlineRule()
            ForEach(state.choices) { choice in
                Button { onChoose(choice) } label: {
                    Text(choice.title)
                        .font(RibbonType.uiMedium(17))
                        .foregroundStyle(choice.destructive ? Palette.text : Palette.chartreuse)
                        .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                        .padding(.horizontal, RibbonShape.textInset)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.pressable)
                HairlineRule()
            }
            Button { onChoose(nil) } label: {
                SmallCaps(dismissTitle, size: 13)
                    .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                    .padding(.horizontal, RibbonShape.textInset)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.pressable)
        }
        .paper(.card)
    }
}
