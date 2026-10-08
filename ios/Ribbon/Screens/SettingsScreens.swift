import SwiftUI
import UIKit
import RibbonCore

// S19–S22 — the four screens the menu pushes to: text, notifications,
// downloads, the plan (ledger A23/A42). Each is a page with a title and a
// lede, and under it groups of tiles with a section label over each and a
// footnote under it where one is owed. Nothing here is a Settings app:
// no icons, no grouped inset table, no disclosure triangles — and since
// I41 none of its controls either: the switches, the page's sliders and
// the quiet hours are drawn on the page (DesignSystem/Drawn.swift).
//
// What is still not here is what was never here: no theme picker (dark is
// the product), no accent picker (chartreuse is the brand's, not the
// user's), no app-icon picker.

// MARK: - S20: Text

/// The version is yours (A60, reversing A42), and so is the page: its
/// typeface, size, spacing, weight, letter spacing, margins, a new line for
/// every verse, clearer verse numbers and red letter move only your own
/// page (A68, A69).
///
/// A version is chosen by reading it (S20, A67): each row carries the verse
/// you are at, in that version's own words; and a typeface the same way,
/// that verse set in each (A69). Above the page's controls is a strip of
/// the page itself, edge to edge — that verse and the ones after it,
/// numbered, weighted, spaced, broken into lines and coloured the way the
/// page sets them, at the page's own width and margins — so every one of
/// the page's settings shows on it. It keeps one height whatever they are,
/// so nothing under a finger moves; and while a slider further down is
/// held, a second copy of it is pinned under the bar (A69). With no book
/// open, it is John 1.
struct TextSettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// The page is set from the same theme as the book (A68), so it reads
    /// what the book's theme reads: Bold Text, which adds weight, and the
    /// type size, at which a face off Book is made.
    @Environment(\.legibilityWeight) private var legibilityWeight
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The licensed versions' chapters at the verse you are at, as this
    /// phone holds them. Each is a file to read and decode, and the body
    /// runs again on every step of a slider, so they are read once for a
    /// place, off the main thread, and kept here with the place they were
    /// read for.
    @State private var streamed = Streamed()
    /// The settings as the slider under a finger would make them, until it
    /// lifts and they are written (A69).
    @State private var held: AppSettings?
    /// A slider was moved a moment ago: the pinned page stays a little.
    @State private var lingering = false
    @State private var lingerTurn = 0
    /// Where the strip of the page and the screen are, to know whether the
    /// strip can be seen without the pinned one.
    @State private var stripFrame: CGRect = .zero
    @State private var screenFrame: CGRect = .zero

    private struct Streamed {
        var key = ""
        var chapters: [TranslationID: ScriptureChapter] = [:]
    }

    /// The bar RibbonScreen draws over its scroll.
    private static let barHeight: CGFloat = 52

    var body: some View {
        let place = readingPlace
        let specimens = versionSpecimens(at: place)
        let faceSpecimen = typefaceSpecimen(at: place)
        let settings = model.settings
        let face = settings.face
        RibbonScreen(title: Copy.textAndTranslation, lede: Copy.textLede, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 28) {
                // The footnote says which verse the rows are showing, and
                // only when one of them is showing it.
                SettingsGroup(
                    title: Copy.translation, detail: Copy.translationIsYours,
                    footnote: specimens.isEmpty ? nil : Copy.specimenAt(place.formatted)
                ) {
                    // Bundled translations always; licensed ones appear the
                    // day their edition is configured on the proxy — never
                    // as a dead row.
                    ForEach(model.availableTranslations) { translation in
                        SettingChoice(
                            translation.displayName,
                            subtitle: translation.isBundled ? Copy.bundledSub : Copy.streamsSub,
                            specimen: specimens[translation.id],
                            chosen: model.words(room: model.currentRoom) == translation.id
                        ) {
                            model.setTranslation(translation.id)
                        }
                    }
                }

                // A typeface is chosen by reading it, too (A69): your verse
                // in each, at the size that looks like Literata's 16.
                SettingsGroup(
                    title: Copy.typeface, detail: Copy.thePageIsYours,
                    footnote: faceSpecimen == nil ? nil : Copy.typefaceSpecimenAt(place.formatted)
                ) {
                    ForEach(PageFaces.all) { option in
                        SettingChoice(
                            option.name,
                            subtitle: Copy.typefaceSub(option.id),
                            specimen: faceSpecimen,
                            specimenFace: option,
                            specimenWeight: settings.weight(boldText: legibilityWeight == .bold),
                            chosen: face == option
                        ) {
                            model.updateSettings { $0.typeface = option.id }
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 14) {
                    strip(at: place)
                        .padding(.horizontal, -RibbonShape.screenMargin)
                        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { stripFrame = $0 }
                    pageGroup(settings, face: face)
                }
            }
        }
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { screenFrame = $0 }
        // The strip, pinned under the bar while a slider is held and the
        // strip itself is out of sight (A69). A copy: it takes no touch and
        // says nothing a screen reader has not heard from the strip.
        .overlay(alignment: .top) {
            if pinned {
                strip(at: place)
                    .padding(.top, Self.barHeight)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                    .transition(.opacity)
            }
        }
        .animation(RibbonMotion.settle(still: reduceMotion), value: pinned)
        .task(id: streamedKey(at: place)) { await readStreamed(at: place) }
    }

    /// The page group's controls (A68, A69): five sliders, then the three
    /// switches. Each slider moves through its scale's positions; while one
    /// is held the strip shows what it would make, and it is written once
    /// when the finger lifts.
    @ViewBuilder
    private func pageGroup(_ settings: AppSettings, face: PageFace) -> some View {
        let lineHeights = PageType.lineHeightScale
        let weights = PageType.weightScale
        let spacings = PageType.letterSpacingScale
        let margins = PageType.marginScale
        let lineHeight = PageType.lineHeightHundredths(saved: settings.lineHeightHundredths, legacyStep: settings.lineSpacingStep)
        let weight = PageType.weight(saved: settings.pageWeight, legacyStep: settings.weightStep)
        SettingsGroup(title: Copy.thePage, detail: Copy.thePageIsYours) {
            // Drawn, not the system's (I41); said as a size in points, a
            // measure of type.
            SettingControl(Copy.textSize, subtitle: Copy.textSizeSub) {
                RibbonSlider(
                    index: write(PageType.sizeIndex(of: settings.scriptureSize)) { s, i in s.scriptureSize = PageType.size(at: i) },
                    count: PageType.sizePositions,
                    label: Copy.textSize,
                    spoken: { Copy.textSizeValue(PageType.size(at: $0)) },
                    onHold: hold { s, i in s.scriptureSize = PageType.size(at: i) }
                ) {
                    SliderEnd.letters("A", size: 13, face: face)
                } trailing: {
                    SliderEnd.letters("A", size: 21, face: face)
                }
            }
            SettingControl(Copy.lineSpacing, subtitle: Copy.lineSpacingSub) {
                RibbonSlider(
                    index: write(lineHeights.index(of: lineHeight)) { s, i in s.setLineHeight(lineHeights.value(at: i)) },
                    count: lineHeights.count, mark: lineHeights.bookIndex,
                    label: Copy.lineSpacing,
                    spoken: { Copy.lineSpacingValue(lineHeights.value(at: $0)) },
                    onHold: hold { s, i in s.setLineHeight(lineHeights.value(at: i)) }
                ) {
                    SliderEnd.rules(gap: 2)
                } trailing: {
                    SliderEnd.rules(gap: 6)
                }
            }
            // The page's own letters, lighter or heavier on their axis
            // (A68), in the typeface you read (A69).
            SettingControl(Copy.weight, subtitle: Copy.weightSub) {
                RibbonSlider(
                    index: write(weights.index(of: weight)) { s, i in s.setWeight(weights.value(at: i)) },
                    count: weights.count, mark: weights.bookIndex,
                    label: Copy.weight,
                    spoken: { Copy.weightValue(weights.nearestNamed(weights.value(at: $0), named: PageType.weights)) },
                    onHold: hold { s, i in s.setWeight(weights.value(at: i)) }
                ) {
                    SliderEnd.letters("a", size: 17, weight: weights.values.first ?? 350, face: face)
                } trailing: {
                    SliderEnd.letters("a", size: 17, weight: weights.values.last ?? 470, face: face)
                }
            }
            SettingControl(Copy.letterSpacing, subtitle: Copy.letterSpacingSub) {
                RibbonSlider(
                    index: write(spacings.index(of: settings.letterSpacingThousandths)) { s, i in
                        s.letterSpacingThousandths = spacings.value(at: i)
                    },
                    count: spacings.count,
                    label: Copy.letterSpacing,
                    spoken: { Copy.letterSpacingValue(spacings.value(at: $0)) },
                    onHold: hold { s, i in s.letterSpacingThousandths = spacings.value(at: i) }
                ) {
                    SliderEnd.letters("ab", size: 15, face: face)
                } trailing: {
                    SliderEnd.letters("ab", size: 15, face: face, tracking: 15 * 0.25)
                }
            }
            // Outside the gutter, so notes stay beside their words, and
            // beside the trailing edge, which the presence form keeps as it
            // always has (A69).
            SettingControl(Copy.margins, subtitle: Copy.marginsSub) {
                RibbonSlider(
                    index: write(margins.index(of: settings.marginPoints)) { s, i in s.marginPoints = margins.value(at: i) },
                    count: margins.count,
                    label: Copy.margins,
                    spoken: { Copy.marginValue(margins.value(at: $0)) },
                    onHold: hold { s, i in s.marginPoints = margins.value(at: i) }
                ) {
                    SliderEnd.page(inset: 1.5)
                } trailing: {
                    SliderEnd.page(inset: 4.5)
                }
            }
            SettingSwitch(
                Copy.verseLines, subtitle: Copy.verseLinesSub,
                isOn: Binding(
                    get: { model.settings.versePerLine },
                    set: { on in model.updateSettings { $0.versePerLine = on } }))
            SettingSwitch(
                Copy.clearNumbers, subtitle: Copy.clearNumbersSub,
                isOn: Binding(
                    get: { model.settings.clearVerseNumbers },
                    set: { on in model.updateSettings { $0.clearVerseNumbers = on } }))
            SettingSwitch(
                Copy.redLetter, subtitle: Copy.redLetterSub,
                isOn: Binding(
                    get: { model.settings.redLetter },
                    set: { on in model.updateSettings { $0.redLetter = on } }))
        }
    }

    /// A slider's position, written through `apply` when it changes — a
    /// tap, a screen reader's step, or a finger lifting — and the pinned
    /// page kept a moment after.
    private func write(_ position: Int, _ apply: @escaping (inout AppSettings, Int) -> Void) -> Binding<Int> {
        Binding(
            get: { position },
            set: { next in
                model.updateSettings { apply(&$0, next) }
                linger()
            })
    }

    /// What a held slider would make, shown on the strip until the finger
    /// lifts; nil when it has.
    private func hold(_ apply: @escaping (inout AppSettings, Int) -> Void) -> (Int?) -> Void {
        { position in
            guard let position else {
                held = nil
                linger()
                return
            }
            var next = model.settings
            apply(&next, position)
            held = next
        }
    }

    /// The pinned page stays 1.2 s after the last change, then goes.
    private func linger() {
        lingerTurn += 1
        let turn = lingerTurn
        lingering = true
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(1200))
            if lingerTurn == turn { lingering = false }
        }
    }

    /// Whether the strip is pinned: a slider is held or was just moved, and
    /// less than half the strip is in sight under the bar.
    private var pinned: Bool {
        guard held != nil || lingering, stripFrame.height > 0, screenFrame.height > 0 else { return false }
        let top = screenFrame.minY + Self.barHeight
        let seen = min(stripFrame.maxY, screenFrame.maxY) - max(stripFrame.minY, top)
        return seen < stripFrame.height / 2
    }

    /// The verse the rows and the strip are shown at: where you are in the
    /// current room's open book, or — with none open — the first verse of
    /// John, the beginning the app's own pictures use.
    private var readingPlace: VerseAddress {
        if let room = model.currentRoom, let reading = model.openReading(in: room) {
            return model.myPosition(in: reading)
        }
        return VerseAddress(bookID: "JHN", chapter: 1, verse: 1)
    }

    /// A chapter as this phone already holds it: a bundled version always,
    /// a licensed one only once that chapter has streamed here for the
    /// book being read. Nothing is fetched to fill a settings screen —
    /// a version without the words here simply shows none (A67).
    ///
    /// A bundled book is decoded once and kept by the store, so it is
    /// asked for here, in the body. A licensed chapter is read by
    /// `readStreamed` and only looked up here.
    private func heldChapter(_ address: VerseAddress, in version: TranslationID) -> ScriptureChapter? {
        guard let licensed = TranslationRegistry.translation(for: version), !licensed.isBundled else {
            return model.scripture.chapter(address, translation: version)
        }
        guard streamed.key == streamedKey(at: address) else { return nil }
        return streamed.chapters[licensed.id]
    }

    /// Which licensed chapters `streamed` holds: the versions, the book and
    /// the chapter. The verse is not part of it — moving within a chapter
    /// reads nothing again.
    private func streamedKey(at place: VerseAddress) -> String {
        let licensed = model.availableTranslations.filter { !$0.isBundled }.map(\.id.rawValue)
        return licensed.joined(separator: ",") + "/\(place.bookID)/\(place.chapter)"
    }

    /// The licensed chapters at a place, read off the main thread. They
    /// come after the screen is drawn, so they arrive rather than appear,
    /// the way the original words' panel brings in a licensed version: the
    /// rows open to their verse and the strip to its page, on `arrive`.
    /// It is the plain token, kept under reduce motion, because what it
    /// carries is words fading in, and held still a fade is a cut.
    private func readStreamed(at place: VerseAddress) async {
        let key = streamedKey(at: place)
        let licensed = model.availableTranslations.filter { !$0.isBundled }
        let store = model.scripture
        let chapters = await Task.detached(priority: .userInitiated) {
            var held: [TranslationID: ScriptureChapter] = [:]
            for translation in licensed {
                if let chapter = store.cachedRemoteChapter(place, translation: translation) {
                    held[translation.id] = chapter
                }
            }
            return held
        }.value
        guard !Task.isCancelled else { return }
        if chapters.isEmpty {
            streamed = Streamed(key: key, chapters: [:])
            return
        }
        withAnimation(RibbonMotion.arrive) {
            streamed = Streamed(key: key, chapters: chapters)
        }
    }

    /// Each version's words for the verse you are at, quoted the way a note
    /// quotes one (`text(forVerse:)`), for the versions that hold it.
    private func versionSpecimens(at place: VerseAddress) -> [TranslationID: String] {
        var specimens: [TranslationID: String] = [:]
        for translation in model.availableTranslations {
            if let text = heldChapter(place, in: translation.id)?.text(forVerse: place.verse) {
                specimens[translation.id] = text
            }
        }
        return specimens
    }

    /// The verse every typeface is shown at (A69): yours, in your version
    /// if this phone holds it, else in the Berean Standard, which it always
    /// does.
    private func typefaceSpecimen(at place: VerseAddress) -> String? {
        heldChapter(place, in: model.words(room: model.currentRoom))?.text(forVerse: place.verse)
            ?? model.scripture.chapter(place, translation: .bsb)?.text(forVerse: place.verse)
    }

    /// The strip of the page at a place, set from what a held slider would
    /// make or else from your settings; nothing when your version's chapter
    /// is not on this phone.
    @ViewBuilder
    private func strip(at place: VerseAddress) -> some View {
        if let chapter = heldChapter(place, in: model.words(room: model.currentRoom)) {
            PageStrip(
                chapter: chapter, place: place, settings: held ?? model.settings,
                boldText: legibilityWeight == .bold, dynamicTypeSize: dynamicTypeSize,
                screenHeight: screenFrame.height)
        }
    }
}

