import SwiftUI
import PhotosUI
import RibbonCore

// S17 — onboarding: a thread, not a screen. Four questions, no tour, no
// carousel, no permission prompts at launch, no account wall. The Wave and
// the tagline are the only branded moment in the product.

struct OnboardingFlow: View {
    @Environment(AppModel.self) private var model
    var onDone: () -> Void

    enum Step: Equatable {
        case mark
        case tour(Int) // 0: Vision, 1: Presence, 2: Notes, 3: Fire
        case intent
        case fromInvite
        case signIn
        case name
        case invite
        case join(UUID)
    }

    @State private var step: Step = .mark
    @State private var selectedIntent: Int = 0
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
                markMoment
                    .transition(.opacity)
            case .tour(let index):
                tourStep(index)
                    .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                             removal: .move(edge: .leading).combined(with: .opacity)))
            case .intent:
                intentStep
                    .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                             removal: .move(edge: .leading).combined(with: .opacity)))
            case .fromInvite:
                fromInviteStep
                    .transition(.opacity)
            case .signIn:
                signInStep
                    .transition(.opacity)
            case .name:
                nameStep
                    .transition(.opacity)
            case .invite:
                inviteStep
                    .transition(.opacity)
            case .join(let token):
                JoinFlow(
                    token: token,
                    onDone: onDone,
                    onStartInstead: {
                        // Declining the join forgets it — otherwise the
                        // pending token re-presents the join over the
                        // room they start instead.
                        model.pendingInvite = nil
                        withAnimation(RibbonMotion.settle) { step = .name }
                    })
                .id(token)
                .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .frame(maxWidth: 420)
        .frame(maxWidth: .infinity)
        .room()
        .preferredColorScheme(.dark)
        // A tapped invite link is the strongest possible statement of
        // intent — it wins over whatever step was showing (S16).
        .onChange(of: model.pendingInvite, initial: true) { _, pending in
            if let pending {
                withAnimation(RibbonMotion.settle) { step = .join(pending.token) }
            }
        }
    }

    // The mark, and one line. It holds for about 750 ms and then dissolves into the tour.
    private var markMoment: some View {
        VStack(spacing: 22) {
            WaveMark()
                .frame(width: 84, height: 84)
            Text(Copy.tagline)
                .font(RibbonType.display(22))
                .foregroundStyle(Palette.text)
        }
        .task {
            try? await Task.sleep(for: .milliseconds(750))
            withAnimation(.easeInOut(duration: 0.5)) { step = .tour(0) }
        }
    }

    // MARK: - Feature Tour Steps (Duolingo Style)
    private func tourStep(_ index: Int) -> some View {
        VStack(spacing: 0) {
            OnboardingProgressBar(
                currentStep: index,
                totalSteps: 6,
                onBack: index > 0 ? {
                    withAnimation(RibbonMotion.settle) { step = .tour(index - 1) }
                } : nil,
                onSignIn: {
                    withAnimation(RibbonMotion.settle) { step = .signIn }
                }
            )

            OnboardingTourCard(index: index)

            VStack(spacing: 12) {
                WayInButton(title: Copy.continueTour) {
                    withAnimation(RibbonMotion.settle) {
                        if index < 3 {
                            step = .tour(index + 1)
                        } else {
                            step = .intent
                        }
                    }
                }
                .padding(.horizontal, 40)

                if index == 0 {
                    QuietControl(title: Copy.alreadyHaveAccount) {
                        withAnimation(RibbonMotion.settle) { step = .signIn }
                    }
                } else {
                    QuietControl(title: Copy.haveAnInvite) {
                        withAnimation(RibbonMotion.settle) { step = .fromInvite }
                    }
                }
            }
            .padding(.bottom, 24)
        }
    }

    // MARK: - Intent Step ("Who will you read with?")
    private var intentStep: some View {
        VStack(spacing: 20) {
            OnboardingProgressBar(
                currentStep: 4,
                totalSteps: 6,
                onBack: { withAnimation(RibbonMotion.settle) { step = .tour(3) } },
                onSignIn: { withAnimation(RibbonMotion.settle) { step = .signIn } }
            )

            Spacer()

            Text(Copy.walkthroughIntentTitle)
                .font(RibbonType.display(24))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)

            VStack(spacing: 12) {
                intentOption(index: 0, title: Copy.walkthroughIntentSpouse, icon: "heart")
                intentOption(index: 1, title: Copy.walkthroughIntentFriend, icon: "person.2")
                intentOption(index: 2, title: Copy.walkthroughIntentGroup, icon: "person.3")
                intentOption(index: 3, title: Copy.walkthroughIntentSolo, icon: "book")
            }
            .padding(.horizontal, 28)

            Spacer()

            WayInButton(title: Copy.continueTour) {
                withAnimation(RibbonMotion.settle) { step = .name }
            }
            .padding(.horizontal, 40)
            .padding(.bottom, 24)
        }
    }

    private func intentOption(index: Int, title: String, icon: String) -> some View {
        Button {
            selectedIntent = index
            Haptics.light()
        } label: {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(selectedIntent == index ? Palette.chartreuse : Palette.muted)
                    .frame(width: 24)

                Text(title)
                    .font(RibbonType.ui(16))
                    .foregroundStyle(selectedIntent == index ? Palette.text : Palette.muted)

                Spacer()

                if selectedIntent == index {
                    Image(systemName: "checkmark")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Palette.chartreuse)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 15)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(selectedIntent == index ? Palette.raised : Palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(selectedIntent == index ? Palette.chartreuse.opacity(0.6) : Palette.rule, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    // The way back to a room you already have.
    private var signInStep: some View {
        VStack(spacing: 24) {
            HStack {
                Button {
                    withAnimation(RibbonMotion.settle) { step = .tour(0) }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Palette.muted)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)

            Spacer()
            Text(Copy.accountReason)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            SignInInline(
                onSignedIn: {
                    if model.me != nil {
                        onDone()
                    } else {
                        withAnimation(RibbonMotion.settle) { step = .name }
                    }
                },
                onCancel: {
                    withAnimation(RibbonMotion.settle) { step = .tour(0) }
                })
                .padding(.horizontal, 40)
            Spacer()
            Spacer()
        }
    }

    private var nameStep: some View {
        VStack(spacing: 24) {
            OnboardingProgressBar(
                currentStep: 5,
                totalSteps: 6,
                onBack: { withAnimation(RibbonMotion.settle) { step = .intent } },
                onSignIn: nil
            )

            Spacer()
            PhotosPicker(selection: $portraitItem, matching: .images) {
                ZStack {
                    if let portraitData, let image = UIImage(data: portraitData) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 96, height: 96)
                            .clipShape(Circle())
                    } else {
                        Circle()
                            .fill(Palette.surface)
                            .overlay(Circle().strokeBorder(Palette.rule, lineWidth: 1))
                            .frame(width: 96, height: 96)
                        SmallCaps(Copy.addAPortrait, size: 11)
                    }
                }
            }
            .onChange(of: portraitItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self) {
                        portraitData = downsampledJPEG(data)
                    }
                }
            }

            // The portrait is asked for with the one reason that is true.
            Text(Copy.portraitReason)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)

            TextField("", text: $name, prompt: Text(Copy.yourName).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(20))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .focused($nameFocused)
                .padding(.horizontal, 40)
                .submitLabel(.done)
                .onSubmit(advanceFromName)

            WayInButton(title: Copy.thatsMe) { advanceFromName() }
                .padding(.horizontal, 80)
                .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
            Spacer()
            Spacer()
        }
        .onAppear { nameFocused = true }
    }

    // The link is the whole mechanism (S15): opening it lands here via
    // the universal link — and pasting it works when the link was sent
    // somewhere this device can't tap it from.
    private var fromInviteStep: some View {
        VStack(spacing: 22) {
            Spacer()
            Text(Copy.openTheLink)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 48)
            TextField(
                "", text: $pastedInvite,
                prompt: Text(Copy.pasteInvitePrompt).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Palette.rule, lineWidth: 1))
                .padding(.horizontal, 48)
                .submitLabel(.go)
                .onSubmit(acceptPasted)
                .onChange(of: pastedInvite) { _, text in
                    // A pasted link is complete the moment it lands —
                    // don't make them find a go button.
                    pasteMissed = false
                    if AppModel.inviteToken(fromPasted: text) != nil { acceptPasted() }
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
    }

    private func acceptPasted() {
        guard let token = AppModel.inviteToken(fromPasted: pastedInvite) else {
            if !pastedInvite.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                pasteMissed = true
            }
            return
        }
        withAnimation(RibbonMotion.settle) { step = .join(token) }
    }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        Task {
            await model.completeOnboarding(name: trimmed, portraitData: portraitData)
            withAnimation(RibbonMotion.settle) { step = .invite }
        }
    }

    @State private var invite: Invite?

    private var inviteStep: some View {
        VStack(spacing: 24) {
            Spacer()
            Text(Copy.inviteSend)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)

            if model.remote != nil, !model.isSignedIn {
                // A link handed out signed-out is a dead link — the
                // account happens here, where it's honestly needed. The
                // quiet ways past (pick a book, invite later) stand.
                Text(Copy.inviteNeedsSignIn)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 48)
                SignInInline(onSignedIn: {
                    if let room = model.currentRoom {
                        let live = model.createInvite(for: room)
                        invite = live
                        Task { try? await model.pushInvite(live, for: room) }
                    }
                })
                .padding(.horizontal, 40)
            } else if let invite {
                ShareLink(item: invite.url()) {
                    Text(Copy.sendTheInvite)
                        .font(RibbonType.uiMedium(17))
                        .foregroundStyle(Palette.ground)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 13)
                        .background(Palette.chartreuse, in: Capsule())
                }
            }

            // You can read alone immediately while the invite is out — the
            // room's first-run state is the book chooser, so picking a book
            // and starting is the next thing that happens (§6.1).
            WayInButton(title: Copy.pickABook) { onDone() }
                .padding(.horizontal, 56)
            QuietControl(title: Copy.inviteLater) { onDone() }
            Spacer()
            Spacer()
        }
        .task(id: model.currentRoom?.id) {
            if let room = model.currentRoom {
                let live = model.createInvite(for: room)
                invite = live
                try? await model.pushInvite(live, for: room)
            }
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
