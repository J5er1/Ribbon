import AVFoundation
import Foundation
import Observation

// Voice notes (§4.4, S05). Speak is press-and-hold; the waveform draws live
// in your ink; release keeps it, drag away discards. A recording under ~1 s
// is discarded silently as a mis-touch. A recording interrupted by a call
// is kept and offered, not thrown away.

@MainActor
@Observable
final class VoiceRecorder: NSObject, AVAudioRecorderDelegate {
    private var recorder: AVAudioRecorder?
    private var meterTimer: Timer?

    /// Live, normalized peaks while recording — the waveform in your ink.
    /// Ring-buffered for display; the full take accumulates separately so
    /// a long note's stored waveform covers the whole recording.
    private(set) var livePeaks: [Float] = []
    private var allPeaks: [Float] = []
    private(set) var isRecording = false
    /// Set when a system interruption (a call) ended the recording early:
    /// what was captured is kept and offered.
    private(set) var interrupted = false

    private var fileURL: URL?
    private var startedAt: Date?

    var hasMicrophoneAccess: Bool {
        AVAudioApplication.shared.recordPermission == .granted
    }

    var microphoneUndecided: Bool {
        AVAudioApplication.shared.recordPermission == .undetermined
    }

    /// Ask in context, once (§6.1). If refused, the speak control routes to
    /// Settings and never asks again (S25).
    func requestAccess() async -> Bool {
        await AVAudioApplication.requestRecordPermission()
    }

    func begin(to url: URL) {
        guard !isRecording else { return }
        interrupted = false
        livePeaks = []
        allPeaks = []
        fileURL = url
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
            let settings: [String: Any] = [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVSampleRateKey: 44_100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
            ]
            let recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder.delegate = self
            recorder.isMeteringEnabled = true
            recorder.record()
            self.recorder = recorder
            startedAt = Date()
            isRecording = true
            meterTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.sampleMeter() }
            }
        } catch {
            recorder = nil
        }
    }

    private func sampleMeter() {
        guard let recorder, recorder.isRecording else { return }
        recorder.updateMeters()
        let db = recorder.averagePower(forChannel: 0)
        // Map -50…0 dB to 0…1 with a gentle floor so silence still draws a
        // thread.
        let level = max(0, min(1, (db + 50) / 50))
        let peak = max(0.06, pow(level, 1.6))
        livePeaks.append(peak)
        allPeaks.append(peak)
        if livePeaks.count > 600 { livePeaks.removeFirst(livePeaks.count - 600) }
    }

    /// Release to keep. Returns the file URL and a downsampled waveform, or
    /// nil for a mis-touch (< 1 s).
    func finish() -> (url: URL, waveform: [Float])? {
        guard let recorder, let fileURL else { return nil }
        stopMetering()
        recorder.stop()
        self.recorder = nil
        isRecording = false
        let duration = startedAt.map { Date().timeIntervalSince($0) } ?? 0
        if duration < 1.0 {
            try? FileManager.default.removeItem(at: fileURL)
            return nil
        }
        return (fileURL, Self.downsample(allPeaks, to: 96))
    }

    /// Drag away to discard — the waveform recedes rather than a dialog
    /// appearing.
    func discard() {
        stopMetering()
        recorder?.stop()
        if let fileURL { try? FileManager.default.removeItem(at: fileURL) }
        recorder = nil
        isRecording = false
        livePeaks = []
        allPeaks = []
    }

    private func stopMetering() {
        meterTimer?.invalidate()
        meterTimer = nil
    }

    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        Task { @MainActor in
            if self.isRecording {
                // Ended underneath us — an interruption. Keep what landed.
                self.stopMetering()
                self.isRecording = false
                self.interrupted = true
            }
        }
    }

    static func downsample(_ peaks: [Float], to count: Int) -> [Float] {
        guard peaks.count > count else { return peaks }
        let stride = Double(peaks.count) / Double(count)
        return (0..<count).map { i in
            let start = Int(Double(i) * stride)
            let end = min(peaks.count, Int(Double(i + 1) * stride) + 1)
            return peaks[start..<end].max() ?? 0
        }
    }
}

/// Playback in place (S04): the waveform fills left-to-right in the ink;
/// scrubbing by dragging. No timer, no duration readout.
@MainActor
@Observable
final class VoicePlayer: NSObject, AVAudioPlayerDelegate {
    private var player: AVAudioPlayer?
    private var progressTimer: Timer?

    private(set) var isPlaying = false
    /// 0...1 — drives the waveform fill only; never rendered as a number.
    private(set) var progress: Double = 0

    func play(url: URL) {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio)
            try session.setActive(true)
            let player = try AVAudioPlayer(contentsOf: url)
            player.delegate = self
            player.play()
            self.player = player
            isPlaying = true
            progressTimer = Timer.scheduledTimer(withTimeInterval: 1.0 / 30.0, repeats: true) { [weak self] _ in
                Task { @MainActor in
                    guard let self, let player = self.player else { return }
                    self.progress = player.duration > 0 ? player.currentTime / player.duration : 0
                }
            }
        } catch {
            player = nil
        }
    }

    func pause() {
        player?.pause()
        isPlaying = false
    }

    func resume() {
        player?.play()
        isPlaying = player != nil
    }

    func scrub(to fraction: Double) {
        guard let player else { return }
        player.currentTime = max(0, min(0.999, fraction)) * player.duration
        progress = fraction
    }

    func stop() {
        player?.stop()
        player = nil
        isPlaying = false
        progress = 0
        progressTimer?.invalidate()
        progressTimer = nil
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in
            self.isPlaying = false
            self.progress = 1
            self.progressTimer?.invalidate()
            self.progressTimer = nil
        }
    }
}
