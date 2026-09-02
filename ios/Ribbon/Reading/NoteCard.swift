import SwiftUI
import RibbonCore

// A note, open (S04) — inline in the text, never a sheet. The single most
// important emotional moment in the app: reading what someone left.

struct NoteCard: View {
    @Environment(\.appModel) private var model
    let note: Note
    /// The reader's view of the author.
    let author: Person?
    let authorInk: Ink
    /// Nil where the note can't be acted on — someone else's, or an
    /// ember's record: then there is no menu at all.
    var onTakeBack: (() -> Void)?
    var onEdit: (() -> Void)?

    @State private var player = VoicePlayer()
    @State private var transcriptShown = false
    @State private var audioURL: URL?

    private var isMine: Bool { note.authorID == model.me?.id }
    private var canAct: Bool { isMine && (onTakeBack != nil || onEdit != nil) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                PortraitView(
                    person: author, ink: authorInk, size: 22,
                    image: author.flatMap { model.portrait($0.id) })
                SmallCaps(RibbonClock.phrase(for: note.createdAt), size: 12)
                Spacer()
            }

            switch note.kind {
            case .written:
                Text(note.body ?? "")
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.text)
                    .fixedSize(horizontal: false, vertical: true)
            case .voice:
                voiceBody
            }

            // When the note quotes the verse, the quote renders in the
            // author's translation, small — you see the words they were
            // looking at (§2.6).
            if let quote = authorTranslationQuote {
                Text(quote)
                    .font(RibbonType.scripture(13))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.vertical, 8)
        .contextMenu {
            if canAct {
                // Edit only makes sense for words; a voice note is taken
                // back or left as it is.
                if note.kind == .written, let onEdit {
                    Button(Copy.edit, action: onEdit)
                }
                if let onTakeBack {
                    Button(Copy.takeBack, role: .destructive, action: onTakeBack)
                }
            }
        }
        .task {
            if let path = note.audioPath {
                audioURL = await model.store.audioFileURL(path)
            }
        }
        .onDisappear { player.stop() }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(accessibilityText)
    }

    @ViewBuilder
    private var voiceBody: some View {
        VStack(alignment: .leading, spacing: 6) {
            WaveformView(
                peaks: note.waveform ?? [],
                ink: authorInk,
                progress: player.progress,
                isPlaying: player.isPlaying,
                onScrub: { player.scrub(to: $0) },
                onTap: togglePlayback)

            switch note.transcriptState {
            case .pending:
                SmallCaps(Copy.transcriptComing, size: 12)
            case .failed:
                HStack(spacing: 10) {
                    Text(Copy.noTranscript)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                    if model.speechRecognitionRefused {
                        // Refused once: a route to Settings, not a retry
                        // that can never succeed (S25).
                        Button {
                            if let url = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(url)
                            }
                        } label: {
                            SmallCaps(Copy.openSettings, size: 12, color: Palette.text)
                                .frame(minHeight: 44)
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            model.retryTranscript(note)
                        } label: {
                            Text(Copy.tryAgain)
                                .font(RibbonType.ui(14))
                                .foregroundStyle(Palette.text)
                                .frame(minHeight: 44)
                        }
                        .buttonStyle(.plain)
                    }
                }
            case .ready, nil:
                if let transcript = note.transcript {
                    Button {
                        withAnimation(RibbonMotion.settle) { transcriptShown.toggle() }
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            if transcriptShown {
                                Text(transcript)
                                    .font(RibbonType.ui(14))
                                    .foregroundStyle(Palette.muted)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .multilineTextAlignment(.leading)
                            }
                            SmallCaps(Copy.transcript, size: 12)
                                .frame(minHeight: 32)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Copy.transcript)
                    .accessibilityValue(transcript)
                    .accessibilityAddTraits(transcriptShown ? [.isSelected] : [])
                }
            }
        }
    }

    private func togglePlayback() {
        if player.isPlaying {
            player.pause()
        } else if player.progress > 0, player.progress < 1 {
            player.resume()
        } else if let audioURL {
            player.play(url: audioURL)
        }
    }

    private var authorTranslationQuote: String? {
        guard let author, author.translation != model.me?.translation else { return nil }
        guard let text = model.scripture.verseText(note.verse, translation: author.translation)
        else { return nil }
        return "“\(text)”"
    }

    private var accessibilityText: String {
        let name = author?.name ?? ""
        switch note.kind {
        case .written:
            return Copy.noteFromAuthor(Copy.writtenNoteKind, name, note.body)
        case .voice:
            return Copy.noteFromAuthor(Copy.voiceNoteKind, name, nil)
        }
    }
}

/// The waveform, drawn in the author's ink, filling left-to-right as it
/// plays. No timer, no duration readout — a duration is a count and it
/// makes people self-conscious about how long they talked. The bars fit
/// the measure whatever the peak count; scrubbing maps to the same width.
struct WaveformView: View {
    var peaks: [Float]
    var ink: Ink
    var progress: Double
    var isPlaying: Bool
    var onScrub: (Double) -> Void
    var onTap: () -> Void

    var body: some View {
        GeometryReader { geo in
            let width = max(1, geo.size.width)
            let bars = displayPeaks
            let barWidth = max(1.2, min(3, width / CGFloat(bars.count) * 0.62))
            let gap = (width - barWidth * CGFloat(bars.count)) / CGFloat(max(1, bars.count - 1))
            HStack(alignment: .center, spacing: max(0.5, gap)) {
                ForEach(bars.indices, id: \.self) { index in
                    let played = Double(index) / Double(max(1, bars.count)) <= progress
                    Capsule()
                        .fill(ink.color.opacity(played ? 1 : 0.35))
                        .frame(width: barWidth, height: max(3, CGFloat(bars[index]) * 30))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .gesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { value in
                        onScrub(max(0, min(1, value.location.x / width)))
                    })
        }
        .frame(height: 34)
        .accessibilityElement()
        .accessibilityLabel(isPlaying ? Copy.pauseTheVoiceNote : Copy.playTheVoiceNote)
        .accessibilityValue(isPlaying ? Copy.audioPlaying : Copy.audioPaused)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onTap() }
        // Scrubbing, for VoiceOver: a step back or forward (§11).
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onScrub(min(1, progress + 0.1))
            case .decrement: onScrub(max(0, progress - 0.1))
            @unknown default: break
            }
        }
    }

    private var displayPeaks: [Float] {
        peaks.isEmpty ? Array(repeating: 0.2, count: 40) : peaks
    }
}
