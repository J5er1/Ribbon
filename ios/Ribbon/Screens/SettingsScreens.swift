import SwiftUI
import UIKit
import RibbonCore

// S19–S22 — the four screens the menu pushes to: text, notifications,
// downloads, the plan (ledger A23/A42). Each is a page with a title and a
// lede, and under it groups of tiles with a section label over each and a
// footnote under it where one is owed. Nothing here is a Settings app:
// no icons, no grouped inset table, no disclosure triangles.
//
// What is still not here is what was never here: no theme picker (dark is
// the product), no accent picker (chartreuse is the brand's, not the
// user's), no app-icon picker.

// MARK: - S20: Text

/// The room reads one version (A42), and the page is yours: size, spacing
/// and red letter move only your own page. The size slider previews live
/// over real Scripture — the verse you were last reading.
struct TextSettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        RibbonScreen(title: Copy.textAndTranslation, lede: Copy.textLede, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 28) {
                SettingsGroup(title: Copy.translation, detail: Copy.translationIsTheRooms) {
                    // Bundled translations always; licensed ones appear the
                    // day their edition is configured on the proxy — never
                    // as a dead row.
                    ForEach(model.availableTranslations) { translation in
                        SettingChoice(
                            translation.displayName,
                            subtitle: translation.isBundled ? Copy.bundledSub : Copy.streamsSub,
                            chosen: (model.currentRoom?.translation ?? model.me?.translation) == translation.id
                        ) {
                            model.setTranslation(translation.id)
                        }
                    }
                }

                SettingsGroup(title: Copy.thePage, detail: Copy.thePageIsYours) {
                    SettingControl(Copy.textSize, subtitle: Copy.textSizeSub) {
                        VStack(spacing: 12) {
                            Slider(
                                value: Binding(
                                    get: { model.settings.scriptureSize },
                                    set: { size in model.updateSettings { $0.scriptureSize = size } }),
                                in: 16...24, step: 0.5)
                            .tint(Palette.chartreuse)
                            .accessibilityLabel(Copy.textSize)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .well(.small)
                            preview
                        }
                    }
                    SettingControl(Copy.lineSpacing, subtitle: Copy.lineSpacingSub) {
                        Segments(
                            options: [Copy.lineSpacingClose, Copy.lineSpacingBook, Copy.lineSpacingOpen],
                            selection: Binding(
                                get: { model.settings.lineSpacingStep },
                                set: { step in model.updateSettings { $0.lineSpacingStep = step } }))
                        .accessibilityLabel(Copy.lineSpacing)
                    }
                    SettingSwitch(
                        Copy.redLetter, subtitle: Copy.redLetterSub,
                        isOn: Binding(
                            get: { model.settings.redLetter },
                            set: { on in model.updateSettings { $0.redLetter = on } }))
                }
            }
        }
    }

    /// The live preview: the verse you were last reading, in the room's
    /// version, at your size, in a well of its own.
    @ViewBuilder
    private var preview: some View {
        if let room = model.currentRoom,
           let reading = model.openReading(in: room) {
            let position = model.myPosition(in: reading)
            if let text = model.scripture.verseText(position, translation: model.words(room: room, reading: reading)) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(text)
                        .font(RibbonType.scripture(model.settings.scriptureSize))
                        .foregroundStyle(Palette.text)
                        .lineSpacing(model.settings.scriptureSize * (model.settings.lineHeightMultiple - 1))
                        .fixedSize(horizontal: false, vertical: true)
                    SmallCaps(position.formatted, size: 11)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .well(.small)
            }
        }
    }
}

