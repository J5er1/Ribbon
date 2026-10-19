import ObjectiveC
import SwiftUI
import UIKit

// How one screen becomes the next on the iPhone (I45).
//
// A22 made Android flow rather than cut, and A16 wrote down that the iPhone
// "gets it from a zoom navigation transition tied to the tapped view". It
// never did. Every push here was the system's slide from the right: a seat
// tapped at the hearth and a page about that person coming in from the side,
// an ember tapped on the shelf and its record arriving from somewhere else.
// Both moved, and neither continued.
//
// So the two things A22 carries are carried here, the iPhone's way: the
// screen grows out of the thing that was tapped, and goes back into it. A
// face at the hearth opens into that person's page; an ember opens into its
// own record. It is the system's zoom, which §12.1's split allows — a
// navigation morph is the system behaving as the system — and it brings the
// way back with it: a drag down, or a pinch, puts the page back where it
// came from. Android carries the face alone, as a shared element; the
// iPhone grows the whole page from it, which is the platform's idiom for
// the same sentence.
//
// The settings rows' words, A22's third flow, do not travel here. A row
// growing into a whole settings screen is a heavier movement than a heading
// rising out of a row, and the zoom is the only cross-push morph the system
// offers.
//
// Under reduce motion nothing grows: the push is the push it was (§11).
//
// And the way back by the edge. Every pushed page draws its own bar and
// hides the system's (A29), and a navigation controller with its bar hidden
// turns its edge swipe off. So no page the room or the menu pushed could be
// swiped back from — only tapped back from, by a chevron in its corner.
// `EdgeSwipeBack` turns it on again.

/// The names of the things that travel, once, so a key cannot be spelt two
/// ways in two files (A22's `Flows`): a key that does not match is not an
/// error, it is a screen that silently stops growing out of anything.
enum Flow: Hashable {
    /// A seat at the hearth, and that person's own page. Keyed by the room
    /// too, because you have a seat in every room you are in.
    case seat(room: UUID, person: UUID)
    /// A finished reading's ember: on a shelf, and on its own record.
    case ember(UUID)
    /// A room's jar, and the page of what it holds (I46).
    case jar(UUID)
}

private struct FlowSpaceKey: EnvironmentKey {
    static let defaultValue: Namespace.ID? = nil
}

extension EnvironmentValues {
    /// The namespace a navigation stack's sources and destinations share.
    /// Nil outside a stack — a preview, a sheet — where a source simply
    /// draws and a destination simply pushes.
    var flowSpace: Namespace.ID? {
        get { self[FlowSpaceKey.self] }
        set { self[FlowSpaceKey.self] = newValue }
    }
}

extension View {
    /// The screen this opens grows out of here, and goes back into it.
    /// `cornerRadius` is the shape the page starts from: half a face's
    /// width for a face, so it opens out of a circle.
    func flowSource(_ flow: Flow, cornerRadius: CGFloat = 0) -> some View {
        modifier(FlowSource(flow: flow, cornerRadius: cornerRadius))
    }

    /// This screen grows out of `flow`'s source. Nil is a push like any
    /// other: a page opened from somewhere with nothing to grow from.
    func flows(from flow: Flow?) -> some View {
        modifier(FlowDestination(flow: flow))
    }
}

private struct FlowSource: ViewModifier {
    @Environment(\.flowSpace) private var space
    let flow: Flow
    let cornerRadius: CGFloat

    func body(content: Content) -> some View {
        if let space {
            // No background of its own: the ground under a source is
            // grained, and a flat one stood behind an ember as a dark
            // square for the moment it was taken.
            content.matchedTransitionSource(id: flow, in: space) { source in
                source.clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            }
        } else {
            content
        }
    }
}

private struct FlowDestination: ViewModifier {
    @Environment(\.flowSpace) private var space
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let flow: Flow?

    func body(content: Content) -> some View {
        if let space, let flow, !reduceMotion {
            content.navigationTransition(.zoom(sourceID: flow, in: space))
        } else {
            content
        }
    }
}

// MARK: - The way back by the edge

/// Turns the edge swipe back on for the navigation stack this is drawn in.
/// Placed once, behind a stack's first screen; it draws nothing.
struct EdgeSwipeBack: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Probe { Probe() }
    func updateUIViewController(_ controller: Probe, context: Context) {}

    /// Finds the stack's navigation controller once it is in one, which is
    /// not yet true when it is made.
    final class Probe: UIViewController {
        private let gate = Gate()

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            gate.watch(navigationController)
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            gate.watch(navigationController)
        }
    }

    /// The edge swipe's delegate in place of the system's, which refuses
    /// every swipe while the bar is hidden. It lets one begin wherever there
    /// is a page to go back to and nothing is already moving: never on a
    /// stack's first screen, where a swipe back would take hold of nothing
    /// and leave the stack unable to take a tap.
    final class Gate: NSObject, UIGestureRecognizerDelegate {
        private weak var navigation: UINavigationController?
        private static var held = 0

        func watch(_ navigation: UINavigationController?) {
            guard let navigation, navigation !== self.navigation,
                  let swipe = navigation.interactivePopGestureRecognizer
            else { return }
            self.navigation = navigation
            // A gesture holds its delegate weakly. Held by the controller,
            // the gate lives exactly as long as the swipe it answers for:
            // one that went first would leave a swipe with no delegate,
            // free to begin on a stack's first screen.
            objc_setAssociatedObject(navigation, &Gate.held, self, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            swipe.delegate = self
            swipe.isEnabled = true
        }

        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            guard let navigation else { return false }
            return navigation.viewControllers.count > 1 && navigation.transitionCoordinator == nil
        }
    }
}
