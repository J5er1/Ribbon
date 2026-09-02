import SwiftUI
import PhotosUI
import RibbonCore

// S17 — onboarding: a thread, not a screen. Four questions, no tour, no
// carousel, no permission prompts at launch, no account wall. The Wave and
// the tagline are the only branded moment in the product. The thread ends
// in the book itself: the room is met for the first time on closing it,
// with a fire in it, catching — the loop (§6.2) shown rather than told.

struct OnboardingFlow: View {
    @Environment(\.appModel) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Done — with the reading to open straight away, when the thread
    /// ended on a book.
    var onDone: (Reading?) -> Void

    enum Step: Equatable {
        case mark
        case who
        case link
        case name
        case invite
        case book
        case join(UUID)
    }

    @State private var step: Step = .mark
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var portraitData: Data?
    @State private var pastedInvite = ""
    @State private var pasteMissed = false
    @FocusState private var nameFocused: Bool

    var body: some View {
        ZStack {
            switch step {
            case .mark:
                markMoment.transition(.opacity)
            case .who:
                whoStep.transition(.opacity)
            case .link:
                linkStep.transition(.opacity)
            case .name:
                nameStep.transition(.opacity)
            case .invite:
                inviteStep.transition(.opacity)
            case .book:
                bookStep.transition(.opacity)
            case .join(let token):
                JoinFlow(
                    token: token,
                    onDone: { onDone(nil) },
                    onStartInstead: {
                        // Declining the join forgets it — otherwise the
                        // pending token re-presents the join over the
                        // room they start instead.
                        model.pendingInvite = nil
                        withAnimation(RibbonMotion.settle) { step = model.me == nil ? .name : .invite }
                    })
                .id(token)
                .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .room()
        .preferredColorScheme(.dark)
        // A tapped invite link is the strongest possible statement of
        // intent — it wins over whatever step was showing (S16). The name
        // and portrait already typed are kept (the person exists once).
        .onChange(of: model.pendingInvite, initial: true) { _, pending in
            if let pending {
                withAnimation(RibbonMotion.settle) { step = .join(pending.token) }
            }
        }
    }

    // MARK: The mark

    // The mark, and one line. It holds for about 900 ms and then dissolves.
    // A reinstall restores silently underneath the hold (§6.10): if the
    // account already has a person, the thread ends here, in their room.
    private var markMoment: some View {
        VStack(spacing: 22) {
            WaveMark()
                .frame(width: 84, height: 84)
            Text(Copy.tagline)
                .font(RibbonType.display(22))
                .foregroundStyle(Palette.text)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Copy.tagline)
        .task {
            // The mark waits for whichever is longer — its own breath, or
            // the restore — and the restore is capped (§6.10). A tapped
            // link can replace the mark mid-hold; then this task is
            // cancelled and must not set a step underneath the join.
            let restore = Task { await model.restoreFromAccountIfPossible(within: .milliseconds(2400)) }
            try? await Task.sleep(for: .milliseconds(900))
            guard !Task.isCancelled else { return }
            let restored = await restore.value
            guard !Task.isCancelled else { return }
            if restored {
                onDone(nil)
                return
            }
            if model.me != nil {
                // A person already exists (the thread was re-entered):
                // straight to the invite, never to a second name.
                withAnimation(dissolve) { step = .invite }
            } else {
                withAnimation(dissolve) { step = .who }
            }
        }
    }

    private var dissolve: Animation {
        reduceMotion ? .easeOut(duration: 0.01) : .easeInOut(duration: 0.6)
    }

    // MARK: Who

    private var whoStep: some View {
        VStack(spacing: 26) {
            Spacer()
            Text(Copy.whoIsReading)
                .font(RibbonType.display(24))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            VStack(spacing: 16) {
                WayInButton(title: Copy.startARoom) {
                    withAnimation(RibbonMotion.settle) { step = .name }
                }
                QuietControl(title: Copy.haveAnInvite) {
                    withAnimation(RibbonMotion.settle) { step = .link }
                }
            }
            .padding(.horizontal, 56)
            Spacer()
            Spacer()
        }
        .threadColumn()
    }

    // MARK: The link (I have an invite)

    // The link is the whole mechanism (S15): opening it lands here via
    // the universal link — and pasting it works when the link was sent
    // somewhere this device can't tap it from. The system paste control
    // reads the clipboard without the paste banner.
    private var linkStep: some View {
        VStack(spacing: 22) {
            threadBack { step = .who }
            Spacer()
            Text(Copy.openTheLinkTheySent)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
                .accessibilityAddTraits(.isHeader)
            PasteButton(payloadType: String.self) { strings in
                // Parsed directly, not through the field: writing it there
                // would fire the field's onChange, which clears the miss
                // line the moment it was set.
                if let text = strings.first { accept(text) }
            }
            .labelStyle(.titleOnly)
            .buttonBorderShape(.capsule)
            .tint(Palette.chartreuse)
            .foregroundStyle(Palette.ground)
            RibbonTextField(prompt: Copy.orPasteItHere, text: $pastedInvite, centered: true, size: 15)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 40)
                .submitLabel(.go)
                .onSubmit { accept(pastedInvite) }
                .onChange(of: pastedInvite) { _, text in
                    // A pasted link is complete the moment it lands —
                    // don't make them find a go button.
                    pasteMissed = false
                    if AppModel.inviteToken(fromPasted: text) != nil { accept(text) }
                }
            if pasteMissed {
                Text(Copy.thatLinkIsntAnInvite)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
            }
            QuietControl(title: Copy.startARoomInstead) {
                withAnimation(RibbonMotion.settle) { step = .name }
            }
            Spacer()
            Spacer()
        }
        .threadColumn()
    }

    private func accept(_ text: String) {
        guard let token = AppModel.inviteToken(fromPasted: text) else {
            if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                pasteMissed = true
            }
            return
        }
        withAnimation(RibbonMotion.settle) { step = .join(token) }
    }

