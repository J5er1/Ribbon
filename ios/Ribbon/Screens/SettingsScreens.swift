import SwiftUI
import PhotosUI
import RibbonCore

// S18 — You: account and app-wide settings. One tap from the room now (the
// portrait in the room's header), by the owner's call — the book buried it
// two taps deep; docs/deviations.md records the change. Still not here: no
// theme picker (dark is the product), no accent picker (chartreuse is the
// brand's, not the user's), no app-icon picker.

struct YouSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var editingName = false
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    HStack(spacing: 14) {
                        // Portrait and name, editable in place (S18) —
                        // presence is faces, so the face can be added or
                        // changed here, not only at onboarding.
                        PhotosPicker(selection: $portraitItem, matching: .images) {
                            PortraitView(
                                person: model.me, ink: nil, size: 56,
                                image: model.me.flatMap { model.portrait($0.id) })
                        }
                        .buttonStyle(.plain)
                        .onChange(of: portraitItem) { _, item in
                            Task {
                                if let data = try? await item?.loadTransferable(type: Data.self),
                                   let jpeg = downsampledJPEG(data) {
                                    await model.setPortrait(jpeg)
                                }
                            }
                        }
                        if editingName {
                            TextField("", text: $name)
                                .font(RibbonType.ui(18))
                                .foregroundStyle(Palette.text)
                                .onSubmit {
                                    let trimmed = name.trimmingCharacters(in: .whitespaces)
                                    if !trimmed.isEmpty { model.updateMe(name: trimmed) }
                                    editingName = false
                                }
                        } else {
                            Text(model.me?.name ?? "")
                                .font(RibbonType.ui(18))
                                .foregroundStyle(Palette.text)
                                .onTapGesture {
                                    name = model.me?.name ?? ""
                                    editingName = true
                                }
                        }
                    }
                    .padding(.top, 26)

                    VStack(alignment: .leading, spacing: 20) {
                        NavigationLink(Copy.textAndTranslation) { TextSettingsScreen() }
                        NavigationLink(Copy.notifications) { NotificationSettingsScreen() }
                        NavigationLink(Copy.downloads) { DownloadsScreen() }
                        NavigationLink(Copy.plan) { PlanScreen() }
                    }
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)

                    if let room = model.currentRoom {
                        RoomSection(room: room, onLeft: { dismiss() })
                    }

                    AccountSection()

                    QuietControl(title: Copy.deleteAccount) { confirmDelete = true }

                    SmallCaps(appVersion, size: 11, color: Palette.muted.opacity(0.7))
                        .padding(.top, 8)
                }
                .padding(.horizontal, 24)
            }
            .scrollIndicators(.hidden)
            .room()
        }
        .presentationBackground(Palette.ground)
        .confirmationDialog(
            // §6.8: the "leave your notes behind?" question, asked once, at
            // deletion. Leaving them is never not the default.
            Copy.leaveNotesQuestion, isPresented: $confirmDelete, titleVisibility: .visible
        ) {
            Button("Delete, and leave them", role: .destructive) {
                model.deleteAccount(keepNotesBehind: true)
            }
            Button("Delete, and take them back", role: .destructive) {
                model.deleteAccount(keepNotesBehind: false)
            }
        }
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        return "ribbon \(version)"
    }
}

/// The current room's own controls: its name, your ink, the way out. These
/// lived only on your S12, which a fresh room of one couldn't reach
/// (deviations 9a) — now they're one tap away with the rest of You.
private struct RoomSection: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onLeft: () -> Void

    @State private var editingRoomName = false
    @State private var roomName = ""
    @State private var showInkPicker = false
    @State private var confirmLeave = false
    @State private var askAboutNotes = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SmallCaps(model.displayName(of: room), size: 12)
            if editingRoomName {
                TextField(
                    "", text: $roomName,
                    prompt: Text(Copy.roomName).foregroundStyle(Palette.muted))
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.text)
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
            if model.inkIsIdentity(in: room) {
                QuietControl(title: Copy.changeYourInk) { showInkPicker = true }
            }
            QuietControl(title: Copy.leaveThisRoom) { confirmLeave = true }
        }
        .padding(.top, 8)
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
        .confirmationDialog(
            Copy.leaveRoomConfirm, isPresented: $confirmLeave, titleVisibility: .visible
        ) {
            Button(Copy.leaveThisRoom, role: .destructive) { askAboutNotes = true }
        }
        .confirmationDialog(
            Copy.leaveNotesQuestion, isPresented: $askAboutNotes, titleVisibility: .visible
        ) {
            // Leaving them is the default; taking them back is possible
            // and never the default (§6.8).
            Button(Copy.leaveThem) {
                model.leaveRoom(room, keepNotesBehind: true)
                onLeft()
            }
            Button(Copy.takeThemBack) {
                model.leaveRoom(room, keepNotesBehind: false)
                onLeft()
            }
        }
    }
}

