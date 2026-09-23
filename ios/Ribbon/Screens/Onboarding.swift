import SwiftUI
import PhotosUI
import RibbonCore

// S17 — onboarding: a thread, not a screen. Four questions, no tour, no
// carousel, no permission prompts at launch, no account wall. The Wave and
// the tagline are the only branded moment in the product.

struct OnboardingFlow: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
    /// The name has gone to the model and the room is being made. A second
    /// tap on "That's me" while that is under way must not make a second
    /// person — with a portrait there is an await before the first one
    /// exists, and a quick finger fits inside it.
    @State private var committing = false
    /// Which way the thread is moving, read by the steps' transition at the
    /// moment it runs (see `ThreadMove`).
    @State private var direction = ThreadDirection()
    @FocusState private var nameFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            // One bar for the whole thread, standing still while the steps
            // move under it. Each step used to carry its own, so the bar
            // slid away with the card it was counting, and its fill was
            // never once seen to fill.
            if let progress {
                OnboardingProgressBar(
                    currentStep: progress.current,
                    totalSteps: 6,
                    onBack: progress.back,
                    onSignIn: progress.signIn)
                .transition(.opacity)
            }
            ZStack {
                steps
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                go(.join(pending.token))
            }
        }
    }

    @ViewBuilder
    private var steps: some View {
        switch step {
        case .mark:
            markMoment
                .transition(.opacity)
        case .tour(let index):
            tourStep(index)
                .transition(moving)
        case .intent:
            intentStep
                .transition(moving)
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
                    go(.name)
                })
            .id(token)
            .transition(.opacity)
        }
    }

    // MARK: - Moving along the thread

    /// Onward, or back: `forward: false` for every way back.
    private func go(_ next: Step, forward: Bool = true) {
        direction.sign = forward ? 1 : -1
        withAnimation(RibbonMotion.settle) { step = next }
    }

    /// A tour card or the intent step comes in from the side the thread is
    /// moving toward and leaves by the other — so going back looks like
    /// going back. Under reduce motion they only fade (§11).
    private var moving: AnyTransition {
        reduceMotion ? .opacity : AnyTransition(ThreadMove(direction: direction))
    }

    /// The bar over the steps that have one — the tour, the intent, the
    /// name — with the way back and the way to sign in each of them offers.
    private var progress: (current: Int, back: (() -> Void)?, signIn: (() -> Void)?)? {
        switch step {
        case .tour(let index):
            return (
                index,
                index > 0 ? { go(.tour(index - 1), forward: false) } : nil,
                { go(.signIn) })
        case .intent:
            return (4, { go(.tour(3), forward: false) }, { go(.signIn) })
        case .name:
            return (5, { go(.intent, forward: false) }, nil)
        default:
            return nil
        }
    }

    // The mark, and one line. It holds for about 750 ms and then dissolves
    // into the tour — a dissolve, in place: the first card does not slide.
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
            guard !Task.isCancelled else { return }
            direction.sign = 0
            withAnimation(RibbonMotion.settle) { step = .tour(0) }
        }
    }

    // MARK: - Feature Tour Steps (Duolingo Style)
    private func tourStep(_ index: Int) -> some View {
        VStack(spacing: 0) {
            OnboardingTourCard(index: index)

            VStack(spacing: 12) {
                WayInButton(title: Copy.continueTour) {
                    go(index < 3 ? .tour(index + 1) : .intent)
                }
                .padding(.horizontal, 40)

                if index == 0 {
                    QuietControl(title: Copy.alreadyHaveAccount) { go(.signIn) }
                } else {
                    QuietControl(title: Copy.haveAnInvite) { go(.fromInvite) }
                }
            }
            .padding(.bottom, 24)
        }
    }

    // MARK: - Intent Step ("Who will you read with?")
    private var intentStep: some View {
        VStack(spacing: 20) {
            Spacer()

            Text(Copy.walkthroughIntentTitle)
                .font(RibbonType.display(24))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)

            // Four answers in one group of tiles, all in ivory: the
            // unchosen ones are not lesser, they are simply not chosen. No
            // icons — the words are the whole of each.
            SettingsGroup {
                SettingChoice(Copy.walkthroughIntentSpouse, chosen: selectedIntent == 0) { choose(0) }
                SettingChoice(Copy.walkthroughIntentFriend, chosen: selectedIntent == 1) { choose(1) }
                SettingChoice(Copy.walkthroughIntentGroup, chosen: selectedIntent == 2) { choose(2) }
                SettingChoice(Copy.walkthroughIntentSolo, chosen: selectedIntent == 3) { choose(3) }
            }
            .padding(.horizontal, 28)

            Spacer()

            WayInButton(title: Copy.continueTour) { go(.name) }
                .padding(.horizontal, 40)
                .padding(.bottom, 24)
        }
    }

    /// No tick under the finger: §9.3 lists every haptic the product has,
    /// and "no selection ticks" is on it by name. The check drawing itself
    /// in is the answer to the tap.
    private func choose(_ index: Int) {
        selectedIntent = index
    }

    // The way back to a room you already have.
    private var signInStep: some View {
        VStack(spacing: 24) {
            HStack {
                BackChevron { go(.tour(0), forward: false) }
                Spacer()
            }
            .padding(.horizontal, 12)
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
                        go(.name)
                    }
                },
                onCancel: { go(.tour(0), forward: false) })
                .padding(.horizontal, 40)
            Spacer()
            Spacer()
        }
    }

    private var nameStep: some View {
        VStack(spacing: 24) {
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
                // The face you chose settles into the circle rather than
                // replacing it between two frames.
                .animation(RibbonMotion.arrive, value: portraitData)
            }
            .buttonStyle(.pressable)
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

            CentredTextField(text: $name, prompt: Copy.yourName, submitLabel: .done, contentType: .name, onSubmit: advanceFromName)
                .focused($nameFocused)
                .padding(.horizontal, 40)

            WayInButton(title: Copy.thatsMe) { advanceFromName() }
                .padding(.horizontal, 80)
                .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
                // It wakes with the first letter, rather than switching on.
                .animation(RibbonMotion.arrive, value: name.trimmingCharacters(in: .whitespaces).isEmpty)
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
            CentredTextField(text: $pastedInvite, prompt: Copy.pasteInvitePrompt, submitLabel: .go, keyboard: .URL, onSubmit: acceptPasted)
                .padding(.horizontal, 48)
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
                    .transition(.opacity)
            }
            QuietControl(title: Copy.startARoomInstead) { go(.name) }
            Spacer()
            Spacer()
        }
        .animation(RibbonMotion.arrive, value: pasteMissed)
    }

    private func acceptPasted() {
        guard let token = AppModel.inviteToken(fromPasted: pastedInvite) else {
            if !pastedInvite.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                pasteMissed = true
            }
            return
        }
        go(.join(token))
    }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, !committing else { return }
        committing = true
        Task {
            await model.completeOnboarding(name: trimmed, portraitData: portraitData)
            go(.invite)
            committing = false
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
                        .contentShape(Capsule())
                }
                .buttonStyle(.pressable)
                .simultaneousGesture(TapGesture().onEnded { model.inviteWasHandedOut(invite) })
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

/// Which way the onboarding thread is moving: 1 on, -1 back, 0 a dissolve
/// in place. A reference, deliberately: a transition leaving the screen is
/// the one drawn with the step as it last was, so a direction held in view
/// state reaches the outgoing step one move late — the bug that sent every
/// step off to the left, going back included. Read through a reference,
/// it is the direction of the move actually being made.
final class ThreadDirection {
    var sign: CGFloat = 1
}

/// A step arriving from the side the thread moves toward and leaving by
/// the other, a whole width, fading as it goes.
private struct ThreadMove: Transition {
    let direction: ThreadDirection

    func body(content: Content, phase: TransitionPhase) -> some View {
        content
            .visualEffect { [across = shift(at: phase)] effect, proxy in
                effect.offset(x: across * proxy.size.width)
            }
            .opacity(phase.isIdentity ? 1 : 0)
    }

    /// Where the step is, in widths: in from the side the thread is going
    /// to, out by the side it came from.
    private func shift(at phase: TransitionPhase) -> CGFloat {
        switch phase {
        case .willAppear: return direction.sign
        case .didDisappear: return -direction.sign
        default: return 0
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