    // MARK: Name and portrait

    private var nameStep: some View {
        VStack(spacing: 24) {
            threadBack { step = .who }
            Spacer()
            PortraitPicker(item: $portraitItem, data: $portraitData)
            // The portrait is asked for with the one reason that is true.
            // Skipping it is silent: no control, nothing said (S17).
            Text(Copy.portraitReason)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)

            RibbonTextField(prompt: Copy.yourName, text: $name, centered: true, size: 20)
                .textContentType(.givenName)
                .focused($nameFocused)
                .padding(.horizontal, 40)
                .submitLabel(.done)
                .onSubmit(advanceFromName)

            WayInButton(title: Copy.thatsMe) { advanceFromName() }
                .padding(.horizontal, 80)
                .disabled(nameIsEmpty)
                .opacity(nameIsEmpty ? 0.3 : 1)
            Spacer()
            Spacer()
        }
        .threadColumn()
        .onAppear { nameFocused = true }
    }

    private var nameIsEmpty: Bool { name.trimmingCharacters(in: .whitespaces).isEmpty }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        nameFocused = false
        Task {
            await model.completeOnboarding(name: trimmed, portraitData: portraitData)
            withAnimation(RibbonMotion.settle) { step = .invite }
        }
    }

    // MARK: Invite

    // Send the link, or read on your own for now; the account happens in
    // here when it must, with its reason (§6.1 — inside whichever path).
    @ViewBuilder
    private var inviteStep: some View {
        if let room = model.currentRoom {
            VStack(spacing: 24) {
                Spacer()
                InviteStep(
                    room: room,
                    after: (Copy.pickABook, { withAnimation(RibbonMotion.settle) { step = .book } }),
                    later: (Copy.inviteLater, { withAnimation(RibbonMotion.settle) { step = .book } }),
                    skip: (Copy.readOnYourOwnForNow, { withAnimation(RibbonMotion.settle) { step = .book } }))
                .padding(.horizontal, 32)
                Spacer()
                Spacer()
            }
            .threadColumn()
        } else {
            Color.clear.onAppear { withAnimation(RibbonMotion.settle) { step = .name } }
        }
    }

    // MARK: The book

    // The chooser (S13) as the last step: choosing opens the book itself.
    @ViewBuilder
    private var bookStep: some View {
        if let room = model.currentRoom {
            BookChooserContent(
                room: room,
                heading: Copy.pickSomethingToRead,
                onChoose: { bookID, _ in
                    let reading = model.startReading(bookID: bookID, in: room)
                    onDone(reading)
                },
                onClose: nil)
        }
    }

    // MARK: Pieces

    private func threadBack(_ action: @escaping () -> Void) -> some View {
        HStack {
            BackControl { withAnimation(RibbonMotion.settle) { action() } }
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}

/// The portrait circle: a face, or the invitation to add one. Used by the
/// thread, the join flow, and You.
struct PortraitPicker: View {
    @Binding var item: PhotosPickerItem?
    @Binding var data: Data?
    var size: CGFloat = 96

    var body: some View {
        PhotosPicker(selection: $item, matching: .images) {
            ZStack {
                if let data, let image = UIImage(data: data) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: size, height: size)
                        .clipShape(Circle())
                } else {
                    Circle()
                        .fill(Palette.surface)
                        .overlay(Circle().strokeBorder(Palette.rule, lineWidth: 1))
                        .frame(width: size, height: size)
                    SmallCaps(Copy.addAPortrait, size: 11)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel(data == nil ? Copy.addAPortrait : Copy.changePortrait)
        .onChange(of: item) { _, item in
            Task {
                if let raw = try? await item?.loadTransferable(type: Data.self) {
                    data = downsampledJPEG(raw)
                }
            }
        }
    }
}

private extension View {
    /// The thread's column: one readable width, centered, filling the
    /// screen so the step sits where its spacers put it, and scrolling
    /// only when it must (largest type, a short phone with the keyboard
    /// up).
    func threadColumn() -> some View {
        GeometryReader { proxy in
            ScrollView {
                self
                    .frame(maxWidth: 420)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: max(proxy.size.height, 560))
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
        }
    }
}

/// Portraits are small; keep them that way on disk.
func downsampledJPEG(_ data: Data, maxSide: CGFloat = 512) -> Data? {
    guard let image = UIImage(data: data) else { return nil }
    let scale = min(1, maxSide / max(image.size.width, image.size.height))
    let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
    let renderer = UIGraphicsImageRenderer(size: size)
    let resized = renderer.image { _ in
        image.draw(in: CGRect(origin: .zero, size: size))
    }
    return resized.jpegData(compressionQuality: 0.82)
}
