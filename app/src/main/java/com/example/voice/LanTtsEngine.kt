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
        private val sentenceQ = java.util.concurrent.LinkedBlockingQueue<String?>()
        private val pcmQ = java.util.concurrent.LinkedBlockingQueue<ByteArray?>()
        private val sessionStop = AtomicBoolean(false)
        @Volatile private var failed = false
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
            sentenceQ.put(null) // end marker for fetcher
        }

        fun abort() {
            sessionStop.set(true)
            sentenceQ.clear()
            sentenceQ.put(null)
            pcmQ.put(null)
            try { track?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop() } } catch (_: Throwable) {}
        }

        /** Blocking play until all queued sentences have played. Call on IO thread. */
        fun join(): Boolean {
            if (failed) return false
            fetchThread = Thread { fetchLoop() }.also { it.isDaemon = true; it.start() }

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
            at.play()
            val buf = ByteArray(4096)
            try {
                while (!sessionStop.get()) {
                    val pcm = pcmQ.take() ?: break
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
                    val sentence = sentenceQ.take() ?: break
                    if (sessionStop.get()) break
                    try {
                        val url = "$serverUrl/tts?text=${URLEncoder.encode(sentence, "UTF-8")}&sid=0&speed=$speed"
                        val req = Request.Builder().url(url).build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) { failed = true; return }
                            val stream = resp.body?.byteStream() ?: return
                            val all = java.io.ByteArrayOutputStream(1 shl 16)
                            val chunk = ByteArray(16384)
                            var n: Int
                            while (stream.read(chunk).also { n = it } != -1) all.write(chunk, 0, n)
                            val bytes = all.toByteArray()
                            val pcm = if (bytes.size > 44 &&
                                String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF"
                            ) bytes.copyOfRange(44, bytes.size) else bytes
                            if (pcm.isNotEmpty()) pcmQ.put(pcm)
                        }
                    } catch (_: Throwable) {
                        failed = true
                        break
                    }
                }
            } finally {
                pcmQ.put(null) // end marker for player
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
    }
}
