import Foundation

/// The twelve inks — highlight and identity colors. The brief's eight (§8),
/// and four more so that a room of twelve has an ink for everyone (A70):
/// bronze, jade, sky and iris, each measured to sit at least as far from its
/// nearest neighbour as plum already sits from rose, on both grounds, so a
/// 6pt dot is still told apart. They are listed around the colour wheel,
/// which is the order every picker shows them in.
///
/// Chartreuse is not among them and never becomes selectable: it is the
/// brand's, not the user's.
public enum Ink: String, Codable, CaseIterable, Hashable, Sendable {
    case crimson
    case clay
    case ochre
    case bronze
    case moss
    case jade
    case teal
    case sky
    case indigo
    case iris
    case plum
    case rose

    /// Hex value against the dark (primary) ground.
    public var darkHex: String {
        switch self {
        case .crimson: return "C9584E"
        case .clay: return "C87A46"
        case .ochre: return "DCA846"
        case .bronze: return "98853B"
        case .moss: return "8AA77B"
        case .jade: return "52B88D"
        case .teal: return "63A09A"
        case .sky: return "21A8CE"
        case .indigo: return "7297CE"
        case .iris: return "BA95E9"
        case .plum: return "B3849E"
        case .rose: return "CE6B84"
        }
    }

    /// Hex value against the light (paper) ground. The light palette is an
    /// open question (build book §16.1); the first eight are the brief's
    /// values, kept so nothing has to be invented later, and the four added
    /// in A70 each hold 4.5:1 against the paper.
    public var lightHex: String {
        switch self {
        case .crimson: return "A2332C"
        case .clay: return "9A5430"
        case .ochre: return "8C6412"
        case .bronze: return "5E5435"
        case .moss: return "4F6B45"
        case .jade: return "0A7959"
        case .teal: return "2F6360"
        case .sky: return "27728F"
        case .indigo: return "3A578A"
        case .iris: return "805BA1"
        case .plum: return "74445D"
        case .rose: return "9E4059"
        }
    }

    /// Lowercase display name, matching the voice rules (small caps do the
    /// capitalization work in the interface, not the string).
    public var displayName: String { rawValue }

    /// The inks not yet claimed by a membership, in canonical order.
    public static func remaining(taken: some Sequence<Ink>) -> [Ink] {
        let taken = Set(taken)
        return allCases.filter { !taken.contains($0) }
    }
}