/// A strip of the page itself, edge to edge (A69): the page's ground and a
/// hairline over and under it, the place in the page's small caps, and the
/// verse you are at and the ones after it set by the page's own theme — its
/// typeface, size, weight, spacing, letter spacing, margins, verse lines,
/// number ink and red letter — at the page's own insets, so the lines break
/// where they would on the page. It is one height whatever the page's
/// settings are, and the words that run past it fade into the ground.
struct PageStrip: View {
    var chapter: ScriptureChapter
    var place: VerseAddress
    var settings: AppSettings
    var boldText: Bool
    var dynamicTypeSize: DynamicTypeSize
    var screenHeight: CGFloat

    @State private var width: CGFloat = 0
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 176

    /// The words' fade into the ground, at the foot.
    private static let fade: CGFloat = 28

    var body: some View {
        let theme = ReadingTheme(settings, boldText: boldText, dynamicTypeSize: dynamicTypeSize, columnWidth: width)
        let tall = screenHeight > 0 ? min(height, screenHeight * 0.4) : height
        let scaled = RibbonType.uiScripture(theme.fontSize).pointSize
        let leading = CGFloat(PageType.extraLeading(
            size: Double(scaled), multiple: Double(theme.lineHeightMultiple), face: theme.face))
        VStack(alignment: .leading, spacing: 10) {
            SmallCaps(place.formatted, size: 11)
            if let page = pageText(from: place.verse, theme: theme) {
                page
                    .font(RibbonType.scripture(theme.fontSize, weight: theme.weight, face: theme.face))
                    .lineSpacing(max(0, leading))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.leading, theme.textInsets.left)
        .padding(.trailing, theme.textInsets.right)
        .padding(.top, 16)
        .frame(maxWidth: .infinity, minHeight: tall, maxHeight: tall, alignment: .topLeading)
        .mask {
            LinearGradient(
                stops: [
                    .init(color: .black, location: 0),
                    .init(color: .black, location: max(0, 1 - Self.fade / tall)),
                    .init(color: .clear, location: 1),
                ],
                startPoint: .top, endPoint: .bottom)
        }
        .clipped()
        .background {
            ZStack {
                Palette.ground
                Image("PaperGrain")
                    .resizable(resizingMode: .tile)
                    .opacity(0.035)
            }
        }
        .overlay(alignment: .top) { Rectangle().fill(Palette.rule).frame(height: 1) }
        .overlay(alignment: .bottom) { Rectangle().fill(Palette.rule).frame(height: 1) }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .accessibilityElement(children: .combine)
    }

    /// The verses set the way ChapterTextView sets them, so the strip is the
    /// page and not a description of it: a number before each verse but a
    /// chapter's first, in the page's ink for numbers, the words in the
    /// page's typeface and weight, spaced as the page spaces them, in the
    /// crimson ink where they are Jesus' and the switch is on, each block
    /// on its own line, a verse on its own line where the page gives it
    /// one, and a psalm's title and a stanza break left out.
    ///
    /// The walk is the page's — a running verse moved by every number, a
    /// title's included, as `ownTexts()` keeps it — so a verse that begins
    /// on a title still finds its words in the line after it. Nil when the
    /// chapter does not have the verse.
    private func pageText(from first: Int, theme: ReadingTheme) -> Text? {
        var lines: [Text] = []
        var running: Int?
        var numbered: Set<Int> = []
        let kern = CGFloat(PageType.letterSpacingEm(theme.letterSpacing))
            * RibbonType.uiScripture(theme.fontSize, weight: theme.weight, face: theme.face).pointSize
        for block in chapter.blocks where block.s != .b {
            // Where the page breaks a line before a verse (A68), the strip
            // closes the line it is on.
            let lineStarts: Set<Int> = theme.versePerLine ? Set(block.verseLineStarts()) : []
            var line: Text?
            for (index, span) in block.x.enumerated() {
                if let v = span.v { running = v }
                guard block.s != .d, let verse = running, verse >= first, verse <= first + 3 else { continue }
                if lineStarts.contains(index), let sofar = line {
                    lines.append(sofar)
                    line = nil
                }
                var run = pageWords(span.t, theme: theme, kern: kern, red: theme.redLetter && span.isRedLetter)
                if verse != 1 && !numbered.contains(verse) {
                    numbered.insert(verse)
                    run = Text("\(verseNumber(verse, theme: theme))\(run)")
                }
                if let sofar = line {
                    line = Text("\(sofar)\(run)")
                } else {
                    line = run
                }
            }
            if let line { lines.append(line) }
        }
        guard var page = lines.first else { return nil }
        for line in lines.dropFirst() {
            page = Text("\(page)\n\(line)")
        }
        return page
    }

    /// The page's verse number: small caps at 0.62 of the size, ivory at
    /// the page's alpha — 45%, or 70% when they are clearer (A68) — raised
    /// by 0.3 of the size, and a thin space after it.
    private func verseNumber(_ verse: Int, theme: ReadingTheme) -> Text {
        Text(verbatim: "\(verse)\u{2009}")
            .font(RibbonType.smallCaps(theme.fontSize * 0.62))
            .foregroundStyle(Palette.text.opacity(theme.verseNumberAlpha))
            .baselineOffset(theme.fontSize * 0.3)
    }

    /// A run of the page's words in its typeface and weight, spaced as the
    /// page spaces them: ivory, or the crimson ink where the words are
    /// Jesus' and red letter is on — the page's own red.
    private func pageWords(_ words: String, theme: ReadingTheme, kern: CGFloat, red: Bool) -> Text {
        Text(verbatim: words)
            .font(RibbonType.scripture(theme.fontSize, weight: theme.weight, face: theme.face))
            .kerning(kern)
            .foregroundStyle(red ? Ink.crimson.color : Palette.text)
    }
}

// MARK: - S19: Notifications

/// Per room, not global: you want everything from your wife and almost
/// nothing from the Thursday study. The finished-book note has no switch —
/// it fires a handful of times a year and is an invitation back. Nothing
/// here is about absence, lapses, streaks, or reminders to read, because
/// those notifications don't exist.
///
/// They say who (A67). Each room's switches sit under its faces, and in a
/// room of two — where "they" is one person — the switches name them and
/// show the notification itself, their face beside the words that will
/// arrive. A room of three or more, or one you are alone in, reads as it
/// always did.
struct NotificationSettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    @State private var allowed = true

