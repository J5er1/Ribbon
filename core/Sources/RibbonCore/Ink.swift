import Foundation

/// The eight inks — highlight and identity colors (brand brief §8, unchanged by
/// the build book). Eight is deliberate: enough for a room of five to be
/// distinct with real choice left over, while staying separable at a 6pt dot.
///
/// Chartreuse is not among them and never becomes selectable: it is the
/// brand's, not the user's.
public enum Ink: String, Codable, CaseIterable, Hashable, Sendable {
    case crimson
    case clay
    case ochre
    case moss
    case teal
    case indigo
    case plum
    case rose

    /// Hex value against the dark (primary) ground.
    public var darkHex: String {
        switch self {
        case .crimson: return "C9584E"
        case .clay: return "C87A46"
        case .ochre: return "DCA846"
        case .moss: return "8AA77B"
        case .teal: return "63A09A"
        case .indigo: return "7297CE"
        case .plum: return "B3849E"
        case .rose: return "CE6B84"
        }
    }

    /// Hex value against the light (paper) ground. The light palette is an
    /// open question (build book §16.1); these are the brief's values, kept
    /// so nothing has to be invented later.
    public var lightHex: String {
        switch self {
        case .crimson: return "A2332C"
        case .clay: return "9A5430"
        case .ochre: return "8C6412"
        case .moss: return "4F6B45"
        case .teal: return "2F6360"
        case .indigo: return "3A578A"
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

    /// A person's ink where they have none yet — a room of two draws
    /// from the whole palette, so a membership carries no ink there (§4.5),
    /// but their monogram and their marks in the gutter still want a
    /// color that is theirs (§03: "a monogram in their ink"). Derived from
    /// the id, so it is the same on every device and never changes under
    /// them; never written to the membership.
    public static func stable(for id: UUID) -> Ink {
        let bytes = id.uuid
        var hash: UInt32 = 2166136261
        for byte in [bytes.0, bytes.1, bytes.2, bytes.3, bytes.4, bytes.5, bytes.6, bytes.7,
                     bytes.8, bytes.9, bytes.10, bytes.11, bytes.12, bytes.13, bytes.14, bytes.15] {
            hash = (hash ^ UInt32(byte)) &* 16777619
        }
        return allCases[Int(hash % UInt32(allCases.count))]
    }
}
