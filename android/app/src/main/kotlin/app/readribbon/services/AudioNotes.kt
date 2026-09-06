package app.readribbon.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Voice notes (§4.4, S05). Speak is press-and-hold; the waveform draws live
// in your ink; release keeps it, drag away discards. A recording under ~1 s
// is discarded silently as a mis-touch. A recording interrupted by a call
// is kept and offered, not thrown away.
//
// AVAudioRecorder becomes MediaRecorder rather than AudioRecord. AudioRecord
// hands back raw PCM, which would mean encoding the note ourselves with
// MediaCodec to land the same AAC-in-MPEG-4 file the iOS build writes — and
// the file format is not a free choice, because a note recorded on one
// platform is played on the other through the same storage bucket.
// MediaRecorder writes exactly that file and still reports live amplitude
// through getMaxAmplitude(), which is the one thing AudioRecord was going to
// be needed for. AVAudioPlayer becomes MediaPlayer, which has the same
// prepare/play/seek shape and, unlike ExoPlayer, needs no player thread of
// its own for a fifteen-second note.
//
// Both classes are main-thread objects, standing in for the Swift's
// @MainActor: every callback below arrives on the main looper and every
// piece of published state is Compose snapshot state, which is what
// @Observable maps to.

/**
 * A kept recording: the file on disk and the waveform to draw over it.
 *
 * Swift returns this as a `(url:waveform:)` tuple; a named pair is the
 * Kotlin of it. Note what is *not* here — no duration. §4.4 draws the peaks
 * and nothing else, because a duration is a count, and a count makes people
 * self-conscious about how long they talked.
 */
data class VoiceTake(
    val file: File,
    /** Normalized 0...1 peaks, [VoiceRecorder.WAVEFORM_PEAKS] of them. */
    val waveform: List<Float>,
)

class VoiceRecorder(context: Context) {

    private val context: Context = context.applicationContext

    private var recorder: MediaRecorder? = null
    private var meterJob: Job? = null

    /**
     * Live, normalized peaks while recording — the waveform in your ink.
     * Ring-buffered for display; the full take accumulates separately so
     * a long note's stored waveform covers the whole recording.
     */
    var livePeaks: List<Float> by mutableStateOf(emptyList<Float>())
        private set

    private val allPeaks = mutableListOf<Float>()

    var isRecording by mutableStateOf(false)
        private set

    /**
     * Set when a system interruption (a call) ended the recording early:
     * what was captured is kept and offered.
     */
    var interrupted by mutableStateOf(false)
        private set

    private var file: File? = null
    private var startedAt: Instant? = null

    /** Android: MediaRecorder.stop() is not idempotent — see [stopRecorder]. */
    private var recorderStopped = false

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val audio: AudioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    /** Whether the microphone has ever been asked for — see [microphoneUndecided]. */
    private val permissionMemory =
        context.applicationContext.getSharedPreferences(PERMISSION_FILE, Context.MODE_PRIVATE)

