import Foundation

// Cards the room writes (build book §4.6, §16.4; A75). Ribbon writes one
// card a chapter; anyone in the room may ask one more, of their own, beside
// it. The server opens a card when the last answer lands
// (20261019120000_ribbon_cards_the_room_writes.sql). The Kotlin port
// (Cards.kt) matches this case for case; CardsTests and CardsTest hold the
// two to the same answers.

/// The cards at the end of a chapter.
public enum ChapterCards {
    /// Ribbon's card for a chapter has the same id on every phone, made
    /// from the reading's and the chapter's, so two people reaching the end
    /// of a chapter at once make one card and not two whose answers never
    /// meet. The reading's id with the chapter folded into its last four
    /// bytes, marked as a version 8 (custom) UUID.
    public static func ribbonCardID(readingID: UUID, chapter: Int) -> UUID {
        var bytes = withUnsafeBytes(of: readingID.uuid) { Array($0) }
        let folded = UInt32(truncatingIfNeeded: chapter)
        bytes[12] ^= UInt8(truncatingIfNeeded: folded >> 24)
        bytes[13] ^= UInt8(truncatingIfNeeded: folded >> 16)
        bytes[14] ^= UInt8(truncatingIfNeeded: folded >> 8)
        bytes[15] ^= UInt8(truncatingIfNeeded: folded)
        bytes[6] = (bytes[6] & 0x0F) | 0x80
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
    }

    /// The cards standing at a chapter's end, in the order they sit:
    /// Ribbon's, then the room's. A card set down is gone.
    public static func standing(_ cards: [ReflectionCard], readingID: UUID, chapter: Int) -> [ReflectionCard] {
        let here = cards.filter { $0.readingID == readingID && $0.chapter == chapter && $0.state != .setDown }
        let ribbon = here.filter { !$0.byTheRoom }
        let room = here.filter(\.byTheRoom).sorted { $0.id.uuidString < $1.id.uuidString }
        return ribbon + room
    }

    /// A card's state as the server spells it. The phones wrote "setDown",
    /// which the table refuses (it says "set_down"), so a card set down was
    /// never set down for anyone else.
    public static func stored(_ state: CardState) -> String {
        state == .setDown ? "set_down" : state.rawValue
    }

    /// A state read from the server, or nil for one this phone does not know.
    public static func state(stored: String) -> CardState? {
        stored == "set_down" ? .setDown : CardState(rawValue: stored)
    }

    /// Whether someone may ask the room's own question here: while no
    /// question of the room's stands at this chapter.
    public static func roomMayAsk(_ cards: [ReflectionCard], readingID: UUID, chapter: Int) -> Bool {
        !standing(cards, readingID: readingID, chapter: chapter).contains(where: \.byTheRoom)
    }
}

/// A question someone asks the room.
public enum RoomQuestion {
    /// A sentence or two. The server holds 280; the field stops well short.
    public static let maxLength = 200

    /// What was typed, as a question: its spaces and line breaks run
    /// together, trimmed. Nil when there is nothing, or too much.
    public static func cleaned(_ text: String) -> String? {
        let words = text.split(whereSeparator: { $0.isWhitespace || $0.isNewline }).joined(separator: " ")
        if words.isEmpty || words.count > maxLength { return nil }
        return words
    }
}
