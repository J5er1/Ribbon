import XCTest
@testable import RibbonCore

// Seating.kt's tests (SeatingTest.kt) are these, case for case.

final class SeatingTests: XCTestCase {
    func testNobodyIsNoRows() {
        XCTAssertEqual(Seating.rows(places: 0), [])
    }

    func testUpToSixSitInOneRow() {
        for places in 1...6 {
            XCTAssertEqual(Seating.rows(places: places), [places])
        }
    }

    func testPastSixTheySitInTwoRowsAsEvenlyAsTheyGo() {
        XCTAssertEqual(Seating.rows(places: 7), [4, 3])
        XCTAssertEqual(Seating.rows(places: 8), [4, 4])
        XCTAssertEqual(Seating.rows(places: 9), [5, 4])
        XCTAssertEqual(Seating.rows(places: 11), [6, 5])
        XCTAssertEqual(Seating.rows(places: 12), [6, 6])
    }

    func testEveryPlaceIsSeatedAndNoRowHoldsMoreThanSix() {
        for places in 0...(Room.capacity + 1) {
            let rows = Seating.rows(places: places)
            XCTAssertEqual(rows.reduce(0, +), places)
            XCTAssertTrue(rows.allSatisfy { $0 <= Seating.perRow })
            // The longer row is nearer the fire, and the rows differ by one
            // at most.
            XCTAssertEqual(rows, rows.sorted(by: >))
            if let first = rows.first, let last = rows.last {
                XCTAssertLessThanOrEqual(first - last, 1)
            }
        }
    }
}
