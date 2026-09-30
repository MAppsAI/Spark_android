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
                healthy = body.contains("\"ready\":true")
                val m = Regex("\"sample_rate\":(\\d+)").find(body)
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