/// The account (§6.10): an emailed code, no passwords. Signed out is a
/// state, not a nag — one quiet line, and the reason stated plainly.
private struct AccountSection: View {
    @Environment(AppModel.self) private var model

    enum Phase: Equatable { case idle, email, code }
    @State private var phase: Phase = .idle
    @State private var email = ""
    @State private var code = ""
    @State private var errorLine: String?
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if model.isSignedIn {
                if let address = model.accountEmail {
                    SmallCaps(address, size: 12)
                }
                QuietControl(title: Copy.signOut) {
                    Task { await model.signOutRemote() }
                }
            } else if model.remote == nil {
                // Remote is not configured in this build; no dead control.
                EmptyView()
            } else {
                switch phase {
                case .idle:
                    VStack(alignment: .leading, spacing: 8) {
                        QuietControl(title: Copy.signIn) {
                            phase = .email
                        }
                        Text(Copy.accountReason)
                            .font(RibbonType.ui(13))
                            .foregroundStyle(Palette.muted)
                    }
                case .email:
                    field(prompt: Copy.yourEmail, text: $email, submit: Copy.sendTheCode) {
                        sendCode()
                    }
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                case .code:
                    Text(Copy.codeOnItsWay)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                    field(prompt: Copy.theCode, text: $code, submit: Copy.signIn) {
                        verify()
                    }
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                }
                if let errorLine {
                    Text(errorLine)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.muted)
                }
            }
        }
        .padding(.top, 8)
    }

    private func field(
        prompt: String, text: Binding<String>, submit: String, action: @escaping () -> Void
    ) -> some View {
        HStack(spacing: 12) {
            TextField("", text: text, prompt: Text(prompt).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .focused($focused)
                .onSubmit(action)
            QuietControl(title: submit, action: action)
        }
        .onAppear { focused = true }
    }

    private func sendCode() {
        let address = email.trimmingCharacters(in: .whitespaces)
        guard address.contains("@") else { return }
        errorLine = nil
        Task {
            do {
                try await model.sendSignInCode(to: address)
                code = ""
                phase = .code
            } catch {
                errorLine = Copy.serverUnreachable
            }
        }
    }

    private func verify() {
        let entered = code.trimmingCharacters(in: .whitespaces)
        guard !entered.isEmpty else { return }
        errorLine = nil
        Task {
            do {
                try await model.verifySignInCode(
                    email: email.trimmingCharacters(in: .whitespaces), code: entered)
                phase = .idle
            } catch {
                errorLine = Copy.signInCodeWrong
            }
        }
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
                        Text("Close").tag(0)
                        Text("Book").tag(1)
                        Text("Open").tag(2)
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
                Text("to")
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                minutePicker(
                    minutes: Binding(
                        get: { model.settings.quietHoursEnd },
                        set: { m in model.updateSettings { $0.quietHoursEnd = m } }))
            }
            Text("Thinking of you still arrives, silently, as a touch.")
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
                            SmallCaps("\(bundledMegabytes(translation.id)) MB", size: 12)
                        } else {
                            // Licensed text streams; the book being read
                            // stays on the phone, the rest doesn't — its
                            // license, not our design.
                            SmallCaps("streams", size: 12)
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
                Text("When your room's first ember is on the shelf, Ribbon will ask — there, and only there.")
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
            }
            .padding(24)
        }
        .scrollIndicators(.hidden)
        .room()
    }
}
