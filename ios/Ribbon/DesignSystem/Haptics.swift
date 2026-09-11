import CoreHaptics
import UIKit

// Haptics (build book §9.3). Core Haptics patterns, not feedback-generator
// presets — the thinking-of-you tap needs a custom envelope and the presets
// are all too eager (§12.1).
//
// The full list, and nothing else: someone arrives; a verse lifts;
// thinking-of-you sending (rising texture over the hold, completing on
// release); thinking-of-you receiving (the tap on the shoulder). Cards
// opening get nothing. No selection ticks, no success thumps, no error buzz.

@MainActor
final class Haptics {
    static let shared = Haptics()

    private var engine: CHHapticEngine?
    private var holdPlayer: CHHapticAdvancedPatternPlayer?

    private init() {
        prepare()
    }

    private func prepare() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        do {
            let engine = try CHHapticEngine()
            engine.playsHapticsOnly = true
            engine.resetHandler = { [weak self] in
                Task { @MainActor in try? await self?.engine?.start() }
            }
            try engine.start()
            self.engine = engine
        } catch {
            engine = nil
        }
    }

    private func play(_ events: [CHHapticEvent]) {
        guard let engine else { return }
        do {
            let pattern = try CHHapticPattern(events: events, parameters: [])
            let player = try engine.makePlayer(with: pattern)
            try engine.start()
            try player.start(atTime: CHHapticTimeImmediate)
        } catch {
            // A missed haptic is silence, which is always acceptable here.
        }
    }

    /// Someone arrives in the book: one soft transient, low intensity.
    func someoneArrives() {
        play([
            CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.35),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.2),
                ],
                relativeTime: 0)
        ])
    }

    /// The verse lifts on long-press — the moment it lifts, not on
    /// touch-down.
    func verseLifts() {
        play([
            CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.45),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.25),
                ],
                relativeTime: 0)
        ])
    }

    /// Thinking of you, sending: a rising texture over the ~700 ms hold.
    func beginThinkingOfYouHold() {
        guard let engine else { return }
        let continuous = CHHapticEvent(
            eventType: .hapticContinuous,
            parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.18),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.1),
            ],
            relativeTime: 0,
            duration: 0.7)
        do {
            let pattern = try CHHapticPattern(events: [continuous], parameters: [])
            let player = try engine.makeAdvancedPlayer(with: pattern)
            try engine.start()
            try player.start(atTime: CHHapticTimeImmediate)
            holdPlayer = player
        } catch {
            holdPlayer = nil
        }
    }

    /// The hold completed: a single sharp-soft transient on release.
    func completeThinkingOfYouHold() {
        try? holdPlayer?.stop(atTime: CHHapticTimeImmediate)
        holdPlayer = nil
        play([
            CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.8),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.45),
                ],
                relativeTime: 0)
        ])
    }

    func cancelThinkingOfYouHold() {
        try? holdPlayer?.stop(atTime: CHHapticTimeImmediate)
        holdPlayer = nil
    }

    /// Thinking of you, receiving: one transient with a short decay — the
    /// tap on the shoulder. The single fast thing in the product.
    func tapOnTheShoulder() {
        play([
            CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.9),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.5),
                ],
                relativeTime: 0),
            CHHapticEvent(
                eventType: .hapticContinuous,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 0.2),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.1),
                ],
                relativeTime: 0.02,
                duration: 0.09),
        ])
    }

    static func light() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}