    val hasMicrophoneAccess: Boolean
        get() = context.checkSelfPermission(MICROPHONE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Android has no `.undetermined` to read back: a permission that was
     * never asked for and one that was refused for good both read as simply
     * denied, and `shouldShowRequestPermissionRationale` cannot separate
     * them either. So the first ask is remembered here. Getting this right
     * is what makes S25 work — refused once, the speak control routes to
     * Settings and never asks again.
     */
    val microphoneUndecided: Boolean
        get() = !hasMicrophoneAccess && !permissionMemory.getBoolean(ASKED_KEY, false)

    /**
     * Ask in context, once (§6.1). If refused, the speak control routes to
     * Settings and never asks again (S25).
     *
     * The ask itself is the screen's: on Android a permission dialog can
     * only be raised from an Activity, so the composer launches
     * [MICROPHONE_PERMISSION] through `rememberLauncherForActivityResult`
     * and calls this from the result callback. Everything the screen needs
     * to decide *whether* to ask is above.
     */
    fun noteAccessAsked() {
        permissionMemory.edit().putBoolean(ASKED_KEY, true).apply()
    }

    /**
     * Begin recording into [file], which comes from
     * `LocalStore.audioFile("<uuid>.m4a")` — the app's own audio directory,
     * so a note that is never kept never leaves a trace anywhere else.
     */
    fun begin(file: File) {
        if (isRecording) return
        interrupted = false
        livePeaks = emptyList()
        allPeaks.clear()
        this.file = file
        val created = MediaRecorder(context)
        try {
            requestFocus()
            // The same settings the iOS build records with: AAC in an MPEG-4
            // container (an .m4a), 44.1 kHz, mono. The bit rate stands in
            // for AVAudioQuality.medium, which has no numeric equivalent
            // here; 64 kbps mono is speech-transparent and keeps a long note
            // small enough to upload on a phone connection.
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioSamplingRate(SAMPLE_RATE)
            created.setAudioChannels(1)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.setOutputFile(file.absolutePath)
            created.setOnErrorListener { _, _, _ -> endedUnderneathUs() }
            created.prepare()
            created.start()
        } catch (error: Exception) {
            created.release()
            abandonFocus()
            recorder = null
            return
        }
        recorder = created
        recorderStopped = false
        startedAt = Clock.System.now()
        isRecording = true
        meterJob = scope.launch {
            while (isActive) {
                delay(METER_INTERVAL)
                sampleMeter()
            }
        }
    }

    private fun sampleMeter() {
        val active = recorder ?: return
        if (!isRecording) return
        // AVAudioRecorder reports average power in dB straight out.
        // MediaRecorder reports the loudest raw sample since the last call,
        // 0…32767, so it is converted to dBFS here — that conversion is the
        // whole of the difference, and the mapping below is the iOS one
        // unchanged, so the two platforms draw the same waveform.
        val amplitude = runCatching { active.maxAmplitude }.getOrDefault(0)
        val db =
            if (amplitude <= 0) SILENCE_DB
            else (20.0 * log10(amplitude.toDouble() / MAX_AMPLITUDE)).toFloat()
        // Map -50…0 dB to 0…1 with a gentle floor so silence still draws a
        // thread.
        val level = ((db + 50f) / 50f).coerceIn(0f, 1f)
        val peak = maxOf(0.06f, level.pow(1.6f))
        allPeaks.add(peak)
        val live = livePeaks + peak
        livePeaks = if (live.size > LIVE_PEAKS) live.takeLast(LIVE_PEAKS) else live
    }

    /**
     * Release to keep. Returns the file and a downsampled waveform, or
     * null for a mis-touch (< 1 s).
     */
    fun finish(): VoiceTake? {
        val active = recorder ?: return null
        val file = this.file ?: return null
        stopMetering()
        stopRecorder()
        active.release()
        recorder = null
        abandonFocus()
        isRecording = false
        val duration = startedAt?.let { Clock.System.now() - it } ?: Duration.ZERO
        if (duration < 1.seconds) {
            file.delete()
            return null
        }
        return VoiceTake(file, downsample(allPeaks.toList(), WAVEFORM_PEAKS))
    }

    /**
     * Drag away to discard — the waveform recedes rather than a dialog
     * appearing.
     */
    fun discard() {
        stopMetering()
        stopRecorder()
        recorder?.release()
        file?.delete()
        recorder = null
        abandonFocus()
        isRecording = false
        livePeaks = emptyList()
        allPeaks.clear()
    }

    /**
     * Android only: whoever owns a recorder hands it back — the composer
     * does this in a `DisposableEffect`, where iOS does it in `onDisappear`.
     * Anything in flight is discarded, because the mic is never left hot,
     * and then the metering scope is stopped for good.
     */
    fun dispose() {
        if (isRecording) discard()
        stopMetering()
        abandonFocus()
        scope.cancel()
    }

    private fun stopMetering() {
        meterJob?.cancel()
        meterJob = null
    }

    /**
     * Android: MediaRecorder.stop() throws when it has already stopped and
     * when nothing was captured — which is exactly the mis-touch case, the
     * commonest path through this file. Both throws are expected rather than
     * errors, and AVAudioRecorder simply tolerates them, so every stop goes
     * through here.
     */
    private fun stopRecorder() {
        val active = recorder ?: return
        if (recorderStopped) return
        recorderStopped = true
        runCatching { active.stop() }
    }

    /**
     * Ended underneath us — an interruption. Keep what landed.
     *
     * On iOS this arrives as AVAudioRecorderDelegate's
     * `audioRecorderDidFinishRecording`, by which point the system has
     * already closed the file. Android has no one callback for it, so two
     * signals feed this: losing audio focus, which is what an incoming call
     * takes, and MediaRecorder's own error listener. The stop is what closes
     * the MPEG-4 container — without it the file has no index and the take
     * we promised to keep would not play. The recorder is deliberately not
     * cleared, so a later [finish] still hands the take over, exactly as the
     * iOS one does.
     */
    private fun endedUnderneathUs() {
        if (!isRecording) return
        stopMetering()
        stopRecorder()
        isRecording = false
        interrupted = true
    }

    /**
     * Setting an AVAudioSession `.playAndRecord` category active is what
     * makes an iOS recording interruptible in the first place; audio focus
     * is the same contract here, and losing it is how a call announces
     * itself. Whether the request is granted is not gated on — the system,
     * not this class, decides whether a mic is available, and a refusal
     * arrives as the error listener firing.
     */
    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                val lost = change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                if (lost) endedUnderneathUs()
            }
            .build()
        focusRequest = request
        audio.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    companion object {
        /**
         * What the screen asks for, and what it hands
         * `rememberLauncherForActivityResult(RequestPermission())`. Already
         * declared in the manifest; asking is the screen's moment to choose.
         */
        val MICROPHONE_PERMISSION: String = Manifest.permission.RECORD_AUDIO

        /**
         * Peaks in a stored waveform. The same 96 as iOS, so a note recorded
         * there and one recorded here draw the same shape in the same
         * column.
         */
        const val WAVEFORM_PEAKS = 96

        fun downsample(peaks: List<Float>, count: Int): List<Float> {
            if (peaks.size <= count) return peaks
            val stride = peaks.size.toDouble() / count.toDouble()
            return (0 until count).map { i ->
                val start = (i * stride).toInt()
                val end = minOf(peaks.size, ((i + 1) * stride).toInt() + 1)
                peaks.subList(start, end).maxOrNull() ?: 0f
            }
        }

        /** How many live peaks the ring buffer keeps for display. */
        private const val LIVE_PEAKS = 600
        private val METER_INTERVAL = 50.milliseconds
        private const val SAMPLE_RATE = 44_100
        private const val BIT_RATE = 64_000
        /** Full scale for a 16-bit sample, what getMaxAmplitude() counts in. */
        private const val MAX_AMPLITUDE = 32_767.0
        private const val SILENCE_DB = -160f
        private const val PERMISSION_FILE = "ribbon.microphone"
        private const val ASKED_KEY = "asked"
    }
}

