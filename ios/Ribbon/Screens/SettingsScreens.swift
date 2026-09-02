import SwiftUI
import PhotosUI
import RibbonCore

// S18 — You: account and app-wide settings. One tap from the room now (the
// portrait in the room's header), by the owner's call — the book buried it
// two taps deep; docs/deviations.md records the change. Still not here: no
// theme picker (dark is the product), no accent picker (chartreuse is the
// brand's, not the user's), no app-icon picker. No system bars anywhere:
// every pushed screen carries its own small-caps title and way back.

struct YouSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var editingName = false
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var confirmDelete = false
    @State private var askAboutNotesOnDelete = false
    @State private var deleteLine: String?
    @FocusState private var nameFocused: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    HStack {
                        Spacer()
                        BackControl(title: Copy.close) { dismiss() }
                    }
                    .padding(.top, 8)
                    HStack(spacing: 14) {
                        // Portrait and name, editable in place (S18) —
                        // presence is faces, so the face can be added or
                        // changed here, not only at onboarding.
                        PhotosPicker(selection: $portraitItem, matching: .images) {
                            PortraitView(
                                person: model.me,
                                ink: model.me.map { Ink.stable(for: $0.id) },
                                size: 56,
                                image: model.me.flatMap { model.portrait($0.id) })
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(model.me?.portraitPath == nil ? Copy.addAPortrait : Copy.changePortrait)
                        .onChange(of: portraitItem) { _, item in
                            Task {
                                if let data = try? await item?.loadTransferable(type: Data.self),
                                   let jpeg = downsampledJPEG(data) {
                                    await model.setPortrait(jpeg)
                                }
                            }
                        }
                        if editingName {
                            RibbonTextField(prompt: Copy.yourName, text: $name, size: 18)
                                .focused($nameFocused)
                                .submitLabel(.done)
                                .onSubmit(saveName)
                                .onAppear { nameFocused = true }
                        } else {
                            Button {
                                name = model.me?.name ?? ""
                                editingName = true
                            } label: {
                                HStack(spacing: 10) {
                                    Text(model.me?.name ?? "")
                                        .font(RibbonType.ui(18))
                                        .foregroundStyle(Palette.text)
                                    SmallCaps(Copy.edit, size: 11)
                                }
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(model.me?.name ?? ""), \(Copy.edit)")
                        }
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        NavigationLink { TextSettingsScreen() } label: { SettingRow(title: Copy.textAndTranslation) }
                        NavigationLink { NotificationSettingsScreen() } label: { SettingRow(title: Copy.notifications) }
                        NavigationLink { DownloadsScreen() } label: { SettingRow(title: Copy.downloads) }
                        NavigationLink { PlanScreen() } label: { SettingRow(title: Copy.plan) }
                    }
                    .buttonStyle(.plain)

                    if let room = model.currentRoom, !room.isDeparted {
                        RoomSection(room: room, onLeft: { dismiss() })
                    }

                    AccountSection()

                    if model.isSignedIn {
                        VStack(alignment: .leading, spacing: 8) {
                            QuietControl(title: Copy.deleteAccount) { confirmDelete = true }
                            if let deleteLine {
                                Text(deleteLine)
                                    .font(RibbonType.ui(13))
                                    .foregroundStyle(Palette.muted)
                            }
                        }
                    }

                    SmallCaps(appVersion, size: 11, color: Palette.muted.opacity(0.7))
                        .padding(.top, 8)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 30)
                .readableColumn()
            }
            .scrollIndicators(.hidden)
            .room()
            .toolbarVisibility(.hidden, for: .navigationBar)
        }
        .presentationBackground(Palette.ground)
        // §6.8: a plain confirmation first, then — only for a person who
        // has left notes — the one question, asked once, at deletion.
        .confirmationDialog(Copy.deleteAccountConfirm, isPresented: $confirmDelete, titleVisibility: .visible) {
            if model.hasLeftNotes {
                Button(Copy.deleteAndLeaveNotes, role: .destructive) { delete(keepNotes: true) }
                Button(Copy.deleteAndTakeNotes, role: .destructive) { delete(keepNotes: false) }
            } else {
                Button(Copy.deleteAccount, role: .destructive) { delete(keepNotes: true) }
            }
        }
    }

    private func saveName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if !trimmed.isEmpty { model.updateMe(name: trimmed) }
        editingName = false
    }

    private func delete(keepNotes: Bool) {
        deleteLine = nil
        Task {
            do {
                try await model.deleteAccount(keepNotesBehind: keepNotes)
                dismiss()
            } catch {
                deleteLine = Copy.deleteCouldNotReach
            }
        }
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        return "\(Copy.version) \(version)"
    }
}

