package app.readribbon.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.OutputStream
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes

// Transcripts are not optional: they are how the deaf read this app, and
// how anyone finds a note again six months later (§4.4, §11).
//
// Decision — on-device transcription at launch. The build book (§13) says
// server-side at launch and flags on-device as open question §16.5; this
// build starts on-device because it is the better privacy answer, it costs
// nothing per minute, and it works offline. If quality proves insufficient,
// a server transcriber slots in behind this same interface. Logged in
// docs/deviations.md.
//
// SFSpeechRecognizer becomes android.speech.SpeechRecognizer, built through
// createOnDeviceSpeechRecognizer so that the decision above means the same
// thing here: the audio never leaves the phone. Two things about Android's
// recognizer differ from Apple's, and the length of this file is those two
// things:
//
//   1. There is no SFSpeechURLRecognitionRequest. Android's recognizer
//      listens to a *stream*, and the only way to hand it a finished
//      recording is RecognizerIntent.EXTRA_AUDIO_SOURCE — a file
//      descriptor of raw PCM, with the channel count, encoding and sample
//      rate passed alongside it. So the note (AAC in MPEG-4, what
//      VoiceRecorder writes) is decoded to 16-bit PCM first, through
//      MediaExtractor + MediaCodec, into a scratch file in the cache that
//      is deleted the moment recognition ends. A plain file rather than a
//      pipe on purpose: a pipe would block this side whenever the
//      recognizer stopped reading, and there is nothing useful to do about
//      a half-fed recognizer. Fifteen seconds of speech is about 1.3 MB of
//      scratch.
//      EXTRA_AUDIO_SOURCE arrived in API 33, and it is the reason minSdk
//      is 33 rather than 31: below it there is no route from a recorded
//      note to a transcript at all — not a worse transcript, no
//      transcript — and §11 does not treat a transcript as optional.
//   2. It is callback-based and main-thread-bound where Swift's is an
//      async continuation, so the whole session runs on Dispatchers.Main
//      inside one suspendCancellableCoroutine — and, because nothing in
//      the contract promises a terminal callback for a session fed from a
//      file, under a ceiling that turns a silent hang into the same
//      failure the caller already renders.

/**
 * On-device transcription of a finished voice note.
 *
 * Swift's `enum Transcriber` with statics; an `object` is the Kotlin of it.
 * Both entry points take a [Context] because everything Android does here —
 * the permission read, the recognizer, the scratch directory — is asked of
 * one. The application context is used internally, so passing an Activity
 * leaks nothing.
 */
object Transcriber {

    /**
     * Swift: `requestAccessIfNeeded()`.
     *
     * Android has no speech-recognition authorization to request: reading a
     * recording is covered by RECORD_AUDIO, the permission the note could
     * not have been recorded without. That collapses Apple's three-state
     * ask to a one-line read, and it should — a second system dialog after
     * the composer's would break §6.1's ask-in-context-once. So the *ask*
     * belongs to the screen (see `VoiceRecorder.MICROPHONE_PERMISSION`) and
     * only the check lives here.
     *
     * It stays a gate rather than an assumption because the grant can be
     * taken away later, and Try again on a year-old note has to answer for
     * that case the same way iOS does: no transcript, no crash.
     */
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Transcribe a finished recording. Returns null when transcription
     * fails — the note plays fine; the transcript line reads "No
     * transcript for this one." with Try again (S04).
     *
     * [file] is the note's audio in the app's own audio directory, i.e.
     * `LocalStore.audioFile(note.audioPath)`. Swift takes the same thing as
     * a `URL`.
     */
    suspend fun transcribe(context: Context, file: File): String? {
        val app = context.applicationContext
        if (!hasAccess(app)) return null
        // Swift's `SFSpeechRecognizer()` + `recognizer.isAvailable`: no
        // recognizer, no transcript, and never a half-answer. A device can
        // still lack an on-device recogniser at any API level.
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(app)) return null

