import SwiftUI

// Motion tokens (build book §9.1). Everything breathes rather than snaps:
// gentle ease-out, no bounce, no spring overshoot, no parallax. One thing
// in the product is allowed to be fast — the thinking-of-you haptic.
//
// The split with system materials (§12.1): glass may morph with system
// spring physics — it is a material behaving as a material. Everything
// Ribbon draws obeys these tokens.

enum RibbonMotion {
    /// Presence appearing, sheets, cross-fades between rooms.
    static let arrive = Animation.easeOut(duration: 0.32)
    /// Notes unfurling, the book closing, screen pushes.
    static let settle = Animation.easeOut(duration: 0.40)
    /// Cards turning, the presence panel expanding.
    static let open = Animation.easeOut(duration: 0.48)
    /// Fire → ember, once per book.
    static let become = Animation.easeInOut(duration: 2.5)
    /// The fire's breath — used as a period, not an Animation: the flicker
    /// is drawn, never synchronised across elements.
    static let flickerPeriod: Double = 3.4
    /// The thinking-of-you fill.
    static let inkFill = Animation.easeInOut(duration: 0.7)

    static let arriveDuration: Double = 0.32
    static let settleDuration: Double = 0.40
    static let openDuration: Double = 0.48
    static let becomeDuration: Double = 2.5
}