/// The current room's own controls: its name, an invite, your ink, the
/// shelf's export, the way out. These lived only on your S12, which a
/// fresh room of one couldn't reach (deviations 9a) — now they're one tap
/// away with the rest of You.
private struct RoomSection: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onLeft: () -> Void

    @State private var editingRoomName = false
    @State private var roomName = ""
    @State private var showInkPicker = false
    @State private var showInvite = false
    @State private var confirmLeave = false
    @State private var confirmClose = false
    @State private var exportURL: URL?
    @FocusState private var roomNameFocused: Bool

    private var alone: Bool { model.members(of: room).count <= 1 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionHeader(model.displayName(of: room))
            if editingRoomName {
                RibbonTextField(prompt: model.derivedRoomNamePrompt, text: $roomName, size: 16)
                    .focused($roomNameFocused)
                    .submitLabel(.done)
                    .onAppear { roomNameFocused = true }
                    .onSubmit {
                        model.renameRoom(room, to: roomName)
                        editingRoomName = false
                    }
            } else {
                QuietControl(title: Copy.nameThisRoom) {
                    roomName = room.name ?? ""
                    editingRoomName = true
                }
            }
            // Any member can invite (§6.7), until the room holds six.
            if !room.isPaused {
                QuietControl(title: alone ? Copy.inviteSomeone : Copy.inviteSomeoneElse) { showInvite = true }
            }
            if model.inkIsIdentity(in: room) {
                QuietControl(title: Copy.changeYourInk) { showInkPicker = true }
                if model.members(of: room).count < 3 {
                    // Back to two: the free palette returns only by asking
                    // (§4.5).
                    QuietControl(title: Copy.freePaletteAgain) { model.restoreFreePalette(in: room) }
                }
            }
            if let exportURL {
                ShareLink(item: exportURL) {
                    SmallCaps(Copy.exportTheShelf, size: 13, color: Palette.muted)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle().inset(by: -8))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Copy.exportTheShelf)
            }
            if alone {
                // A room of one closes rather than "leaves" itself; the
                // export is offered first (§6.8).
                QuietControl(title: Copy.closeThisRoom) { confirmClose = true }
            } else {
                QuietControl(title: Copy.leaveThisRoom) { confirmLeave = true }
            }
        }
        .padding(.top, 8)
        .onAppear { exportURL = model.exportShelf(of: room) }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
        .sheet(isPresented: $showInvite) {
            InviteSheet(room: room)
        }
        .leaveRoomDialog(room: room, isPresented: $confirmLeave, onLeft: onLeft)
        .confirmationDialog(Copy.closeRoomConfirm, isPresented: $confirmClose, titleVisibility: .visible) {
            Button(Copy.closeIt, role: .destructive) {
                model.closeRoomOfOne(room)
                onLeft()
            }
        }
    }
}

/// The account (§6.10): an emailed code, no passwords. Signed out is a
/// state, not a nag — one quiet line, and the reason stated plainly.
private struct AccountSection: View {
    @Environment(AppModel.self) private var model