        val scratch = withContext(Dispatchers.IO) {
            runCatching { File.createTempFile("transcript", ".pcm", app.cacheDir) }.getOrNull()
        } ?: return null
        try {
            val audio = decodeToPcm(file, scratch) ?: return null
            return withTimeoutOrNull(RECOGNITION_CEILING) { recognize(app, audio) }
        } finally {
            // The note's own audio is kept forever; this copy of it is not.
            withContext(NonCancellable + Dispatchers.IO) { scratch.delete() }
        }
    }

    /** The decoded scratch audio, and what the recognizer must be told about it. */
    private class PcmAudio(
        val file: File,
        val sampleRate: Int,
        val channels: Int,
    )

    /**
     * One recognition session, start to terminal callback.
     *
     * The Swift is `recognitionTask(with:)` inside `withCheckedContinuation`
     * with a `resumed` flag guarding the double-callback; this is the same
     * shape, and the flag is kept for the same reason — a recognizer that
     * reports an error *after* a result would otherwise resume the
     * continuation twice.
     */
    private suspend fun recognize(context: Context, audio: PcmAudio): String? =
        // Every method on SpeechRecognizer must be called from the main
        // thread, and every callback arrives there.
        withContext(Dispatchers.Main.immediate) {
            val recognizer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }.getOrNull() ?: return@withContext null
            val descriptor = runCatching {
                ParcelFileDescriptor.open(audio.file, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull()
            if (descriptor == null) {
                recognizer.destroy()
                return@withContext null
            }
            try {
                suspendCancellableCoroutine<String?> { continuation ->
                    var resumed = false
                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onResults(results: Bundle?) {
                            if (resumed) return
                            resumed = true
                            // `bestTranscription.formattedString`: the
                            // recognizer's best guess, first in the list.
                            val text = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                            continuation.resume(if (text.isNullOrEmpty()) null else text)
                        }

                        override fun onError(error: Int) {
                            if (resumed) return
                            resumed = true
                            continuation.resume(null)
                        }

                        // Nothing below is wanted: partial results are off,
                        // and this is a file, not a live microphone, so
                        // there is no level to draw and no endpoint to show.
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        // `SFSpeechRecognizer()` with no locale is the
                        // user's own; this is that.
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            Locale.getDefault().toLanguageTag(),
                        )
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        // `request.shouldReportPartialResults = false`.
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        // Read the note, not the microphone. All four
                        // extras go together or none of them count.
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptor)
                        putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT,
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                            audio.sampleRate,
                        )
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, audio.channels)
                    }

                    // A throw here is a recognizer that will never call
                    // back, so it lands where a callback error lands.
                    runCatching { recognizer.startListening(intent) }.onFailure {
                        if (!resumed) {
                            resumed = true
                            continuation.resume(null)
                        }
                    }
                }
            } finally {
                // Runs on the main thread on every path, cancellation
                // included, because that is where the continuation resumes.
                // Both of these matter: an undestroyed recognizer holds a
                // binding to the recognition service, and the descriptor is
                // ours to close — the service reads its own dup of it.
                recognizer.destroy()
                runCatching { descriptor.close() }
            }
        }

    /**
     * Decode the note's AAC-in-MPEG-4 into raw 16-bit PCM at [into].
     *
     * Returns null for anything unreadable, which is the same nothing every
     * other failure returns. Nothing here is Swift's — on iOS the framework
     * opens the file itself.
     */
    private suspend fun decodeToPcm(source: File, into: File): PcmAudio? =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            var sink: OutputStream? = null
            try {
                extractor.setDataSource(source.absolutePath)
                var track = -1
                for (index in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        track = index
                        break
                    }
                }
                if (track < 0) return@withContext null
                extractor.selectTrack(track)

                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
                // What the container claims. For the AAC-LC mono 44.1 kHz
                // VoiceRecorder writes this is also what comes out of the
                // decoder, but the decoder's own answer wins below if it
                // ever disagrees.
                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                // Asked for rather than assumed: a decoder is free to hand
                // back floats, and the recognizer is being promised 16-bit.
                format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)

                val decoder = MediaCodec.createDecoderByType(mime)
                codec = decoder
                decoder.configure(format, null, null, 0)
                decoder.start()

                val stream = into.outputStream().buffered()
                sink = stream
                val info = MediaCodec.BufferInfo()
                var fedEverything = false
                var done = false
                while (!done) {
                    // A long note is a long loop; cancellation — the ceiling
                    // below, or the screen going away — has to be able to
                    // stop it.
                    currentCoroutineContext().ensureActive()
                    if (!fedEverything) fedEverything = feed(decoder, extractor)

                    val index = decoder.dequeueOutputBuffer(info, BUFFER_WAIT_US)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val decoded = decoder.outputFormat
                        sampleRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (index >= 0) {
                        drain(decoder, index, info, stream)
                        decoder.releaseOutputBuffer(index, false)
                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (ended) done = true
                    }
                }
                stream.flush()
                // A recording of pure silence decodes to nothing worth
                // sending; so does a file that was truncated on the way in.
                if (into.length() <= 0L) null else PcmAudio(into, sampleRate, channels)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            } finally {
                runCatching { sink?.close() }
                runCatching { codec?.stop() }
                codec?.release()
                extractor.release()
            }
        }

    /**
     * Hand the decoder the next packet of the recording. Returns true once
     * the end of the file has been queued and there is nothing left to feed.
     */
    private fun feed(decoder: MediaCodec, extractor: MediaExtractor): Boolean {
        val index = decoder.dequeueInputBuffer(BUFFER_WAIT_US)
        if (index < 0) return false
        val buffer = decoder.getInputBuffer(index)
        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
        if (size < 0) {
            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    /** Copy one decoded buffer of PCM out to the scratch file. */
    private fun drain(
        decoder: MediaCodec,
        index: Int,
        info: MediaCodec.BufferInfo,
        sink: OutputStream,
    ) {
        if (info.size <= 0) return
        val buffer = decoder.getOutputBuffer(index) ?: return
        val bytes = ByteArray(info.size)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.get(bytes)
        sink.write(bytes)
    }

    /** How long to wait for a buffer either way, in microseconds. */
    private const val BUFFER_WAIT_US = 10_000L

    /**
     * Android only, and not a product rule: a recognizer fed from a file
     * descriptor is not promised to call back at all — a service that
     * mis-handles the end of the stream simply goes quiet. Without a
     * ceiling a note would sit on "Transcript coming" for the rest of its
     * life, which is worse than the honest failure line, so a session that
     * has not finished by here is treated as one that failed. Well past any
     * real note: recognition of a recording runs faster than the recording
     * did.
     */
    private val RECOGNITION_CEILING = 2.minutes
}
