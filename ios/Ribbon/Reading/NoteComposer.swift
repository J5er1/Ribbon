import SwiftUI
import RibbonCore

// Leaving a note (S05): the toolbar rises from the bottom after the
// long-press — the ink swatches, write, speak. Highlighting (S06) shares
// the toolbar. Dismissed by tapping anywhere in the text. Speak is press
// and hold, on the toolbar itself: the recording begins under the finger,
// the waveform draws live in your ink, release keeps it, drag away
// discards it.

struct LeaveToolbar: View {
    @Environment(\.appModel) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let room: Room
    let range: VerseRange
    /// Paused rooms: only highlight shows, greyed, with one line (S02).
    let roomPaused: Bool
    let recorder: VoiceRecorder
    /// A highlight already on the lifted verse, if any — its author is
    /// named here, and it can be removed if it's yours (S06).
    var existingHighlight: Highlight?
    var onHighlight: (Ink) -> Void
    var onRemoveHighlight: (Highlight) -> Void
    var onWrite: () -> Void
    var onSpeakKept: (URL, [Float]) -> Void
    var onPickInk: () -> Void

    @State private var recording = false
    /// The finger is still down: set on press, cleared on release, and
    /// checked once the microphone is ready — a quick tap, or the
    /// permission alert lifting the finger, must not start a recording
    /// with nobody holding (deviation 21).
    @State private var wantsRecording = false
    @State private var draggedAway = false
    @State private var deniedRoute = false
    @State private var storageShort: Int?

    var body: some View {
        VStack(spacing: 8) {
            if roomPaused {
                SmallCaps(Copy.newNotesNeedTheRoom, size: 12)
            }
            if let existingHighlight, let author = model.person(existingHighlight.authorID) {
                // A small label naming who made it, and remove if it's
                // yours (S06). Here, in the chrome — never glass over the
                // verse.
                HStack(spacing: 10) {
                    InkDot(ink: existingHighlight.ink)
                    Text(author.name)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.text)
                    if model.isFromWhenTheRoomWasTwo(existingHighlight, in: room) {
                        SmallCaps(Copy.inksFromWhenTheRoomWasTwo, size: 11)
                    }
                    if existingHighlight.authorID == model.me?.id {
                        QuietControl(title: Copy.remove) { onRemoveHighlight(existingHighlight) }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .ribbonGlass(in: Capsule())
                .accessibilityElement(children: .combine)
            }
            if recording || deniedRoute || storageShort != nil {
                speakSurface
            }
            toolbar
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
        .onDisappear {
            // The composer leaving the screen for any reason — a tap in
            // the text, the book closing — ends the recording. The mic is
            // never left hot.
            if recorder.isRecording { recorder.discard() }
        }
        .onChange(of: recorder.interrupted) { _, interrupted in
            // Interrupted by a call: what was captured is kept and
            // offered, not thrown away (S05).
            guard interrupted, recording else { return }
            recording = false
            if let kept = recorder.finish() { onSpeakKept(kept.url, kept.waveform) }
        }
    }

    // MARK: The toolbar

    private var toolbar: some View {
        let identity = model.inkIsIdentity(in: room)
        let mine = model.inkForNewHighlight(in: room)
        return HStack(spacing: 14) {
            // Two people: eight swatches, pick per highlight, last-used
            // pre-selected. Three or more: one swatch — yours; none picked
            // yet → the invitation to pick (§4.5, §6.7). Paused: only
            // highlight shows, greyed and inert — the room reads
            // everything and writes nothing (S02, §08).
            if identity {
                if let mine {
                    InkSwatch(ink: mine, isSelected: !roomPaused) {
                        if !roomPaused { onHighlight(mine) }
                    }
                    .opacity(roomPaused ? 0.35 : 1)
                } else {
                    Button(action: onPickInk) {
                        SmallCaps(Copy.pickYourInk, size: 13, color: Palette.text)
                            .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: dynamicTypeSize.isAccessibilitySize ? 18 : 12) {
                        ForEach(Ink.allCases, id: \.self) { ink in
                            InkSwatch(ink: ink, isSelected: !roomPaused && ink == model.lastUsedInk) {
                                if !roomPaused { onHighlight(ink) }
                            }
                            .opacity(roomPaused ? 0.35 : 1)
                        }
                    }
                }
                .frame(maxWidth: dynamicTypeSize.isAccessibilitySize ? 180 : 232)
            }

            if !roomPaused {
                Rectangle().fill(Palette.rule).frame(width: 1, height: 20)

                Button(action: onWrite) {
                    SmallCaps(Copy.write, size: 13, color: Palette.text)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Copy.leaveANote)

                speakHold
            }
        }
        .padding(.horizontal, 18)
        .frame(minHeight: 52)
        .ribbonGlass(in: Capsule(), interactive: true)
    }

    /// Speak: press and hold. The recording starts under the finger, not
    /// on a tap, so a hesitation never keeps a note of silence. VoiceOver
    /// gets a start/stop action instead of the hold (§11 motor).
    private var speakHold: some View {
        SmallCaps(recording ? Copy.releaseToLeaveIt : Copy.speak, size: 13,
                  color: recording ? Palette.chartreuse : Palette.text)
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !recording { beginRecording() }
                        draggedAway = abs(value.translation.height) > 70 || abs(value.translation.width) > 90
                    }
                    .onEnded { _ in endRecording(discarding: draggedAway) })
            .accessibilityLabel(recording ? Copy.stopRecording : Copy.recordAVoiceNote)
            .accessibilityHint(recording ? "" : Copy.holdToSpeak)
            .accessibilityAddTraits(.isButton)
            .accessibilityAction {
                if recording { endRecording(discarding: false) } else { beginRecording() }
            }
    }

