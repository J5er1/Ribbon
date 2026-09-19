import Foundation
import Testing
@testable import RibbonCore

// The three shapes the iOS polish pass added to the model (ledger A30, A41g,
// A42) and what an older state file says when it meets them.

@Suite struct VerseRangeTests {
    @Test func aDragMadeUpwardsIsStoredAsOneMadeDownwards() {
        let r = VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 3, startChar: 12, endChar: 4)
        #expect(r.startVerse == 3)
        #expect(r.endVerse == 9)
        // The offsets belong to their ends and turn over with them.
        #expect(r.startChar == 4)
        #expect(r.endChar == 12)
    }

    @Test func offsetsInOneVerseAreOrdered() {
        let r = VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 9, startChar: 30, endChar: 10)
        #expect(r.startChar == 10)
        #expect(r.endChar == 30)
        #expect(!r.isWholeVerses)
    }

    @Test func offsetsMeanNothingInAnotherVersion() {
        let r = VerseRange(bookID: "MRK", chapter: 4, startVerse: 9, endVerse: 9, startChar: 10, endChar: 30, charTranslation: .bsb)
        let mine = r.chars(in: .bsb)
        #expect(mine.start == 10 && mine.end == 30)
        let theirs = r.chars(in: .web)
        #expect(theirs.start == nil && theirs.end == nil)
    }

    @Test func aWholeVerseRangeDecodesWithoutOffsets() throws {
        let json = #"{"bookID":"MRK","chapter":4,"startVerse":9,"endVerse":11}"#.data(using: .utf8)!
        let r = try JSONDecoder().decode(VerseRange.self, from: json)
        #expect(r.isWholeVerses)
        #expect(r.charTranslation == nil)
        let again = try JSONDecoder().decode(VerseRange.self, from: JSONEncoder().encode(r))
        #expect(again == r)
    }
}

@Suite struct RoomVersionTests {
    @Test func aRoomSavedBeforeVersionsReadsTheDefault() throws {
        let json = #"{"id":"6F9619FF-8B86-D011-B42D-00C04FC964FF","name":"Us","createdAt":0,"isPaused":false}"#.data(using: .utf8)!
        let room = try JSONDecoder().decode(Room.self, from: json)
        #expect(room.translation == .bsb)
        #expect(room.name == "Us")
    }

    @Test func aReadingSavedBeforeVersionsReadsTheDefault() throws {
        let reading = Reading(roomID: UUID(), bookID: "MRK", startedAt: Date(timeIntervalSince1970: 0), handiwork: Handiwork(scale: .medium))
        var object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(reading)) as! [String: Any]
        object.removeValue(forKey: "translation")
        let data = try JSONSerialization.data(withJSONObject: object)
        let back = try JSONDecoder().decode(Reading.self, from: data)
        #expect(back.translation == .bsb)
        #expect(back.bookID == "MRK")
    }

    @Test func theRoomsVersionRoundTrips() throws {
        let room = Room(name: nil, createdAt: Date(timeIntervalSince1970: 10), translation: .web)
        let back = try JSONDecoder().decode(Room.self, from: JSONEncoder().encode(room))
        #expect(back == room)
    }
}

@Suite struct RibbonTests {
    @Test func theRibbonRoundTrips() throws {
        let ribbon = Ribbon(readingID: UUID(), personID: UUID(), chapter: 4, verse: 9, placedAt: Date(timeIntervalSince1970: 100))
        let back = try JSONDecoder().decode(Ribbon.self, from: JSONEncoder().encode(ribbon))
        #expect(back == ribbon)
    }
}
