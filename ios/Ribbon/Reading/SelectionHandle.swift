import SwiftUI

// The two ends of a lifted range (A41g): a knob in the accent under the
// first and last letters, dragged to a word's edge. Drawn at 10 points and
// taken at 44. A finger that cannot drag has the same reach as one that
// can: a verse either way, and a word either way (§11).

struct SelectionHandle: View {
    var label: String
    /// A point in the chapter's own coordinate space.
    var onDrag: (CGPoint) -> Void
    var onVerse: (_ forward: Bool) -> Void
    var onWord: (_ forward: Bool) -> Void

    static let knob: CGFloat = 10
    static let target: CGFloat = 44

    var body: some View {
        ZStack {
            Circle()
                .fill(Palette.chartreuse)
                .frame(width: Self.knob, height: Self.knob)
            Circle()
                .strokeBorder(Palette.ground, lineWidth: 1.5)
                .frame(width: Self.knob + 3, height: Self.knob + 3)
        }
        .frame(width: Self.target, height: Self.target)
        .contentShape(Circle())
        .highPriorityGesture(
            DragGesture(minimumDistance: 2, coordinateSpace: .named("chapter"))
                .onChanged { value in onDrag(value.location) })
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityAddTraits(.adjustable)
        .accessibilityAction(named: Copy.aVerseFurtherOn) { onVerse(true) }
        .accessibilityAction(named: Copy.aVerseBack) { onVerse(false) }
        .accessibilityAction(named: Copy.aWordFurtherOn) { onWord(true) }
        .accessibilityAction(named: Copy.aWordBack) { onWord(false) }
        .accessibilityAdjustableAction { direction in
            onWord(direction == .increment)
        }
    }
}