    @State private var signingIn = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if model.isSignedIn {
                if let address = model.accountEmail {
                    Text(address)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                }
                QuietControl(title: Copy.signOut) {
                    signingIn = false
                    Task { await model.signOutRemote() }
                }
                Text(Copy.signOutNote)
                    .font(RibbonType.ui(13))
                    .foregroundStyle(Palette.muted)
            } else if model.remote == nil {
                // Remote is not configured in this build; no dead control.
                EmptyView()
            } else if signingIn {
                AccountStep(
                    reason: Copy.emailReasonSettings,
                    primaryTitle: Copy.signIn,
                    onSignedIn: { signingIn = false },
                    skipTitle: Copy.neverMind,
                    onSkip: { signingIn = false })
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    QuietControl(title: Copy.signIn) { signingIn = true }
                    Text(Copy.emailReasonSettings)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.muted)
                }
            }
        }
        .padding(.top, 8)
    }
}

/// A pushed settings screen: its own way back and a small-caps title —
/// never the system bar (§12.1, §17).
private struct SettingsPage<Content: View>: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                HStack {
                    BackControl { dismiss() }
                    Spacer()
                }
                .padding(.top, 8)
                SmallCaps(title, size: 14)
                    .accessibilityAddTraits(.isHeader)
                content
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 30)
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .room()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }
}

// S20 — text and translation. Translation is personal, not shared (§2.6);
// changing it never moves your position or breaks a note's anchor. The
// size slider previews live over real Scripture — the verse you were last
// reading, which is a small thing and the kind of small thing this product
// is made of.
struct TextSettingsScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        SettingsPage(title: Copy.textAndTranslation) {
            VStack(alignment: .leading, spacing: 4) {
                SectionHeader(Copy.translation)
                // Bundled translations always; licensed ones (NKJV
                // first) appear the day their edition is configured on
                // the proxy — never as a dead row.
                ForEach(model.availableTranslations) { translation in
                    let chosen = model.me?.translation == translation.id
                    Button {
                        model.setTranslation(translation.id)
                    } label: {
                        HStack {
                            Text(translation.displayName)
                                .font(RibbonType.ui(16))
                                .foregroundStyle(Palette.text)
                            Spacer()
                            if chosen {
                                Circle().fill(Palette.chartreuse).frame(width: 6, height: 6)
                            }
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(translation.fullName)
                    .accessibilityAddTraits(chosen ? [.isSelected] : [])
                }
            }

            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(Copy.textSize)
                Slider(
                    value: Binding(
                        get: { model.settings.scriptureSize },
                        set: { size in model.updateSettings { $0.scriptureSize = size } }),
                    in: 16...24, step: 0.5)
                    .tint(Palette.chartreuse)
                    .accessibilityLabel(Copy.textSize)
            }

            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(Copy.lineSpacing)
                HStack(spacing: 10) {
                    ForEach(Array([Copy.spacingClose, Copy.spacingBook, Copy.spacingOpen].enumerated()), id: \.offset) { index, label in
                        let chosen = model.settings.lineSpacingStep == index
                        Button {
                            model.updateSettings { $0.lineSpacingStep = index }
                        } label: {
                            SmallCaps(label, size: 12, color: chosen ? Palette.ground : Palette.text)
                                .frame(minWidth: 64, minHeight: 36)
                                .background(chosen ? Palette.chartreuse : Palette.surface, in: Capsule())
                                .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: chosen ? 0 : 1))
                                .contentShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(Copy.lineSpacing), \(label)")
                        .accessibilityAddTraits(chosen ? [.isSelected] : [])
                    }
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                Toggle(isOn: Binding(
                    get: { model.settings.redLetter },
                    set: { on in model.updateSettings { $0.redLetter = on } })
                ) {
                    Text(Copy.redLetter)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                }
                .toggleStyle(RibbonToggleStyle())
                if let translation = model.me?.translation,
                   TranslationRegistry.translation(for: translation)?.redLetter == false {
                    Text(Copy.redLetterUnavailable)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.muted)
                }
            }

            preview
        }
    }

    /// The live preview: the verse you were last reading, in your
    /// translation, at your size — or the first starter book's opening,
    /// when nothing is open yet.
    @ViewBuilder
    private var preview: some View {
        if let me = model.me {
            let position: VerseAddress = {
                if let room = model.currentRoom, let reading = model.openReading(in: room) {
                    return model.myPosition(in: reading)
                }
                return VerseAddress(bookID: Bible.goodPlacesToStart.first ?? "MRK", chapter: 1, verse: 1)
            }()
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
// don't exist. The switches are kept for the day Ribbon can send them,
// and the screen says so once.
struct NotificationSettingsScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        SettingsPage(title: Copy.notifications) {
            Text(Copy.notificationsNotYet)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
            ForEach(model.liveRooms) { room in
                roomSection(room)
            }
            quietHours
        }
    }

    private func roomSection(_ room: Room) -> some View {
        let prefs = model.notificationPrefs(for: room)
        return VStack(alignment: .leading, spacing: 4) {
            SectionHeader(model.displayName(of: room))
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
        .toggleStyle(RibbonToggleStyle())
    }

    private var quietHours: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(Copy.quietHours)
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
            Text(Copy.quietHoursNote)
                .font(RibbonType.ui(13))
                .foregroundStyle(Palette.muted)
        }
    }

    private func minutePicker(minutes: Binding<Int>) -> some View {
        DatePicker(
            Copy.quietHours,
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
        .tint(Palette.chartreuse)
        .colorScheme(.dark)
    }
}