    var body: some View {
        RibbonScreen(title: Copy.notifications, lede: Copy.notificationsLede, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 28) {
                if !allowed && model.state.hasAskedAboutNotifications {
                    // The OS is silencing it (S19): one line admitting so,
                    // and the one control that helps. Never a second ask.
                    HStack(spacing: 8) {
                        Text(Copy.iOSIsNotPassingTheseOn)
                            .font(RibbonType.ui(15))
                            .foregroundStyle(Palette.muted)
                        Spacer(minLength: 6)
                        QuietControl(title: Copy.openIOSSettings) {
                            if let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        }
                    }
                    .padding(.horizontal, RibbonShape.textInset)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .paper(.row)
                }

                ForEach(model.state.rooms) { room in
                    roomGroup(room)
                }

                SettingsGroup(title: Copy.quietHours, footnote: Copy.thinkingOfYouStillArrives) {
                    // One tile, where there were two rows and a wheel under
                    // each (A67): the night drawn as a band, and over it the
                    // two times in a sentence — in the same clock the
                    // handles are spoken in, so what is read and what is
                    // heard agree.
                    SettingControl(quietHoursTitle, subtitle: Copy.quietHoursBandSub) {
                        QuietHoursBandView(
                            start: Binding(
                                get: { model.settings.quietHoursStart },
                                set: { m in model.updateSettings { $0.quietHoursStart = m } }),
                            end: Binding(
                                get: { model.settings.quietHoursEnd },
                                set: { m in model.updateSettings { $0.quietHoursEnd = m } }))
                    }
                }
            }
        }
        .task { await Notifications.refreshAllowed(); allowed = Notifications.allowed }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                Task { await Notifications.refreshAllowed(); allowed = Notifications.allowed }
            }
        }
    }

    private func roomGroup(_ room: Room) -> some View {
        let prefs = model.notificationPrefs(for: room)
        let reading = model.openReading(in: room)
        let book = reading.flatMap { Bible.book(id: $0.bookID)?.name }

        // A room of two names the other person, in the words their
        // notifications will use. A notification that names a verse or a
        // book is shown, not described — and where there is no verse or
        // book to put in it yet, the switch says what it always said.
        let other = theOther(in: room)
        var notesExample: SettingExample?
        var bookExample: SettingExample?
        if let other, let reading {
            notesExample = example(from: other, Copy.notifNoteLeft(other.name, model.myPosition(in: reading).formatted))
        }
        if let other, let book {
            bookExample = example(from: other, Copy.notifReading(other.name, book))
        }
        let notesSub: String? = notesExample == nil ? Copy.notesLeftForYouSub : nil
        let cardsSub = other.map { Copy.cardsOpenSubNamed($0.name) } ?? Copy.cardsOpenSub
        let bookTitle = other.map { Copy.whenNameOpensTheBook($0.name) } ?? Copy.whenTheyOpenTheBook
        let bookSub: String? = bookExample == nil ? Copy.whenTheyOpenTheBookSub : nil
        let thinkingSub = other.map { Copy.thinkingOfYouSubNamed($0.name) } ?? Copy.thinkingOfYouSub

        return VStack(alignment: .leading, spacing: 10) {
            roomHeader(room, book: book)
            SettingsGroup {
                SettingSwitch(Copy.notesLeftForYou, subtitle: notesSub, example: notesExample, isOn: Binding(
                    get: { prefs.notesLeft },
                    set: { on in var p = prefs; p.notesLeft = on; model.setNotificationPrefs(p, for: room) }))
                SettingSwitch(Copy.cardsOpen, subtitle: cardsSub, isOn: Binding(
                    get: { prefs.cardsOpen },
                    set: { on in var p = prefs; p.cardsOpen = on; model.setNotificationPrefs(p, for: room) }))
                SettingSwitch(bookTitle, subtitle: bookSub, example: bookExample, isOn: Binding(
                    get: { prefs.whenTheyOpenTheBook },
                    set: { on in var p = prefs; p.whenTheyOpenTheBook = on; model.setNotificationPrefs(p, for: room) }))
                SettingSwitch(Copy.thinkingOfYou, subtitle: thinkingSub, isOn: Binding(
                    get: { prefs.thinkingOfYou },
                    set: { on in var p = prefs; p.thinkingOfYou = on; model.setNotificationPrefs(p, for: room) }))
            }
        }
    }

    /// A room's section label with its faces before it (A67) — the same
    /// overlapping faces its tile in Your rooms draws, so the room is known
    /// by who is in it as well as by its name. The label stays the heading
    /// a screen reader stops at; the faces are a picture, and say nothing,
    /// so nobody is counted aloud.
    private func roomHeader(_ room: Room, book: String?) -> some View {
        HStack(spacing: 8) {
            HStack(spacing: -5) {
                ForEach(model.members(of: room)) { membership in
                    PortraitView(
                        person: model.person(membership.personID),
                        ink: membership.ink,
                        size: 18,
                        image: model.portrait(membership.personID))
                }
            }
            .accessibilityHidden(true)
            SectionLabel(model.displayName(of: room), detail: book)
        }
        .padding(.horizontal, RibbonShape.textInset)
    }

    /// The one other person in a room of two, called what their
    /// notifications call them: the first word of their name.
    private struct TheOther {
        var person: Person
        var ink: Ink?
        var name: String { firstName(person.name) }
    }

    /// Nil alone, at three or more, and for someone this phone does not
    /// know by name yet — a switch never names a blank.
    private func theOther(in room: Room) -> TheOther? {
        let others = model.members(of: room).filter { $0.personID != model.me?.id }
        guard others.count == 1, let membership = others.first,
              let person = model.person(membership.personID),
              !person.name.trimmingCharacters(in: .whitespaces).isEmpty
        else { return nil }
        return TheOther(person: person, ink: membership.ink)
    }

    private func example(from other: TheOther, _ sentence: String) -> SettingExample {
        SettingExample(
            person: other.person, ink: other.ink,
            image: model.portrait(other.person.id), sentence: sentence)
    }

    /// The quiet hours as a sentence, or none at all when both ends are on
    /// the same minute — the core's test, the one the band draws by.
    private var quietHoursTitle: String {
        let start = model.settings.quietHoursStart
        let end = model.settings.quietHoursEnd
        guard QuietHoursBand.wrapped(start) != QuietHoursBand.wrapped(end) else { return Copy.noQuietHours }
        return Copy.quietHoursFromUntil(QuietHoursBandView.clock(start), QuietHoursBandView.clock(end))
    }
}