/// A drawn segmented control: a well, and a paper pill that slides to the
/// chosen stop on a spring. The stops are words, in ivory when chosen and
/// muted otherwise, so colour is never the only signal.
struct Segments: View {
    let options: [String]
    @Binding var selection: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width / CGFloat(max(1, options.count))
            ZStack(alignment: .leading) {
                Color.clear.frame(maxWidth: .infinity, maxHeight: .infinity).well(.small)
                if reduceMotion {
                    // Held still, the pill does not travel between stops:
                    // it fades out of the old one and into the new (§11).
                    // It used to jump.
                    ForEach(options.indices, id: \.self) { index in
                        pill(width: width, height: proxy.size.height)
                            .offset(x: 2 + width * CGFloat(index))
                            .opacity(index == selection ? 1 : 0)
                    }
                } else {
                    pill(width: width, height: proxy.size.height)
                        .offset(x: 2 + width * CGFloat(selection))
                }
                HStack(spacing: 0) {
                    ForEach(options.indices, id: \.self) { index in
                        Button {
                            selection = index
                        } label: {
                            Text(options[index])
                                .font(RibbonType.ui(15))
                                .foregroundStyle(index == selection ? Palette.text : Palette.muted)
                                .frame(width: width, height: proxy.size.height)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(index == selection ? [.isSelected] : [])
                    }
                }
            }
            // The pill and the words together: the chosen word brightens
            // as the pill arrives under it, rather than before it does.
            .animation(reduceMotion ? RibbonMotion.arrive : RibbonMotion.touched, value: selection)
        }
        .frame(height: 44)
    }

    private func pill(width: CGFloat, height: CGFloat) -> some View {
        Color.clear
            .frame(width: width - 4, height: height - 4)
            .paper(.init(RibbonShape.small - 2))
    }
}

// MARK: - S19: Notifications

/// Per room, not global: you want everything from your wife and almost
/// nothing from the Thursday study. The finished-book note has no switch —
/// it fires a handful of times a year and is an invitation back. Nothing
/// here is about absence, lapses, streaks, or reminders to read, because
/// those notifications don't exist.
struct NotificationSettingsScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private enum Edge { case from, until }
    @State private var editing: Edge?
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
                    SettingRow(Copy.quietHoursFrom, value: clock(model.settings.quietHoursStart), chevron: false) {
                        editing = editing == .from ? nil : .from
                    }
                    if editing == .from {
                        minutePicker(minutes: Binding(
                            get: { model.settings.quietHoursStart },
                            set: { m in model.updateSettings { $0.quietHoursStart = m } }))
                    }
                    SettingRow(Copy.quietHoursUntil, value: clock(model.settings.quietHoursEnd), chevron: false) {
                        editing = editing == .until ? nil : .until
                    }
                    if editing == .until {
                        minutePicker(minutes: Binding(
                            get: { model.settings.quietHoursEnd },
                            set: { m in model.updateSettings { $0.quietHoursEnd = m } }))
                    }
                }
                .animation(RibbonMotion.settle(still: reduceMotion), value: editing)
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
        let book = model.openReading(in: room).flatMap { Bible.book(id: $0.bookID)?.name }
        return SettingsGroup(title: model.displayName(of: room), detail: book) {
            SettingSwitch(Copy.notesLeftForYou, subtitle: Copy.notesLeftForYouSub, isOn: Binding(
                get: { prefs.notesLeft },
                set: { on in var p = prefs; p.notesLeft = on; model.setNotificationPrefs(p, for: room) }))
            SettingSwitch(Copy.cardsOpen, subtitle: Copy.cardsOpenSub, isOn: Binding(
                get: { prefs.cardsOpen },
                set: { on in var p = prefs; p.cardsOpen = on; model.setNotificationPrefs(p, for: room) }))
            SettingSwitch(Copy.whenTheyOpenTheBook, subtitle: Copy.whenTheyOpenTheBookSub, isOn: Binding(
                get: { prefs.whenTheyOpenTheBook },
                set: { on in var p = prefs; p.whenTheyOpenTheBook = on; model.setNotificationPrefs(p, for: room) }))
            SettingSwitch(Copy.thinkingOfYou, subtitle: Copy.thinkingOfYouSub, isOn: Binding(
                get: { prefs.thinkingOfYou },
                set: { on in var p = prefs; p.thinkingOfYou = on; model.setNotificationPrefs(p, for: room) }))
        }
    }

    private func clock(_ minutes: Int) -> String {
        let date = Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: Date()) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }

    private func minutePicker(minutes: Binding<Int>) -> some View {
        DatePicker(
            "",
            selection: Binding(
                get: {
                    Calendar.current.date(
                        bySettingHour: minutes.wrappedValue / 60,
                        minute: minutes.wrappedValue % 60, second: 0, of: Date()) ?? Date()
                },
                set: { date in
                    let c = Calendar.current.dateComponents([.hour, .minute], from: date)
                    minutes.wrappedValue = (c.hour ?? 0) * 60 + (c.minute ?? 0)
                }),
            displayedComponents: .hourAndMinute)
        .datePickerStyle(.wheel)
        .labelsHidden()
        .colorScheme(.dark)
        .frame(maxWidth: .infinity)
        .frame(height: 160)
        .clipped()
        .well()
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
