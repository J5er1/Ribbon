import Foundation
import Speech

// Transcripts are not optional: they are how the deaf read this app, and
// how anyone finds a note again six months later (§4.4, §11).
//
// Decision — on-device transcription at launch. The build book (§13) says
// server-side at launch and flags on-device as open question §16.5; this
// build starts on-device because it is the better privacy answer, it costs
// nothing per minute, and it works offline. If quality proves insufficient,
// a server transcriber slots in behind this same interface. Logged in
// docs/deviations.md.

enum Transcriber {
    /// Refused once, in Settings: no transcript can ever be written until
    /// that changes, so the note says so instead of offering a retry that
    /// can't succeed (S25).
    static var isRefused: Bool {
        switch SFSpeechRecognizer.authorizationStatus() {
        case .denied, .restricted: return true
        default: return false
        }
    }

    static func requestAccessIfNeeded() async -> Bool {
        switch SFSpeechRecognizer.authorizationStatus() {
        case .authorized:
            return true
        case .notDetermined:
            return await withCheckedContinuation { continuation in
                SFSpeechRecognizer.requestAuthorization { status in
                    continuation.resume(returning: status == .authorized)
                }
            }
        default:
            return false
        }
    }

    /// Transcribe a finished recording. Returns nil when transcription
    /// fails — the note plays fine; the transcript line reads "No
    /// transcript for this one." with Try again (S04).
    static func transcribe(url: URL) async -> String? {
        guard await requestAccessIfNeeded() else { return nil }
        guard let recognizer = SFSpeechRecognizer(), recognizer.isAvailable else { return nil }

        let request = SFSpeechURLRecognitionRequest(url: url)
        request.shouldReportPartialResults = false
        if recognizer.supportsOnDeviceRecognition {
            request.requiresOnDeviceRecognition = true
        }

        return await withCheckedContinuation { continuation in
            var resumed = false
            recognizer.recognitionTask(with: request) { result, error in
                guard !resumed else { return }
                if let result, result.isFinal {
                    resumed = true
                    let text = result.bestTranscription.formattedString
                    continuation.resume(returning: text.isEmpty ? nil : text)
                } else if error != nil {
                    resumed = true
                    continuation.resume(returning: nil)
                }
            }
        }
    }
}
