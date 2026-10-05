package com.example.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File

/**
 * Real on-device neural TTS powered by sherpa-onnx (Kokoro models).
 *
 * Expected layout under [modelDir] (contents of kokoro-int8-multi-lang-v1_1):
 *   model.onnx (or model.int8.onnx), voices.bin, tokens.txt,
 *   espeak-ng-data/ (with phontab), optional date/number .fst rules.
 */
class KokoroTtsEngine {

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var loadedDir: String? = null
    private var audioTrack: AudioTrack? = null
    private val speakerId: Int = 0

    val isReady: Boolean get() = tts != null

    /** Loads the Kokoro bundle. Returns "" on success, an error string otherwise. */
    @Synchronized
    fun load(modelDir: File): String {
        val dir = try { modelDir.canonicalPath } catch (e: Exception) { modelDir.absolutePath }
        if (tts != null && loadedDir == dir) return ""
        release()
        if (!modelDir.isDirectory) return "Model directory missing"

        val onnx = listOf("model.onnx", "model.int8.onnx")
            .map { File(modelDir, it) }
            .firstOrNull { it.exists() && it.length() > 1024 }
            ?: return "model.onnx missing"
        val voices = File(modelDir, "voices.bin")
        if (!voices.exists() || voices.length() == 0L) return "voices.bin missing"
        val tokens = File(modelDir, "tokens.txt")
        if (!tokens.exists()) return "tokens.txt missing"

        val espeakDir = modelDir.walkTopDown()
            .firstOrNull { it.isDirectory && it.name == "espeak-ng-data" && File(it, "phontab").exists() }
            ?: return "espeak-ng-data missing"

        val ruleFstsCsv = listOf("date-zh.fst", "number-zh.fst", "phone-zh.fst")
            .map { File(modelDir, it) }
            .filter { it.exists() }
            .joinToString(",") { it.absolutePath }
            .ifBlank { "" }

        return try {
            val kokoroCfg = OfflineTtsKokoroModelConfig(
                model = onnx.absolutePath,
                voices = voices.absolutePath,
                tokens = tokens.absolutePath,
                dataDir = espeakDir.absolutePath,
                lexicon = "",
                lang = "en-us",
                dictDir = "",
                lengthScale = 1.0f
            )
            val modelConfig = OfflineTtsModelConfig().apply {
                kokoro = kokoroCfg
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                debug = false
                provider = "cpu"
            }
            val config = OfflineTtsConfig().apply {
                model = modelConfig
                this.ruleFsts = ruleFstsCsv
                maxNumSentences = 1
                silenceScale = 0.2f
            }
            val candidate = OfflineTts(null, config)
            // Warm-up sanity check (VoxSherpa pattern): synth once, verify samples.
            val test = candidate.generate("...", speakerId, 1.0f)
            if (test == null || test.samples == null || test.samples.isEmpty()) {
                try { candidate.release() } catch (_: Throwable) {}
                "Kokoro warm-up produced no audio"
            } else {
                tts = candidate
                loadedDir = dir
                ""
            }
        } catch (e: Throwable) {
            try { tts?.release() } catch (_: Throwable) {}
            tts = null
            loadedDir = null
            "Load failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Synthesize and play [text]; blocking — run on a background thread. */
    fun speakBlocking(text: String): Boolean {
        val engine = tts ?: return false
        return try {
            val audio: GeneratedAudio = engine.generate(text, speakerId, 1.0f)
            val samples = audio.samples
            if (samples.isEmpty()) false
            else {
                playPcm(audio.sampleRate, samples)
                true
            }
        } catch (e: Throwable) {
            false
        }
    }

    private fun playPcm(sampleRate: Int, samples: FloatArray) {
        stopPlayback()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(sampleRate / 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()
        var offset = 0
        val chunk = 4096
        while (offset < samples.size) {
            val n = minOf(chunk, samples.size - offset)
            track.write(samples, offset, n, AudioTrack.WRITE_BLOCKING)
            offset += n
        }
        try { track.stop() } catch (_: Throwable) {}
    }

    fun stopPlayback() {
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        } catch (_: Throwable) {}
        audioTrack = null
    }

    @Synchronized
    fun release() {
        stopPlayback()
        try { tts?.release() } catch (_: Throwable) {}
        tts = null
        loadedDir = null
    }
}
