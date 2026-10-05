import XCTest
@testable import RibbonCore

// "In this room" (A62): versions grouped by what they say, yours first, and
// the words of every other rendering that are not in yours.
// RoomRenderingsTest.kt holds the Kotlin port to the same cases.
final class RoomRenderingsTests: XCTestCase {
    // The room of eleven, on four versions. Public-domain wording only: the
    // King James and American Standard are stand-ins inside the fixture.
    let kjv = TranslationID(rawValue: "kjv")
    let asv = TranslationID(rawValue: "asv")
    let nkjv = TranslationID.nkjv

    let me = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
    let ruth = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!
    let ann = UUID(uuidString: "00000000-0000-0000-0000-000000000003")!
    let ben = UUID(uuidString: "00000000-0000-0000-0000-000000000004")!
    let caleb = UUID(uuidString: "00000000-0000-0000-0000-000000000005")!
    let dana = UUID(uuidString: "00000000-0000-0000-0000-000000000006")!
    let eli = UUID(uuidString: "00000000-0000-0000-0000-000000000007")!
    let faith = UUID(uuidString: "00000000-0000-0000-0000-000000000008")!
    let gabe = UUID(uuidString: "00000000-0000-0000-0000-000000000009")!
    let hope = UUID(uuidString: "00000000-0000-0000-0000-00000000000a")!
    let isaac = UUID(uuidString: "00000000-0000-0000-0000-00000000000b")!

    let bsbShort = "Through Him"
    let webShort = "through him"
    let kjvShort = "by him"
    let asvShort = "through him"

    let bsbWhole = "Through Him all things were made, and without Him nothing was made that has been made."
    let webWhole = "All things were made through him. Without him, nothing was made that has been made."
    let kjvWhole = "All things were made by him; and without him was not any thing made that was made."
    let asvWhole = "All things were made through him; and without him was not anything made that hath been made."

    func eleven(bsb: String, web: String, kjv kjvWords: String, asv asvWords: String) -> RoomRenderings {
        RoomRenderings.of(
            yours: .init(version: .bsb, phrase: bsb, readers: [me, caleb, faith, isaac]),
            others: [
                .init(version: .web, phrase: web, readers: [ruth, eli]),
                .init(version: kjv, phrase: kjvWords, readers: [ann, ben, gabe]),
                .init(version: asv, phrase: asvWords, readers: [dana, hope]),
            ])
    }

    func r(_ start: Int, _ end: Int) -> TextRange { TextRange(start: start, end: end) }

    // MARK: The room of eleven

    func testElevenShort() {
        let room = eleven(bsb: bsbShort, web: webShort, kjv: kjvShort, asv: asvShort)
        XCTAssertEqual(room.groups, [
            .init(versions: [.bsb, .web, asv], phrase: "Through Him", differing: [],
                  readers: [me, caleb, faith, isaac, ruth, eli, dana, hope], isYours: true),
            .init(versions: [kjv], phrase: "by him", differing: [r(0, 2)],
                  readers: [ann, ben, gabe], isYours: false),
        ])
        XCTAssertNil(room.notOnThisPhone)
        XCTAssertFalse(room.allAgree)
        XCTAssertFalse(room.isOneVersion)
        XCTAssertEqual(room.groups.flatMap(\.readers).count, 11)
    }

    func testElevenWholeVerse() {
        let room = eleven(bsb: bsbWhole, web: webWhole, kjv: kjvWhole, asv: asvWhole)
        XCTAssertEqual(room.groups.map(\.versions), [[.bsb], [.web], [kjv], [asv]])
        XCTAssertEqual(room.groups.map(\.isYours), [true, false, false, false])
        XCTAssertEqual(room.groups.map(\.phrase), [bsbWhole, webWhole, kjvWhole, asvWhole])
        XCTAssertEqual(room.groups.map(\.readers), [[me, caleb, faith, isaac], [ruth, eli], [ann, ben, gabe], [dana, hope]])
        XCTAssertEqual(room.groups[0].differing, [])
        // "through him", moved to after "All things were made".
        XCTAssertEqual(room.groups[1].differing, [r(21, 28), r(29, 32)])
        // "by", "him", "not", "any", "thing", "was".
        XCTAssertEqual(room.groups[2].differing, [r(21, 23), r(24, 27), r(49, 52), r(53, 56), r(57, 62), r(73, 76)])
        // "through", "him", "not", "anything", "hath".
        XCTAssertEqual(room.groups[3].differing, [r(21, 28), r(29, 32), r(54, 57), r(58, 66), r(77, 81)])
        let marked = room.groups[2].differing.map { range -> String in
            let units = Array(kjvWhole.utf16)[range.start..<range.end]
            return String(decoding: units, as: UTF16.self)
        }
        XCTAssertEqual(marked, ["by", "him", "not", "any", "thing", "was"])
        XCTAssertFalse(room.allAgree)
    }

    // MARK: What counts as the same words

