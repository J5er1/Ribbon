import SwiftUI
import PhotosUI
import RibbonCore

// S17 — onboarding: a thread, not a screen. Four questions, no tour, no
// carousel, no permission prompts at launch, no account wall. The Wave and
// the tagline are the only branded moment in the product.

struct OnboardingFlow: View {
    @Environment(AppModel.self) private var model
    var onDone: () -> Void

    enum Step {
        case mark
        case who
        case name
        case invite
    }

    @State private var step: Step = .mark
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var portraitData: Data?
    @FocusState private var nameFocused: Bool

    var body: some View {
        ZStack {
            switch step {
            case .mark:
                markMoment
                    .transition(.opacity)
            case .who:
                whoStep
                    .transition(.opacity)
            case .name:
                nameStep
                    .transition(.opacity)
            case .invite:
                inviteStep
                    .transition(.opacity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .room()
        .preferredColorScheme(.dark)
    }

    // The mark, and one line. It holds for about 900 ms and then dissolves.
    private var markMoment: some View {
        VStack(spacing: 22) {
            WaveMark()
                .frame(width: 84, height: 84)
            Text(Copy.tagline)
                .font(RibbonType.display(22))
                .foregroundStyle(Palette.text)
        }
        .task {
            try? await Task.sleep(for: .milliseconds(900))
            withAnimation(.easeInOut(duration: 0.6)) { step = .who }
        }
    }

    private var whoStep: some View {
        VStack(spacing: 26) {
            Spacer()
            Text(Copy.whoIsReading)
                .font(RibbonType.display(24))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
            VStack(spacing: 16) {
                WayInButton(title: Copy.startARoom) {
                    withAnimation(RibbonMotion.settle) { step = .name }
                }
                // Accepting an invite (S16) arrives with the deep-link
                // path; the answer here routes to the same name step.
                QuietControl(title: Copy.haveAnInvite) {
                    withAnimation(RibbonMotion.settle) { step = .name }
                }
            }
            .padding(.horizontal, 56)
            Spacer()
            Spacer()
        }
    }

    private var nameStep: some View {
        VStack(spacing: 26) {
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

            WayInButton(title: "That's me") { advanceFromName() }
                .padding(.horizontal, 80)
                .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
            Spacer()
            Spacer()
        }
        .onAppear { nameFocused = true }
    }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        Task {
            await model.completeOnboarding(name: trimmed, portraitData: portraitData)
            withAnimation(RibbonMotion.settle) { step = .invite }
        }
    }

    private var inviteStep: some View {
        VStack(spacing: 24) {
            Spacer()
            Text(Copy.inviteSend)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)

            if let room = model.currentRoom {
                ShareLink(item: model.createInvite(for: room).url()) {
                    Text("Send the invite")
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
