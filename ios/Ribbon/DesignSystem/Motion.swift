import SwiftUI

// Motion tokens (build book §9.1). Everything breathes rather than snaps:
// gentle ease-out, no bounce, no spring overshoot, no parallax. One thing
// in the product is allowed to be fast — the thinking-of-you haptic.
//
// The split with system materials (§12.1): glass may morph with system
// spring physics — it is a material behaving as a material. Everything
// Ribbon draws obeys these tokens.
//
// The division of labour between the two halves of this object: a thing
// that simply *changes* gets a curve — a word swapping under the fire, a
// cross-dissolve between rooms, a note unfurling. A thing a finger is
// *holding* gets a spring, because a spring can be handed the velocity the
// finger let go at and a curve cannot. Every spring here is critically
// damped — the fastest approach that never crosses the mark — which is how
// the product gets physical motion without the overshoot §9.1 forbids.
//
// Every token comes in two: the motion, and the same thing held still for
// reduce motion (§11). A screen asks for `RibbonMotion.settle(still:)`
// rather than writing the branch out, so that "under reduce motion this is
// a cut" is decided in one place instead of ten.
//
// `still:` is for movement — a slide, a swell, a turn, a pill travelling.
// A change that is nothing but opacity or colour asks for the plain token
// and keeps its curve under reduce motion, because a cross-fade is what
// §11 turns movement *into* ("morphs become cross-fades"), not a movement
// to take away. Held still, a fade is a cut, and a snap is the one thing
// §9.1 asks the room never to do.

enum RibbonMotion {
    /// Presence appearing, sheets, cross-fades between rooms.
    static let arrive = Animation.easeOut(duration: arriveDuration)
    /// Notes unfurling, the book closing, screen pushes.
    static let settle = Animation.easeOut(duration: settleDuration)
    /// Cards turning, the presence panel expanding.
    static let open = Animation.easeOut(duration: openDuration)
    /// A gesture let go of halfway: back where it was. Quicker than
    /// `arrive`, because nothing is arriving.
    static let release = Animation.easeOut(duration: releaseDuration)
    /// Fire → ember, once per book.
    static let become = Animation.easeInOut(duration: becomeDuration)
    /// The fire's breath — used as a period, not an Animation: the flicker
    /// is drawn, never synchronised across elements.
    static let flickerPeriod: Double = 3.4
    /// The thinking-of-you fill.
    static let inkFill = Animation.easeInOut(duration: inkFillDuration)

    static let arriveDuration: Double = 0.32
    static let settleDuration: Double = 0.40
    static let openDuration: Double = 0.48
    static let releaseDuration: Double = 0.22
    static let becomeDuration: Double = 2.5
    static let inkFillDuration: Double = 0.7

    static func arrive(still: Bool) -> Animation? { still ? nil : arrive }
    static func settle(still: Bool) -> Animation? { still ? nil : settle }
    static func open(still: Bool) -> Animation? { still ? nil : open }
    static func release(still: Bool) -> Animation? { still ? nil : release }
    static func become(still: Bool) -> Animation? { still ? nil : become }
    static func inkFill(still: Bool) -> Animation? { still ? nil : inkFill }

    // MARK: Springs — for the things a finger holds

    /// A thing the size of the screen: the book rising, the menu arriving.
    /// Low stiffness, because a big object is slow.
    static let cover = Animation.interpolatingSpring(mass: 1, stiffness: 180, damping: 2 * (180.0).squareRoot())
    /// A thing the size of a hand: a card lifting, the hearth swelling
    /// under a press, a row settling into place.
    static let handled = Animation.interpolatingSpring(mass: 1, stiffness: 400, damping: 2 * (400.0).squareRoot())
    /// A thing the size of a fingertip: a switch, a dot moving down a list,
    /// a tile taking a press. Quick enough to feel like a direct response.
    static let touched = Animation.interpolatingSpring(mass: 1, stiffness: 1500, damping: 2 * (1500.0).squareRoot())

    static func cover(still: Bool) -> Animation? { still ? nil : cover }
    static func handled(still: Bool) -> Animation? { still ? nil : handled }
    static func touched(still: Bool) -> Animation? { still ? nil : touched }

    /// The same springs, handed the speed the finger let go at. `rate` is
    /// how fast the value was moving, in its own units per second; `distance`
    /// is how far it still has to go — the target minus where it is now.
    static func cover(rate: CGFloat, towards distance: CGFloat) -> Animation {
        spring(stiffness: 180, handed: rate, over: distance)
    }
    static func handled(rate: CGFloat, towards distance: CGFloat) -> Animation {
        spring(stiffness: 400, handed: rate, over: distance)
    }

    /// A critically damped spring, set off at the finger's speed.
    ///
    /// SwiftUI wants that speed relative to the distance still to travel,
    /// and positive towards the mark — which is `rate / distance`, and why a
    /// raw points-per-second figure handed straight in sends the thing the
    /// wrong way at the wrong speed. And capped: a critically damped spring
    /// stays on its own side of the mark only while it sets off slower than
    /// its natural frequency (√stiffness) per unit of distance. A hard flick
    /// would carry it through — the overshoot §9.1 forbids, arriving by way
    /// of the one kind of animation built never to have it.
    private static func spring(stiffness: Double, handed rate: CGFloat, over distance: CGFloat) -> Animation {
        let frequency = stiffness.squareRoot()
        let relative = abs(distance) < 0.001 ? 0 : Double(rate / distance)
        let limit = frequency * 0.85
        return .interpolatingSpring(
            mass: 1, stiffness: stiffness, damping: 2 * frequency,
            initialVelocity: min(limit, max(-limit, relative)))
    }

    // MARK: The hearth gesture (S01 → S02, ledger A48)

    /// How far up the fire has to be pulled before letting go opens the
    /// book: a fifth of the travel. Deliberately short — the gesture's job
    /// is to *start* the opening, not to perform all of it.
    static let openCommit: CGFloat = 0.19
    /// The flick that opens the book however far it got: points per second,
    /// upward. A fast short flick is somebody who knows the gesture.
    static let openFling: CGFloat = 480
    /// How much of the room's height one full pull is worth.
    static let openTravel: CGFloat = 0.55
}