    func testCapitalsAreIgnored() {
        let room = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: "Through Him", readers: [me]),
            others: [.init(version: .web, phrase: "through him", readers: [ruth])])
        XCTAssertEqual(room.groups.count, 1)
        XCTAssertEqual(room.groups[0].versions, [.bsb, .web])
        XCTAssertEqual(room.groups[0].phrase, "Through Him")
        XCTAssertTrue(room.allAgree)
        XCTAssertEqual(RoomRenderings.differing("THROUGH HIM", from: "Through Him"), [])
    }

    func testPunctuationIsIgnored() {
        let room = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: "Through Him, all things were made.", readers: [me]),
            others: [
                .init(version: .web, phrase: "through him; all things\nwere made", readers: [ruth]),
                .init(version: asv, phrase: "“Through him — all things were made!”", readers: [dana]),
            ])
        XCTAssertEqual(room.groups.count, 1)
        XCTAssertEqual(room.groups[0].versions, [.bsb, .web, asv])
        XCTAssertTrue(room.allAgree)
        // An apostrophe is not a difference either: both say "lords".
        XCTAssertEqual(RoomRenderings.differing("the Lord’s", from: "the Lord's"), [])
    }

    func testReorderingDiffers() {
        // The same words as yours, in another order, are another rendering.
        let room = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: bsbWhole, readers: [me]),
            others: [.init(version: .web, phrase: webWhole, readers: [ruth])])
        XCTAssertEqual(room.groups.count, 2)
        XCTAssertEqual(room.groups[1].differing, [r(21, 28), r(29, 32)])
        XCTAssertFalse(room.allAgree)
        // Of two words swapped, one is on the common subsequence; the other,
        // passed over first, is the difference.
        XCTAssertEqual(RoomRenderings.differing("him through", from: "Through Him"), [r(0, 3)])
    }

    func testDifferingEdges() {
        XCTAssertEqual(RoomRenderings.differing("", from: "Through Him"), [])
        XCTAssertEqual(RoomRenderings.differing("by him", from: ""), [r(0, 2), r(3, 6)])
        XCTAssertEqual(RoomRenderings.differing("by him", from: "Through Him"), [r(0, 2)])
        XCTAssertEqual(RoomRenderings.differing("Through Him", from: "Through Him"), [])
    }

    // MARK: Who and in what order

    func testOtherVersionsThatAgreeShareABlock() {
        let room = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: bsbShort, readers: [me]),
            others: [
                .init(version: kjv, phrase: "by him", readers: [ann]),
                .init(version: .web, phrase: webShort, readers: [ruth]),
                .init(version: asv, phrase: "By Him.", readers: [dana]),
            ])
        XCTAssertEqual(room.groups, [
            .init(versions: [.bsb, .web], phrase: bsbShort, differing: [], readers: [me, ruth], isYours: true),
            .init(versions: [kjv, asv], phrase: "by him", differing: [r(0, 2)], readers: [ann, dana], isYours: false),
        ])
    }

    func testReadersYoursFirst() {
        // Yours leads even when the room lists another version first; in your
        // block your version's readers (you first) lead those that agree.
        let room = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: bsbShort, readers: [me, caleb]),
            others: [
                .init(version: kjv, phrase: kjvShort, readers: [ann, ben, gabe]),
                .init(version: .web, phrase: webShort, readers: [ruth, eli]),
                .init(version: .bsb, phrase: bsbShort, readers: [faith]),
            ])
        XCTAssertEqual(room.groups.map(\.isYours), [true, false])
        XCTAssertEqual(room.groups[0].versions, [.bsb, .web])
        // A version given twice keeps its readers together.
        XCTAssertEqual(room.groups[0].readers, [me, caleb, faith, ruth, eli])
        XCTAssertEqual(room.groups[0].readers.first, me)
        XCTAssertEqual(room.groups[1].readers, [ann, ben, gabe])
    }

    // MARK: Versions not on this phone

    func testNotOnThisPhoneIsKeptApart() {
        let room = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: bsbShort, readers: [me]),
            others: [
                .init(version: .web, phrase: webShort, readers: [ruth]),
                .init(version: nkjv, phrase: nil, readers: [ann]),
                .init(version: .niv, phrase: nil, readers: [ben]),
                .init(version: nkjv, phrase: nil, readers: [gabe]),
            ])
        XCTAssertEqual(room.groups.count, 1)
        XCTAssertEqual(room.groups[0].versions, [.bsb, .web])
        XCTAssertEqual(room.notOnThisPhone, .init(versions: [nkjv, .niv], readers: [ann, gabe, ben]))
        // What could not be compared cannot be said to agree.
        XCTAssertFalse(room.allAgree)
        XCTAssertFalse(room.isOneVersion)
    }

    // MARK: Agreement

    func testRoomOfOneVersion() {
        let alone = RoomRenderings.of(yours: .init(version: .bsb, phrase: bsbShort, readers: [me]), others: [])
        XCTAssertEqual(alone.groups, [
            .init(versions: [.bsb], phrase: bsbShort, differing: [], readers: [me], isYours: true),
        ])
        XCTAssertTrue(alone.allAgree)
        XCTAssertTrue(alone.isOneVersion)

        let together = RoomRenderings.of(
            yours: .init(version: .bsb, phrase: bsbWhole, readers: [me]),
            others: [.init(version: .bsb, phrase: bsbWhole, readers: [ruth, ann])])
        XCTAssertEqual(together.groups.count, 1)
        XCTAssertEqual(together.groups[0].versions, [.bsb])
        XCTAssertEqual(together.groups[0].readers, [me, ruth, ann])
        XCTAssertTrue(together.isOneVersion)
    }

    func testAllAgreeAcrossVersions() {
        let room = eleven(bsb: bsbShort, web: webShort, kjv: "Through him", asv: asvShort)
        XCTAssertEqual(room.groups.count, 1)
        XCTAssertEqual(room.groups[0].versions, [.bsb, .web, kjv, asv])
        XCTAssertEqual(room.groups[0].readers.count, 11)
        XCTAssertTrue(room.allAgree)
        XCTAssertFalse(room.isOneVersion)
    }
}
