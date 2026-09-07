import SwiftUI
import RibbonCore

// A note, open (S04) — inline in the text, never a sheet. The single most
// important emotional moment in the app: reading what someone left.

struct NoteCard: View {
    @Environment(AppModel.self) private var model
    let note: Note
    /// The reader's view of the author.
    let author: Person?
    let authorInk: Ink
    var onTakeBack: () -> Void
    var onEdit: () -> Void

    @State private var player = VoicePlayer()
    @State private var transcriptShown = false
    @State private var audioURL: URL?

    private var isMine: Bool { note.authorID == model.me?.id }

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
            if isMine {
                Button(Copy.edit, action: onEdit)
                Button(Copy.takeBack, role: .destructive, action: onTakeBack)
            }
        }
        .task {
            if let path = note.audioPath {
                audioURL = await model.store.audioFileURL(path)
            }
        }
        .onDisappear { player.stop() }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityText)
    }

    @ViewBuilder
    private var voiceBody: some View {
        VStack(alignment: .leading, spacing: 6) {
            WaveformView(
                peaks: note.waveform ?? [],
                ink: authorInk,
                progress: player.progress,
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
                    Button(Copy.tryAgain) { model.retryTranscript(note) }
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.text)
                        .buttonStyle(.plain)
                }
            case .ready, nil:
                if let transcript = note.transcript {
                    Button {
                        withAnimation(RibbonMotion.settle) { transcriptShown.toggle() }
                    } label: {
                        if transcriptShown {
                            Text(transcript)
                                .font(RibbonType.ui(14))
                                .foregroundStyle(Palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                                .multilineTextAlignment(.leading)
                        } else {
                            SmallCaps(Copy.transcript, size: 12)
                        }
                    }
                    .buttonStyle(.plain)
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
            return "Note from \(name). \(note.body ?? "")"
        case .voice:
            return "Voice note from \(name). \(note.transcript ?? "")"
        }
    }
}

/// The waveform, drawn in the author's ink, filling left-to-right as it
/// plays. No timer, no duration readout — a duration is a count and it
/// makes people self-conscious about how long they talked.
struct WaveformView: View {
    var peaks: [Float]
    var ink: Ink
    var progress: Double
    var onScrub: (Double) -> Void
    var onTap: () -> Void

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            HStack(alignment: .center, spacing: 1.5) {
                let bars = displayPeaks
                ForEach(bars.indices, id: \.self) { index in
                    let played = Double(index) / Double(max(1, bars.count)) <= progress
                    Capsule()
                        .fill(ink.color.opacity(played ? 1 : 0.35))
                        .frame(width: 2, height: max(3, CGFloat(bars[index]) * 30))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .gesture(
                DragGesture(minimumDistance: 8)
                    .onChanged { value in
                        onScrub(max(0, min(1, value.location.x / max(1, width))))
                    })
        }
        .frame(height: 34)
        .accessibilityLabel("Play the voice note")
        .accessibilityAddTraits(.isButton)
    }

    private var displayPeaks: [Float] {
        peaks.isEmpty ? Array(repeating: 0.2, count: 40) : peaks
    }
}
