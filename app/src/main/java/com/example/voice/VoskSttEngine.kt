package com.example.voice

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Real offline STT via Vosk. The downloaded zip must be extracted so that
 * [modelDir] contains mfcc.conf/circular-am.yaml etc. at its root
 * (Vosk small-en-us model has the usual layout).
 */
class VoskSttEngine {

    @Volatile private var model: Model? = null
    @Volatile private var loadedDir: String? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    val isReady: Boolean get() = model != null

    @Synchronized
    fun load(modelDir: File): String {
        val dir = modelDir.canonicalPath
        if (model != null && loadedDir == dir) return ""
        stop()
        if (!modelDir.isDirectory) return "Vosk model directory missing"
        // sanity: vosk models ship am/final.mdl or circular-am.yaml at root
        val looksLikeModel = modelDir.listFiles()?.any {
            it.name.endsWith(".mdl") || it.name.endsWith(".yaml") || it.name == "am" || it.name == "conf"
        } == true
        if (!looksLikeModel) return "Not a Vosk model directory"
        return try {
            model = Model(dir)
            loadedDir = dir
            ""
        } catch (e: Throwable) {
            try { model?.close() } catch (_: Throwable) {}
            model = null
            "Vosk load failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Starts listening. Calls [onPartial] with live text and [onFinal] once with
     * the final transcript ("" if nothing recognized). Must be called from a thread
     * with a Looper for callbacks? Vosk posts callbacks on the thread that created
     * SpeechService — caller should invoke on the main thread.
     */
    @Synchronized
    fun startListening(
        sampleRate: Float = 16000f,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        val m = model ?: return false
        stop()
        return try {
            val rec = Recognizer(m, sampleRate)
            recognizer = rec
            val service = SpeechService(rec, sampleRate)
            speechService = service
            val started = service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.takeIf { it.isNotBlank() }?.let(onPartial)
                }
                override fun onResult(hypothesis: String?) { /* superseded by final */ }
                override fun onFinalResult(hypothesis: String?) {
                    onFinal(hypothesis ?: "")
                }
                override fun onError(exception: Exception?) {
                    onError(exception?.message ?: "Vosk error")
                }
                override fun onTimeout() {
                    onFinal("")
                }
            })
            started
        } catch (e: Throwable) {
            onError(e.message ?: "Vosk start failed")
            stop()
            false
        }
    }

    fun stopListening() {
        try { speechService?.stop() } catch (_: Throwable) {}
    }

    fun cancel() {
        try { speechService?.cancel() } catch (_: Throwable) {}
    }

    @Synchronized
    fun stop() {
        try { speechService?.shutdown() } catch (_: Throwable) {}
        speechService = null
        try { recognizer?.close() } catch (_: Throwable) {}
        recognizer = null
    }

    @Synchronized
    fun release() {
        stop()
        try { model?.close() } catch (_: Throwable) {}
        model = null
        loadedDir = null
    }
}
