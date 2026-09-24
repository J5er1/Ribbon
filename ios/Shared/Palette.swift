import SwiftUI
import RibbonCore

// The room's palette: chartreuse on true black with ivory (build book §00 —
// this supersedes the brief's crimson-and-lamp scheme). Dark is the hero
// condition; there is no light mode at launch (S18 — a light mode arrives
// only when the paper palette is resolved, open question §16.1).
//
// Chartreuse is chrome: hairlines, the follow thread, focus. The fire stays
// warm — it is "a warm object" and an amber thing in a chartreuse-accented
// room, never a chartreuse flame.

extension Color {
    init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.replacingOccurrences(of: "#", with: "")).scanHexInt64(&value)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: 1)
    }
}

enum Palette {
    /// The unlit ground. True black with a breath of warmth — never
    /// blue-black, which reads as a device instead of a room.
    static let ground = Color(hex: "0B0B0A")
    /// Cards and sheets.
    static let surface = Color(hex: "14120D")
    /// Bars and chips.
    static let raised = Color(hex: "1D1A13")
    /// Ivory — primary text.
    static let text = Color(hex: "F3F0E6")
    /// Metadata, small caps, quiet lines.
    static let muted = Color(hex: "8E8271")
    /// Borders and dividers.
    static let rule = Color(hex: "292118")
    /// The brand accent. Never a color a user can pick.
    static let chartreuse = Color(hex: "D6E45C")

    /// The fire's warmth — lamp and clay ambers from the brief, kept for
    /// the one object in the room that must read warm.
    static let flameBright = Color(hex: "F3C778")
    static let flameCore = Color(hex: "E9A63F")
    static let flameDeep = Color(hex: "C87A46")
    static let coal = Color(hex: "8A4A24")
    static let coalDim = Color(hex: "4A2714")

    /// The wash opacity for highlights (§4.5).
    static let highlightWash = 0.24
}

extension Ink {
    /// The ink's color against the dark ground.
    var color: Color { Color(hex: darkHex) }

    var uiColor: UIColor { UIColor(color) }
}
