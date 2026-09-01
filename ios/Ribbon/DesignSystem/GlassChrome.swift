import SwiftUI

// Liquid Glass, tinted to near-invisible (build book §12.1). We use the
// real system material so we inherit the specular edge, refraction, the
// interactive response, and every accessibility fallback — then tint it so
// far toward the unlit ground that it reads as depth rather than as glass.
//
// Deliberate deviation from Apple's guidance, written down as one: Apple
// says tint should carry semantic meaning; Ribbon tints for brand. On a
// #0B0B0A ground with paper grain, what survives is the specular edge and
// a faint lensing of whatever chartreuse sits beneath — which is precisely
// the goal.
//
// Where glass never appears: over Scripture, on the fire, the shelf,
// embers, or as a screen background. There is no tab bar on any platform,
// so the most conspicuous glass slab in a typical iOS app simply doesn't
// exist here.

private struct RibbonGlass<S: InsettableShape>: ViewModifier {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    var shape: S
    var interactive: Bool

    func body(content: Content) -> some View {
        if reduceTransparency {
            // Glass becomes an opaque raised surface with the same
            // geometry. A first-class design, not a fallback — never a
            // mid-grey.
            content
                .background(Palette.raised, in: shape)
                .overlay(shape.strokeBorder(Palette.rule, lineWidth: 1))
        } else if #available(iOS 26.0, *) {
            let tinted = Glass.regular.tint(Palette.ground.opacity(0.72))
            content.glassEffect(interactive ? tinted.interactive() : tinted, in: shape)
        } else {
            content
                .background(.ultraThinMaterial, in: shape)
                .background(Palette.ground.opacity(0.72), in: shape)
        }
    }
}

extension View {
    /// Ribbon's floating chrome material: system glass tinted toward the
    /// unlit ground, legible mostly by its edge.
    func ribbonGlass(in shape: some InsettableShape = Capsule(), interactive: Bool = false) -> some View {
        modifier(RibbonGlass(shape: shape, interactive: interactive))
    }
}
