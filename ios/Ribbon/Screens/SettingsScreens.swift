import SwiftUI
import RibbonCore

// S19–S22 — the four screens You pushes to: text and translation,
// notifications, downloads, the plan.
//
// You itself (S18) is no longer a sheet of its own. It is a section of the
// menu, with the rooms and the room you are in — MenuScreen.swift, and
// docs/deviations.md 14. What is still not here is what was never here: no
// theme picker (dark is the product), no accent picker (chartreuse is the
// brand's, not the user's), no app-icon picker.

// S20 — text and translation. Translation is personal, not shared (§2.6);
// changing it never moves your position or breaks a note's anchor. The
// size slider previews live over real Scripture — the verse you were last
// reading, which is a small thing and the kind of small thing this product
// is made of.
struct TextSettingsScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                VStack(alignment: .leading, spacing: 12) {
                    SmallCaps(Copy.translation, size: 12)
                    // Bundled translations always; licensed ones (NKJV
                    // first) appear the day their edition is configured on
                    // the proxy — never as a dead row.
                    ForEach(model.availableTranslations) { translation in
                        Button {
                            model.setTranslation(translation.id)
                        } label: {
                            HStack {
                                Text(translation.displayName)
                                    .font(RibbonType.ui(16))
                                    .foregroundStyle(Palette.text)
                                Spacer()
                                if model.me?.translation == translation.id {
                                    Circle().fill(Palette.chartreuse).frame(width: 6, height: 6)
                                }
                            }
                            .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                    }
                }

                VStack(alignment: .leading, spacing: 12) {
                    SmallCaps(Copy.textSize, size: 12)
                    Slider(
                        value: Binding(
                            get: { model.settings.scriptureSize },
                            set: { size in model.updateSettings { $0.scriptureSize = size } }),
                        in: 16...24, step: 0.5)
                        .tint(Palette.chartreuse)
                }

                VStack(alignment: .leading, spacing: 12) {
                    SmallCaps(Copy.lineSpacing, size: 12)
                    Picker("", selection: Binding(
                        get: { model.settings.lineSpacingStep },
                        set: { step in model.updateSettings { $0.lineSpacingStep = step } })
                    ) {
                        Text(Copy.lineSpacingClose).tag(0)
                        Text(Copy.lineSpacingBook).tag(1)
                        Text(Copy.lineSpacingOpen).tag(2)
                    }
                    .pickerStyle(.segmented)
                }

                Toggle(isOn: Binding(
                    get: { model.settings.redLetter },
                    set: { on in model.updateSettings { $0.redLetter = on } })
                ) {
                    Text(Copy.redLetter)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                }
                .tint(Palette.chartreuse)

                preview
            }
            .padding(24)
        }
        .scrollIndicators(.hidden)
        .room()
    }

    /// The live preview: the verse you were last reading, in your
    /// translation, at your size.
    @ViewBuilder
    private var preview: some View {
        if let me = model.me,
           let room = model.currentRoom,
           let reading = model.openReading(in: room) {
            let position = model.myPosition(in: reading)
            if let text = model.scripture.verseText(position, translation: me.translation) {
                VStack(alignment: .leading, spacing: 8) {
                    HairlineRule()
                    Text(text)
                        .font(RibbonType.scripture(model.settings.scriptureSize))
                        .foregroundStyle(Palette.text)
                        .lineSpacing(model.settings.scriptureSize * (model.settings.lineHeightMultiple - 1))
                        .fixedSize(horizontal: false, vertical: true)
                    SmallCaps(position.formatted, size: 11)
                }
                .padding(.top, 10)
            }
        }
    }
}