// S21 — downloads. Megabytes are a fine number: they measure a device, not
// a person. Both launch translations ship in the app, whole.
struct DownloadsScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        SettingsPage(title: Copy.downloads) {
            VStack(alignment: .leading, spacing: 4) {
                SectionHeader(Copy.onThisPhone)
                ForEach(model.availableTranslations) { translation in
                    HStack {
                        Text(translation.fullName)
                            .font(RibbonType.ui(16))
                            .foregroundStyle(Palette.text)
                        Spacer()
                        if translation.isBundled {
                            if let megabytes = bundledMegabytes(translation.id) {
                                SmallCaps("\(megabytes) MB", size: 12)
                            }
                        } else {
                            // Licensed text streams; the book being read
                            // stays on the phone, the rest doesn't — its
                            // license, not our design.
                            SmallCaps(Copy.streams, size: 12)
                        }
                    }
                    .frame(minHeight: 44)
                    .accessibilityElement(children: .combine)
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Toggle(isOn: Binding(
                    get: { model.settings.keepEverythingOnDevice },
                    set: { on in model.updateSettings { $0.keepEverythingOnDevice = on } })
                ) {
                    Text(Copy.keepEverything)
                        .font(RibbonType.ui(16))
                        .foregroundStyle(Palette.text)
                }
                .toggleStyle(RibbonToggleStyle())
                Text(Copy.keepEverythingNote)
                    .font(RibbonType.ui(13))
                    .foregroundStyle(Palette.muted)
            }
            Text(Copy.voiceNotesPolicy)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
        }
    }

    private func bundledMegabytes(_ translation: TranslationID) -> Int? {
        guard
            let urls = Bundle.main.urls(
                forResourcesWithExtension: "json",
                subdirectory: "Scripture/\(translation.rawValue)"),
            !urls.isEmpty
        else { return nil }
        let bytes = urls.compactMap {
            try? FileManager.default.attributesOfItem(atPath: $0.path)[.size] as? Int
        }.reduce(0, +)
        return max(1, bytes / 1_000_000)
    }
}

// S22 — the plan. The first book is free, all the way through — not a
// 7-day trial, because a clock is a count. The ask appears in exactly two
// places: the shelf after the first ember, and here. A non-paying member
// never sees a price and never learns who pays. Until the store exists,
// this screen says only what is true.
struct PlanScreen: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        SettingsPage(title: Copy.plan) {
            Text(Copy.firstBookFree)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
            if let room = model.currentRoom, room.isPaused {
                Text(Copy.roomPaused)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
            }
            Text(Copy.planAskLater)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)
            Text(Copy.planNotYet)
                .font(RibbonType.ui(13))
                .foregroundStyle(Palette.muted.opacity(0.8))
        }
    }
}