// MARK: - S21: Downloads

/// Megabytes are a fine number: they measure a device, not a person. Both
/// launch translations ship in the app, whole; a licensed one streams.
struct DownloadsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        RibbonScreen(title: Copy.downloads, lede: Copy.downloadsLede, onBack: { dismiss() }) {
            SettingsGroup(title: Copy.onThisPhone, footnote: Copy.voiceNotesPolicy) {
                ForEach(model.availableTranslations) { translation in
                    SettingValue(
                        translation.fullName,
                        value: translation.isBundled
                            ? Copy.megabytes(bundledMegabytes(translation.id))
                            // Licensed text streams; the book being read
                            // stays on the phone, the rest doesn't — its
                            // license, not our design.
                            : Copy.streams)
                }
            }
        }
    }

    private func bundledMegabytes(_ translation: TranslationID) -> Int {
        guard
            let urls = Bundle.main.urls(
                forResourcesWithExtension: "json",
                subdirectory: "Scripture/\(translation.rawValue)")
        else { return 5 }
        let bytes = urls.compactMap {
            try? FileManager.default.attributesOfItem(atPath: $0.path)[.size] as? Int
        }.reduce(0, +)
        return max(1, bytes / 1_000_000)
    }
}

// MARK: - S22: The plan

/// The first book is free, all the way through — not a 7-day trial,
/// because a clock is a count. The ask appears in exactly two places: the
/// shelf after the first ember, and here. A non-paying member never sees a
/// price and never learns who pays.
struct PlanScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        RibbonScreen(title: Copy.plan, lede: Copy.planLede, onBack: { dismiss() }) {
            SettingsGroup(footnote: Copy.theAskComesOnce) {
                SettingNote(Copy.firstBookFree)
                if let room = model.currentRoom, room.isPaused {
                    // The restore half needs StoreKit, which arrives with
                    // billing (deviation 11): the room says the true thing,
                    // and the store is where the other half of it lives.
                    SettingNote(Copy.roomPaused)
                    SettingRow(Copy.manageInStore) {
                        if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
                            UIApplication.shared.open(url)
                        }
                    }
                }
            }
        }
    }
}
