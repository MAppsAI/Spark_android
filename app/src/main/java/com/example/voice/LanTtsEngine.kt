package com.example.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Streams TTS audio from a Spark TTS server (sherpa-onnx Kokoro) running on
 * a desktop on the LAN. Audio starts playing while later sentences are still
 * being synthesized server-side (chunked WAV over HTTP).
 */
class LanTtsEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile private var serverUrl: String = ""
    @Volatile private var healthy: Boolean = false
    @Volatile private var sampleRate: Int = 24000
    @Volatile var numSpeakers: Int = 54
        private set
    @Volatile var sid: Int = 0

    private val stopFlag = AtomicBoolean(false)
    @Volatile private var playing = false

    fun setServer(url: String) {
        serverUrl = url.trim().removeSuffix("/")
        healthy = false
    }

    fun getServer(): String = serverUrl

    /** Blocking health probe — call on IO dispatcher. */
    fun probe(): Boolean {
        if (serverUrl.isBlank()) return false
        return try {
            val req = Request.Builder().url("$serverUrl/health").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { healthy = false; return false }
                val body = resp.body?.string() ?: return false
                healthy = Regex("\"ready\"\\s*:\\s*true").find(body) != null
                val m = Regex("\"sample_rate\"\\s*:\\s*(\\d+)").find(body)
                m?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { sampleRate = it }
                val sp = Regex("\"speakers\"\\s*:\\s*(\\d+)").find(body)
                sp?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { numSpeakers = it }
                healthy
            }
        } catch (e: Throwable) {
            healthy = false
            false
        }
    }

    val isHealthy: Boolean get() = healthy && serverUrl.isNotBlank()

    /**
     * Opens a streaming session: one AudioTrack for the whole utterance, with
     * a prefetching fetcher thread so sentence N+1 synthesizes server-side
     * while sentence N plays. enqueue() sentences as they complete from the
     * LLM token stream, then finish() and join() on the caller thread.
     */
    fun openSession(speed: Double = 1.0): LanTtsSession = LanTtsSession(speed)

    inner class LanTtsSession(private val speed: Double) {
        // LinkedBlockingQueue forbids null — use sentinel objects (Any queue
        // avoids Kotlin's ByteArray identity-copy problem on array queues).
        private val sentenceQ = java.util.concurrent.LinkedBlockingQueue<Any>()
        private val pcmQ = java.util.concurrent.LinkedBlockingQueue<Any>()
        private val sessionStop = AtomicBoolean(false)
        @Volatile private var failed = false
        @Volatile private var aborted = false
        val isAborted: Boolean get() = aborted
        @Volatile private var anyAudio = false
        @Volatile private var track: AudioTrack? = null
        @Volatile private var fetchThread: Thread? = null

        init {
            if (!isHealthy) failed = true
        }

        fun enqueue(sentence: String) {
            if (!sessionStop.get()) sentenceQ.put(sentence)
        }

        fun finish() {
            sentenceQ.put(QUEUE_END) // end marker for fetcher
        }

        fun abort() {
            aborted = true
            sessionStop.set(true)
            sentenceQ.clear()
            sentenceQ.put(QUEUE_END)
            pcmQ.put(QUEUE_END)
            try { track?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop() } } catch (_: Throwable) {}
        }

        /** Blocking play until all queued sentences have played. Call on IO thread. */
        fun join(): Boolean {
            if (failed) return false
            fetchThread = Thread { fetchLoop() }.also {
                it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> failed = true }
                it.isDaemon = true
                it.start()
            }

            val sr = sampleRate.takeIf { it > 0 } ?: 24000
            val at = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Throwable) {
                failed = true
                return false
            }
            track = at
            playing = true
            try {
                at.play()
            } catch (_: Throwable) {
                failed = true
                try { at.release() } catch (_: Throwable) {}
                playing = false
                return false
            }
            val buf = ByteArray(4096)
            try {
                while (!sessionStop.get()) {
                    val item = pcmQ.take()
                    if (item === QUEUE_END) break
                    val pcm = item as? ByteArray ?: continue
                    var off = 0
                    while (off < pcm.size && !sessionStop.get()) {
                        val n = minOf(buf.size, pcm.size - off)
                        at.write(pcm, off, n, AudioTrack.WRITE_BLOCKING)
                        off += n
                    }
                    anyAudio = true
                }
                // let the hardware drain queued samples before tearing down
                if (!sessionStop.get()) {
                    try { at.stop() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
            } finally {
                try { if (at.playState == AudioTrack.PLAYSTATE_PLAYING) at.stop() } catch (_: Throwable) {}
                try { at.release() } catch (_: Throwable) {}
                if (track === at) track = null
                playing = false
            }
            fetchThread?.join(2000)
            return !failed && !sessionStop.get() && anyAudio
        }

        /** Fetch sentences, strip RIFF header, hand PCM to the player queue. */
        private fun fetchLoop() {
            try {
                while (true) {
                    val item = sentenceQ.take()
                    if (item === QUEUE_END) break
                    if (sessionStop.get()) break
                    val sentence = item as? String ?: continue
                    try {
                        val url = "$serverUrl/tts?text=${URLEncoder.encode(sentence, "UTF-8")}&sid=$sid&speed=$speed"
                        val req = Request.Builder().url(url).build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) { failed = true; return }
                            val stream = resp.body?.byteStream() ?: return
                            // Stream-forward PCM as the server produces it
                            // (server flushes per chunk); first 44 bytes are the
                            // RIFF header which we drop.
                            val chunk = ByteArray(16384)
                            var headerLeft = 44
                            var first = true
                            while (!sessionStop.get()) {
                                val n = stream.read(chunk)
                                if (n == -1) break
                                var off = 0
                                var len = n
                                if (headerLeft > 0) {
                                    val skip = minOf(headerLeft, len)
                                    headerLeft -= skip
                                    off += skip
                                    len -= skip
                                }
                                if (len > 0) {
                                    pcmQ.put(chunk.copyOfRange(off, off + len))
                                    if (first) first = false
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        failed = true
                        break
                    }
                }
            } finally {
                pcmQ.put(QUEUE_END) // end marker for player — never null
            }
        }
    }

    /**
     * Blocking streamed speak: writes PCM16 as it arrives from the server into
     * a streaming AudioTrack so playback starts within ~1s. Returns false on
     * network/parse failure (caller should fall back to another engine).
     */
    fun speakBlocking(text: String): Boolean {
        if (!isHealthy) return false
        stopFlag.set(false)
        return try {
            val url = "$serverUrl/tts?text=${URLEncoder.encode(text, "UTF-8")}&sid=0&speed=1.0"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val stream = resp.body?.byteStream() ?: return false
                stream.use { playPcmStream(it) }
                !stopFlag.get()
            }
        } catch (e: Throwable) {
            false
        } finally {
            playing = false
        }
    }

    private fun playPcmStream(input: InputStream) {
        // Skip the 44-byte RIFF header (server always emits PCM16 mono at sampleRate).
        val header = ByteArray(44)
        var read = 0
        while (read < 44) {
            val n = input.read(header, read, 44 - read)
            if (n == -1) return
            read += n
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrackRef = track
        playing = true
        track.play()
        val buf = ByteArray(4096)
        try {
            while (!stopFlag.get()) {
                val n = input.read(buf)
                if (n == -1) break
                if (n > 1) track.write(buf, 0, n - (n % 2), AudioTrack.WRITE_BLOCKING)
            }
        } catch (_: Throwable) {
        } finally {
            try { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() } catch (_: Throwable) {}
            try { track.release() } catch (_: Throwable) {}
            if (audioTrackRef === track) audioTrackRef = null
        }
    }

    @Volatile private var audioTrackRef: AudioTrack? = null

    fun stop() {
        stopFlag.set(true)
        try {
            audioTrackRef?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
            }
        } catch (_: Throwable) {}
    }

    val isPlaying: Boolean get() = playing

    companion object {
        val IoDispatcher = Dispatchers.IO
        private val QUEUE_END = Any() // sentinel: queues reject null
    }
}
