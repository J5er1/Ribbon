import XCTest
@testable import RibbonCore

// The reader's settings in the state file (I42): what the last release
// wrote is read whole, a field this build cannot read costs only itself,
// and the new ones come back as they went in. Android keeps its settings in
// the app, where StateSurvivesAnUpdateTest and SettingsSurviveADamagedFileTest
// hold them to the same.
final class AppSettingsTests: XCTestCase {
    let room = UUID(uuidString: "1A2B3C4D-0000-4000-8000-000000000001")!

    /// The settings as the build before this one wrote them: its generated
    /// encoder, with the keys sorted as the state file sorts them and a
    /// dictionary keyed by UUID written as a flat array of key, value.
    /// Written out by hand rather than encoded by today's model, because a
    /// round trip through today's encoder would prove only that today
    /// agrees with itself.
    let fromTheLastRelease = """
        {"lineSpacingStep":2,"quietHoursEnd":420,"quietHoursStart":1380,"redLetter":true,\
        "roomNotifications":["1A2B3C4D-0000-4000-8000-000000000001",\
        {"cardsOpen":false,"notesLeft":false,"thinkingOfYou":false,"whenTheyOpenTheBook":true}],\
        "scriptureSize":24}
        """

    /// The settings as the build before the sliders wrote them (PR #47's,
    /// A68): every field it had, the page's three among them, and none of
    /// the sliders' keys. Written out by hand, as above.
    let fromTheBuildBeforeTheSliders = """
        {"clearVerseNumbers":true,"lineSpacingStep":0,"quietHoursEnd":420,"quietHoursStart":1380,\
        "redLetter":true,"roomNotifications":["1A2B3C4D-0000-4000-8000-000000000001",\
        {"cardsOpen":false,"notesLeft":false,"thinkingOfYou":false,"whenTheyOpenTheBook":true}],\
        "scriptureSize":24.5,"versePerLine":true,"weightStep":2}
        """

    func decoded(_ json: String) throws -> AppSettings {
        try JSONDecoder().decode(AppSettings.self, from: Data(json.utf8))
    }

    func testSettingsFromTheLastReleaseKeepEverything() throws {
        let settings = try decoded(fromTheLastRelease)
        XCTAssertEqual(settings.scriptureSize, 24)
        XCTAssertEqual(settings.lineSpacingStep, 2)
        XCTAssertTrue(settings.redLetter)
        XCTAssertEqual(settings.quietHoursStart, 23 * 60)
        XCTAssertEqual(settings.quietHoursEnd, 7 * 60)
        XCTAssertEqual(
            settings.roomNotifications,
            [room: RoomNotificationPrefs(
                notesLeft: false, cardsOpen: false, whenTheyOpenTheBook: true, thinkingOfYou: false)])
        // What this release adds starts where the page already was.
        XCTAssertEqual(settings.weightStep, 1)
        XCTAssertFalse(settings.versePerLine)
        XCTAssertFalse(settings.clearVerseNumbers)
        XCTAssertEqual(settings.weight(boldText: false), 400)
        XCTAssertEqual(settings.verseNumberAlpha, 0.45)
        XCTAssertEqual(settings.lineHeightMultiple, 1.9)
        XCTAssertNil(settings.lineHeightHundredths)
        XCTAssertNil(settings.pageWeight)
        XCTAssertEqual(settings.letterSpacingThousandths, 0)
        XCTAssertEqual(settings.marginPoints, 0)
        XCTAssertEqual(settings.face, PageFaces.literata)
    }

