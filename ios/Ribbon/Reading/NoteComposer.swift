import SwiftUI
import RibbonCore

// Leaving a note (S05): the toolbar rises from the bottom after the
// long-press — the ink swatches, write, speak. Highlighting (S06) shares
// the toolbar. Dismissed by tapping anywhere in the text.

enum ComposerMode: Equatable {
    case toolbar
    case writing
    case speaking
}

struct LeaveToolbar: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let range: VerseRange
    /// Paused rooms: only highlight shows, greyed, with one line (S02).
    let roomPaused: Bool
    var onHighlight: (Ink) -> Void
    var onWrite: () -> Void
    var onSpeak: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            if roomPaused {
                SmallCaps(Copy.newNotesNeedTheRoom, size: 12)
            }
            HStack(spacing: 14) {
                // Two people: eight swatches, pick per highlight, last-used
                // pre-selected. Three or more: one swatch — yours (§4.5).
                // Paused: only highlight shows, greyed and inert — the room
                // reads everything and writes nothing (S02, §08).
                if let mine = model.inkForNewHighlight(in: room) {
                    InkSwatch(ink: mine, isSelected: !roomPaused) {
                        if !roomPaused { onHighlight(mine) }
                    }
                    .opacity(roomPaused ? 0.35 : 1)
                } else {
                    ForEach(Ink.allCases, id: \.self) { ink in
                        InkSwatch(ink: ink, isSelected: !roomPaused && ink == model.lastUsedInk) {
                            if !roomPaused { onHighlight(ink) }
                        }
                        .opacity(roomPaused ? 0.35 : 1)
                    }
                }

                if !roomPaused {
                    Rectangle().fill(Palette.rule).frame(width: 1, height: 20)

                    Button(action: onWrite) {
                        SmallCaps(Copy.write, size: 13, color: Palette.text)
                    }
                    .buttonStyle(.plain)

                    Button(action: onSpeak) {
                        SmallCaps(Copy.speak, size: 13, color: Palette.text)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 18)
            .frame(height: 52)
            .ribbonGlass(in: Capsule(), interactive: true)
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

struct InkSwatch: View {
    var ink: Ink
    var isSelected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Circle()
                .fill(ink.color)
                .frame(width: 20, height: 20)
                .overlay {
                    if isSelected {
                        Circle().strokeBorder(Palette.text.opacity(0.7), lineWidth: 1.4)
                            .padding(-3)
                    }
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(ink.displayName) ink")
    }
}

/// Write (S05): a composer sized to the note, growing as you type, anchored
/// above the keyboard. No formatting controls, no title, no character
/// limit shown. Save is a single control; no draft state.
struct WriteComposer: View {
    let verse: VerseAddress
    var initialText: String = ""
    /// Changes when the note being edited changes, so a switch mid-compose
    /// starts from that note's own words rather than a stale draft.
    var identity: String = ""
    var onSave: (String) -> Void
    var onCancel: () -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        composerBody.id(identity)
    }

    private var composerBody: some View {
        VStack(alignment: .leading, spacing: 10) {
            SmallCaps(verse.formatted, size: 12)
            TextField("", text: $text, axis: .vertical)
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .lineLimit(1...12)
                .focused($focused)
            HStack {
                Button(Copy.takeBack) { onCancel() }
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .buttonStyle(.plain)
                Spacer()
                Button {
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { return }
                    onSave(trimmed)
                } label: {
                    SmallCaps(Copy.leaveIt, size: 13, color: Palette.chartreuse)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle().inset(by: -8))
                }
                .buttonStyle(.plain)
                // ⌘↩ leaves the note — the convention a hardware-keyboard
                // iPad reader expects.
                .keyboardShortcut(.return, modifiers: .command)
            }
        }
        .padding(16)
        .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.rule, lineWidth: 1))
        .padding(.horizontal, 16)
        .readableColumn()
        .onAppear {
            text = initialText
            focused = true
        }
    }
}

/// Speak (S05): press and hold. The waveform draws live in your ink.
/// Release keeps it; drag away discards, with the waveform receding rather
/// than a confirmation.
struct SpeakControl: View {
    let ink: Ink
    let recorder: VoiceRecorder
    var onKeep: (URL, [Float]) -> Void
    var onDismiss: () -> Void

    @Environment(AppModel.self) private var model
    @State private var draggedAway = false
    @State private var deniedRoute = false
    @State private var storageFull = false

    var body: some View {
        VStack(spacing: 10) {
            if storageFull {
                // S25: a count about a device, not about a person.
                Text(Copy.noRoomOnPhone(5))
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
            } else if deniedRoute {
                // Refused once: one route to Settings, then never asked
                // again (S25).
                Text(Copy.micNeeded)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    SmallCaps(Copy.openSettings, size: 13, color: Palette.chartreuse)
                }
                .buttonStyle(.plain)
            } else {
                HStack(spacing: 2) {
                    let peaks = recorder.livePeaks.suffix(80)
                    ForEach(Array(peaks.enumerated()), id: \.offset) { _, peak in
                        Capsule()
                            .fill(ink.color.opacity(draggedAway ? 0.25 : 1))
                            .frame(width: 2.5, height: max(3, CGFloat(peak) * 36))
                    }
                }
                .frame(height: 44)
                .frame(maxWidth: .infinity)
                .animation(.linear(duration: 0.05), value: recorder.livePeaks.count)

                SmallCaps(
                    draggedAway ? "let go to discard" : "release to leave it",
                    size: 12,
                    color: draggedAway ? Palette.muted : Palette.text.opacity(0.7))
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 14)
        .ribbonGlass(in: RoundedRectangle(cornerRadius: 18))
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    draggedAway = abs(value.translation.height) > 70 || abs(value.translation.width) > 90
                }
                .onEnded { _ in
                    if draggedAway {
                        recorder.discard()
                        onDismiss()
                    } else if let kept = recorder.finish() {
                        onKeep(kept.url, kept.waveform)
                    } else {
                        onDismiss()  // a mis-touch, discarded silently
                    }
                })
        .onDisappear {
            // The composer leaving the screen for any reason — a tap in
            // the text, the book closing — ends the recording. The mic is
            // never left hot.
            if recorder.isRecording {
                recorder.discard()
            }
        }
        .task {
            if let free = LocalStore.freeMegabytes(), free < 20 {
                storageFull = true
                return
            }
            if recorder.microphoneUndecided {
                let granted = await recorder.requestAccess()
                if !granted {
                    deniedRoute = true
                    return
                }
            } else if !recorder.hasMicrophoneAccess {
                deniedRoute = true
                return
            }
            let url = await model.store.audioFileURL("\(UUID().uuidString).m4a")
            recorder.begin(to: url)
        }
    }
}