// S19 — notifications, per room, not global: you want everything from your
// wife and almost nothing from the Thursday study. The finished-book note
// has no switch — it fires a handful of times a year and is an invitation
// back, not an absence notification. Nothing here is about absence,
// lapses, streaks, or reminders to read, because those notifications
// don't exist.
struct NotificationSettingsScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 30) {
                ForEach(model.state.rooms) { room in
                    roomSection(room)
                }
                quietHours
            }
            .padding(24)
        }
        .scrollIndicators(.hidden)
        .room()
    }

    private func roomSection(_ room: Room) -> some View {
        let prefs = model.notificationPrefs(for: room)
        return VStack(alignment: .leading, spacing: 14) {
            SmallCaps(model.displayName(of: room), size: 12)
            toggle(Copy.notesLeftForYou, prefs.notesLeft) { on in
                var p = prefs; p.notesLeft = on; model.setNotificationPrefs(p, for: room)
            }
            toggle(Copy.cardsOpen, prefs.cardsOpen) { on in
                var p = prefs; p.cardsOpen = on; model.setNotificationPrefs(p, for: room)
            }
            toggle(Copy.whenTheyOpenTheBook, prefs.whenTheyOpenTheBook) { on in
                var p = prefs; p.whenTheyOpenTheBook = on; model.setNotificationPrefs(p, for: room)
            }
            toggle(Copy.thinkingOfYou, prefs.thinkingOfYou) { on in
                var p = prefs; p.thinkingOfYou = on; model.setNotificationPrefs(p, for: room)
            }
        }
    }

    private func toggle(_ title: String, _ value: Bool, set: @escaping (Bool) -> Void) -> some View {
        Toggle(isOn: Binding(get: { value }, set: set)) {
            Text(title)
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
        }
        .tint(Palette.chartreuse)
    }

    private var quietHours: some View {
        VStack(alignment: .leading, spacing: 10) {
            SmallCaps(Copy.quietHours, size: 12)
            HStack(spacing: 10) {
                minutePicker(
                    minutes: Binding(
                        get: { model.settings.quietHoursStart },
                        set: { m in model.updateSettings { $0.quietHoursStart = m } }))
                Text(Copy.quietHoursTo)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                minutePicker(
                    minutes: Binding(
                        get: { model.settings.quietHoursEnd },
                        set: { m in model.updateSettings { $0.quietHoursEnd = m } }))
            }
            Text(Copy.thinkingOfYouStillArrives)
                .font(RibbonType.ui(13))
                .foregroundStyle(Palette.muted)
        }
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
        .labelsHidden()
        .colorScheme(.dark)
    }
}

// S21 — downloads. Megabytes are a fine number: they measure a device, not
// a person. Both launch translations ship in the app, whole.
struct DownloadsScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                SmallCaps(Copy.onThisPhone, size: 12)
                ForEach(model.availableTranslations) { translation in
                    HStack {
                        Text(translation.fullName)
                            .font(RibbonType.ui(16))
                            .foregroundStyle(Palette.text)
                        Spacer()
                        if translation.isBundled {
                            SmallCaps(Copy.megabytes(bundledMegabytes(translation.id)), size: 12)
                        } else {
                            // Licensed text streams; the book being read
                            // stays on the phone, the rest doesn't — its
                            // license, not our design.
                            SmallCaps(Copy.streams, size: 12)
                        }
                    }
                }
                Text(Copy.voiceNotesPolicy)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .padding(.top, 8)
            }
            .padding(24)
        }
        .scrollIndicators(.hidden)
        .room()
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

// S22 — the plan. The first book is free, all the way through — not a
// 7-day trial, because a clock is a count. The ask appears in exactly two
// places: the shelf after the first ember, and here. A non-paying member
// never sees a price and never learns who pays.
struct PlanScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(Copy.firstBookFree)
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)
                if let room = model.currentRoom, room.isPaused {
                    WayInButton(title: Copy.startTheRoomAgain) {
                        // StoreKit arrives with the backend; nothing to
                        // restore locally.
                    }
                }
                Text(Copy.theAskComesOnce)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
            }
            .padding(24)
        }
        .scrollIndicators(.hidden)
        .room()
    }
}