    func testSettingsFromTheBuildBeforeTheSlidersKeepEverything() throws {
        let settings = try decoded(fromTheBuildBeforeTheSliders)
        XCTAssertEqual(settings.scriptureSize, 24.5)
        XCTAssertEqual(settings.lineSpacingStep, 0)
        XCTAssertTrue(settings.redLetter)
        XCTAssertEqual(settings.quietHoursStart, 23 * 60)
        XCTAssertEqual(settings.quietHoursEnd, 7 * 60)
        XCTAssertEqual(
            settings.roomNotifications,
            [room: RoomNotificationPrefs(
                notesLeft: false, cardsOpen: false, whenTheyOpenTheBook: true, thinkingOfYou: false)])
        XCTAssertEqual(settings.weightStep, 2)
        XCTAssertTrue(settings.versePerLine)
        XCTAssertTrue(settings.clearVerseNumbers)
        // The page is the one its steps set, and the sliders start on it.
        XCTAssertEqual(settings.lineHeightMultiple, 1.55)
        XCTAssertEqual(settings.weight(boldText: false), 470)
        XCTAssertEqual(settings.weight(boldText: true), 620)
        XCTAssertNil(settings.lineHeightHundredths)
        XCTAssertNil(settings.pageWeight)
        XCTAssertEqual(settings.letterSpacingThousandths, 0)
        XCTAssertEqual(settings.marginPoints, 0)
        XCTAssertEqual(settings.typeface, "literata")
        XCTAssertEqual(settings.face, PageFaces.literata)
        XCTAssertEqual(settings.letterSpacingEm, 0)
        XCTAssertEqual(settings.marginRequested, 0)
    }

    func testAnUnreadableFieldCostsOnlyItself() throws {
        let settings = try decoded("""
            {"lineSpacingStep":2,"quietHoursEnd":420,"quietHoursStart":1380,"redLetter":"yes",\
            "roomNotifications":["1A2B3C4D-0000-4000-8000-000000000001",{"notesLeft":false}],\
            "scriptureSize":24,"weightStep":"heavier","versePerLine":true,"clearVerseNumbers":null,\
            "lineHeightHundredths":"open","pageWeight":[440],"letterSpacingThousandths":1.5,\
            "marginPoints":"wide","typeface":7}
            """)
        XCTAssertFalse(settings.redLetter, "the unreadable one takes its default")
        XCTAssertEqual(settings.weightStep, 1)
        XCTAssertFalse(settings.clearVerseNumbers)
        XCTAssertNil(settings.lineHeightHundredths)
        XCTAssertNil(settings.pageWeight)
        XCTAssertEqual(settings.letterSpacingThousandths, 0)
        XCTAssertEqual(settings.marginPoints, 0)
        XCTAssertEqual(settings.typeface, "literata")
        // An unreadable slider falls back to its step, which was read.
        XCTAssertEqual(settings.lineHeightMultiple, 1.9)
        XCTAssertEqual(settings.weight(boldText: false), 400)
        // And everything beside them is kept.
        XCTAssertEqual(settings.scriptureSize, 24)
        XCTAssertEqual(settings.lineSpacingStep, 2)
        XCTAssertEqual(settings.quietHoursStart, 23 * 60)
        XCTAssertEqual(settings.quietHoursEnd, 7 * 60)
        XCTAssertEqual(settings.roomNotifications[room]?.notesLeft, false)
        XCTAssertTrue(settings.versePerLine)
        // An empty object is every default, which is today's page.
        XCTAssertEqual(try decoded("{}"), AppSettings())
    }