    // MARK: The recording surface

    @ViewBuilder
    private var speakSurface: some View {
        VStack(spacing: 10) {
            if let storageShort {
                // S25: a count about a device, not about a person.
                Text(Copy.noRoomOnPhone(storageShort))
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
                        .frame(minHeight: 44)
                }
                .buttonStyle(.plain)
            } else {
                let ink = model.inkForNewHighlight(in: room) ?? model.lastUsedInk
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
                .accessibilityHidden(true)

                SmallCaps(
                    draggedAway ? Copy.letGoToDiscard : Copy.releaseToLeaveIt,
                    size: 12,
                    color: draggedAway ? Palette.muted : Palette.text.opacity(0.7))
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 14)
        .ribbonGlass(in: RoundedRectangle(cornerRadius: 18))
        .transition(.opacity)
    }

    private func beginRecording() {
        guard !roomPaused, !recording, !deniedRoute else { return }
        if let free = LocalStore.freeMegabytes(), free < 20 {
            storageShort = 20
            return
        }
        wantsRecording = true
        Task {
            if recorder.microphoneUndecided {
                let granted = await recorder.requestAccess()
                if !granted { deniedRoute = true; return }
            } else if !recorder.hasMicrophoneAccess {
                deniedRoute = true
                return
            }
            let url = await model.store.audioFileURL("\(UUID().uuidString).m4a")
            guard wantsRecording else { return }
            recorder.begin(to: url)
            withAnimation(RibbonMotion.arrive) { recording = recorder.isRecording }
        }
    }

    private func endRecording(discarding: Bool) {
        wantsRecording = false
        guard recording else { return }
        withAnimation(RibbonMotion.arrive) { recording = false }
        draggedAway = false
        if discarding {
            recorder.discard()
        } else if let kept = recorder.finish() {
            onSpeakKept(kept.url, kept.waveform)
        }
        // A recording under ~1 s is discarded silently as a mis-touch.
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
                .frame(width: 28, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Copy.inkSwatch(ink.displayName))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
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
                .accessibilityLabel(Copy.leaveANote)
            HStack {
                // Discarding a draft is not taking a note back (§10.2 —
                // "take back" is for a note already left).
                Button(action: onCancel) {
                    SmallCaps(Copy.discard, size: 13, color: Palette.muted)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle().inset(by: -8))
                }
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