/**
 * Playback in place (S04): the waveform fills left-to-right in the ink;
 * scrubbing by dragging. No timer, no duration readout.
 */
class VoicePlayer(context: Context) {

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    var isPlaying by mutableStateOf(false)
        private set

    /** 0...1 — drives the waveform fill only; never rendered as a number. */
    var progress: Double by mutableStateOf(0.0)
        private set

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val audio: AudioManager =
        context.applicationContext.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    fun play(file: File) {
        // ARC does this on iOS without being asked: assigning a new
        // AVAudioPlayer over the old one releases and stops it. Android has
        // to be told. (The Swift leaves its old progress timer running here;
        // the effect is the same either way, because a timer only ever reads
        // whichever player is current.)
        releasePlayer()
        val created = MediaPlayer()
        try {
            requestFocus()
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            created.setDataSource(file.absolutePath)
            created.setOnCompletionListener { finished() }
            created.prepare()
            created.start()
        } catch (error: Exception) {
            created.release()
            abandonFocus()
            player = null
            return
        }
        player = created
        isPlaying = true
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL)
                val active = player ?: continue
                progress = runCatching {
                    val duration = active.duration
                    if (duration > 0) {
                        active.currentPosition.toDouble() / duration.toDouble()
                    } else {
                        0.0
                    }
                }.getOrDefault(progress)
            }
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        isPlaying = false
    }

    fun resume() {
        runCatching { player?.start() }
        isPlaying = player != null
    }

    fun scrub(fraction: Double) {
        val active = player ?: return
        runCatching {
            active.seekTo((fraction.coerceIn(0.0, 0.999) * active.duration).toInt())
        }
        progress = fraction
    }

    fun stop() {
        releasePlayer()
        isPlaying = false
        progress = 0.0
        abandonFocus()
    }

    /**
     * Android only: the note card stops the player when it leaves the
     * screen, the same as iOS; this additionally ends the progress scope,
     * for a card that is going away for good.
     */
    fun dispose() {
        stop()
        scope.cancel()
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        progressJob = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    private fun finished() {
        isPlaying = false
        progress = 1.0
        progressJob?.cancel()
        progressJob = null
        abandonFocus()
    }

    /**
     * iOS gets this from the session category: `.playback` made active
     * silences whatever else was playing, and the system pauses the player
     * when a call arrives. Audio focus is both of those on Android — except
     * that nothing pauses us automatically, so the loss is handled here to
     * land in the same place. Focus is handed back the moment the note ends,
     * because holding it would keep the music we interrupted from resuming.
     */
    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                val lost = change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                if (lost) pause()
            }
            .build()
        focusRequest = request
        audio.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        /** 1/30 s — the fill moves, it does not tick. */
        val PROGRESS_INTERVAL = 33.milliseconds
    }
}