    func testRoomSwitchesFromBeforeANewSwitchKeepTheirOwn() throws {
        // A room saved before one of its switches existed.
        let prefs = try JSONDecoder().decode(
            RoomNotificationPrefs.self,
            from: Data(#"{"cardsOpen":false,"notesLeft":false,"whenTheyOpenTheBook":true}"#.utf8))
        XCTAssertEqual(
            prefs,
            RoomNotificationPrefs(notesLeft: false, cardsOpen: false, whenTheyOpenTheBook: true, thinkingOfYou: true))
        // And inside the settings, beside a room whose switches are whole.
        let settings = try decoded("""
            {"roomNotifications":["1A2B3C4D-0000-4000-8000-000000000001",{"whenTheyOpenTheBook":true},\
            "1A2B3C4D-0000-4000-8000-000000000002",{"cardsOpen":false,"notesLeft":true,\
            "thinkingOfYou":false,"whenTheyOpenTheBook":false}]}
            """)
        let other = UUID(uuidString: "1A2B3C4D-0000-4000-8000-000000000002")!
        XCTAssertEqual(settings.roomNotifications[room], RoomNotificationPrefs(whenTheyOpenTheBook: true))
        XCTAssertEqual(settings.roomNotifications[other], RoomNotificationPrefs(cardsOpen: false, thinkingOfYou: false))
    }

    func testTheNewSettingsSurviveARoundTrip() throws {
        let settings = AppSettings(
            scriptureSize: 27.5, lineSpacingStep: 0, redLetter: true,
            quietHoursStart: 21 * 60, quietHoursEnd: 5 * 60 + 30,
            roomNotifications: [room: RoomNotificationPrefs(thinkingOfYou: false)],
            weightStep: 2, versePerLine: true, clearVerseNumbers: true,
            lineHeightHundredths: 160, pageWeight: 460, letterSpacingThousandths: 25, marginPoints: 32,
            typeface: "ebGaramond")
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(settings)
        XCTAssertEqual(try JSONDecoder().decode(AppSettings.self, from: data), settings)
        // The names the last release wrote, the three A68 added beside them,
        // and the sliders' five (A69).
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(
            Set(object.keys),
            [
                "scriptureSize", "lineSpacingStep", "redLetter", "quietHoursStart", "quietHoursEnd",
                "roomNotifications", "weightStep", "versePerLine", "clearVerseNumbers",
                "lineHeightHundredths", "pageWeight", "letterSpacingThousandths", "marginPoints", "typeface",
            ])
        // A slider's value is the page, not the step beside it.
        XCTAssertEqual(settings.weight(boldText: false), 460)
        XCTAssertEqual(settings.weight(boldText: true), 610)
        XCTAssertEqual(settings.verseNumberAlpha, 0.70)
        XCTAssertEqual(settings.lineHeightMultiple, 1.6)
        XCTAssertEqual(settings.face, PageFaces.ebGaramond)
        XCTAssertEqual(settings.letterSpacingEm, 0.025)
        XCTAssertEqual(settings.marginRequested, 32)
    }

    // A slider writes its value and, beside it, the old step nearest it, so
    // that a build from before the sliders, which reads only the step,
    // opens on nearly the same page (A69).
    func testASliderWritesTheOldStepBeside() {
        var settings = AppSettings()
        settings.setLineHeight(184)
        XCTAssertEqual(settings.lineHeightHundredths, 184)
        XCTAssertEqual(settings.lineSpacingStep, 2)
        XCTAssertEqual(settings.lineHeightMultiple, 1.84)
        settings.setLineHeight(163)
        XCTAssertEqual(settings.lineHeightHundredths, 165, "held to the scale")
        XCTAssertEqual(settings.lineSpacingStep, 1)
        settings.setLineHeight(150)
        XCTAssertEqual(settings.lineSpacingStep, 0)
        settings.setWeight(360)
        XCTAssertEqual(settings.pageWeight, 360)
        XCTAssertEqual(settings.weightStep, 0)
        XCTAssertEqual(settings.weight(boldText: true), 510)
        settings.setWeight(999)
        XCTAssertEqual(settings.pageWeight, 470, "held to the scale")
        XCTAssertEqual(settings.weightStep, 2)
        settings.setWeight(420)
        XCTAssertEqual(settings.weightStep, 1)
        // A value no slider writes is still held when the page reads it.
        settings.marginPoints = 1000
        settings.letterSpacingThousandths = -5
        settings.typeface = "comicSans"
        XCTAssertEqual(settings.marginRequested, 48)
        XCTAssertEqual(settings.letterSpacingEm, 0)
        XCTAssertEqual(settings.face, PageFaces.literata)
    }
}
