package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

enum class VoiceModelType { STT, TTS }

const val DEFAULT_VOICE_SYSTEM_PROMPT =
    "You are speaking out loud, not writing. Answer in one or two short, " +
    "conversational sentences. No lists, no markdown, no code, no emoji. " +
    "Get to the point immediately."

data class VoiceModelInfo(
    val id: String,
    val name: String,
    val type: VoiceModelType,
    val tier: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val description: String,
    val downloadUrl: String,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isSelected: Boolean = false,
    val loadError: String = ""
)

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class VoiceEngineManager(
    private val context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // model downloads: no read timeout
        .followRedirects(true)
        .build()

    private val modelsDir = File(context.filesDir, "voice_models").apply {
        if (!exists()) mkdirs()
    }
    private val prefs = context.getSharedPreferences("voice_engine", Context.MODE_PRIVATE)

    // Model catalog — real, verified URLs (sherpa-onnx tts-models release + Vosk).
    private val initialModels = listOf(
        VoiceModelInfo(
            id = "stt_system",
            name = "Android On-Device Recognizer",
            type = VoiceModelType.STT,
            tier = "Built-in (0MB)",
            sizeBytes = 0L,
            sizeFormatted = "0 MB",
            description = "Native Android offline speech recognizer. Zero download required.",
            downloadUrl = "",
            isDownloaded = true
        ),
        VoiceModelInfo(
            id = "stt_vosk_tiny",
            name = "Vosk Small EN",
            type = VoiceModelType.STT,
            tier = "Tiny (~41MB)",
            sizeBytes = 41_205_931L,
            sizeFormatted = "41 MB",
            description = "Real offline Vosk acoustic model. Near real-time dictation, no network needed.",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        ),
        VoiceModelInfo(
            id = "stt_vosk_medium",
            name = "Vosk Large EN (LGraph)",
            type = VoiceModelType.STT,
            tier = "Medium (~130MB)",
            sizeBytes = 130_557_655L,
            sizeFormatted = "130 MB",
            description = "High-accuracy on-device Vosk model. Better grammar and vocabulary.",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip"
        ),
        VoiceModelInfo(
            id = "tts_system",
            name = "System Neural TTS Voice",
            type = VoiceModelType.TTS,
            tier = "Built-in (0MB)",
            sizeBytes = 0L,
            sizeFormatted = "0 MB",
            description = "Native on-device text-to-speech provided by Android.",
            downloadUrl = "",
            isDownloaded = true
        ),
        VoiceModelInfo(
            id = "tts_lan",
            name = "LAN Voice Server (fastest)",
            type = VoiceModelType.TTS,
            tier = "Streaming (0MB)",
            sizeBytes = 0L,
            sizeFormatted = "0 MB",
            description = "Streams Kokoro audio from spark_tts_server.py on your PC — playback starts in ~1s. Set the address in Voice settings.",
            downloadUrl = "",
            isDownloaded = true
        ),
        VoiceModelInfo(
            id = "tts_kokoro",
            name = "Kokoro Neural Voice",
            type = VoiceModelType.TTS,
            tier = "HD (~126MB)",
            sizeBytes = 132_303_094L,
            sizeFormatted = "126 MB",
            description = "Real Kokoro-82M neural voice via sherpa-onnx. Natural prosody, fully offline.",
            downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
        )
    )

    private val _models = MutableStateFlow<List<VoiceModelInfo>>(initialModels)
    val models: StateFlow<List<VoiceModelInfo>> = _models.asStateFlow()

    val selectedSttModel: VoiceModelInfo?
        get() = _models.value.find { it.type == VoiceModelType.STT && it.isSelected }

    val selectedTtsModel: VoiceModelInfo?
        get() = _models.value.find { it.type == VoiceModelType.TTS && it.isSelected }

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsDecibels = MutableStateFlow(0f)
    val rmsDecibels: StateFlow<Float> = _rmsDecibels.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _liveAiSpeechText = MutableStateFlow("")
    val liveAiSpeechText: StateFlow<String> = _liveAiSpeechText.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var activeDownloadJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Real engines
    private val kokoroEngine = KokoroTtsEngine()
    private val voskEngine = VoskSttEngine()
    private val lanEngine = LanTtsEngine()
    private var kokoroCandidateDir: String = ""

    init {
        refreshDownloadedStates()
        restoreSelections()
        initializeTts()
        // Restore LAN TTS server address and probe in background.
        val lanUrl = prefs.getString("lan_tts_url", "") ?: ""
        if (lanUrl.isNotBlank()) {
            lanEngine.setServer(lanUrl)
            scope.launch(Dispatchers.IO) { lanEngine.probe() }
        }
        // Lazy-load whichever real engines back the current selection.
        selectedTtsModel?.takeIf { it.id == "tts_kokoro" && it.isDownloaded }?.let { ensureKokoroLoaded() }
        selectedSttModel?.takeIf { it.id.startsWith("stt_vosk") && it.isDownloaded }?.let { ensureVoskLoaded(it.id) }
    }

    // ───────────────────────── model files ─────────────────────────

    private fun modelDirFor(id: String) = File(modelsDir, id)
    private fun archiveFor(id: String) = File(modelsDir, "$id.download")

    private fun isExtracted(id: String): Boolean {
        val dir = modelDirFor(id)
        if (!dir.isDirectory) return false
        return when (id) {
            "tts_kokoro" ->
                (File(dir, "model.onnx").exists() || File(dir, "model.int8.onnx").exists()) &&
                    File(dir, "voices.bin").length() > 0 && File(dir, "tokens.txt").exists()
            else -> dir.walkTopDown().any {
                it.isFile && (it.name.endsWith(".mdl") || it.name.endsWith(".am.yaml") || it.name == "circular-am.yaml")
            }
        }
    }

    private fun refreshDownloadedStates() {
        val current = _models.value.toMutableList()
        for (i in current.indices) {
            val m = current[i]
            if (m.downloadUrl.isBlank()) continue
            if (isExtracted(m.id)) {
                current[i] = m.copy(isDownloaded = true, isDownloading = false, downloadProgress = 1f)
            } else {
                // stale partial archive? delete so retry is clean
                val a = archiveFor(m.id)
                if (a.exists() && !m.isDownloading) a.delete()
                current[i] = m.copy(isDownloaded = false)
            }
        }
        _models.value = current
    }

    private fun restoreSelections() {
        val stt = prefs.getString("stt", "stt_system") ?: "stt_system"
        val tts = prefs.getString("tts", "tts_system") ?: "tts_system"
        applySelection(VoiceModelType.STT, stt)
        applySelection(VoiceModelType.TTS, tts)
    }

    private fun applySelection(type: VoiceModelType, id: String) {
        val target = _models.value.find { it.id == id && it.type == type } ?: return
        if (!target.isDownloaded && target.downloadUrl.isNotBlank()) return
        val list = _models.value.toMutableList()
        for (i in list.indices) {
            if (list[i].type == type) list[i] = list[i].copy(isSelected = list[i].id == id)
        }
        _models.value = list
    }

    /**
     * Downloads a real model archive, extracts it and verifies contents.
     */
    fun downloadModel(modelId: String) {
        val model = _models.value.find { it.id == modelId } ?: return
        if (model.downloadUrl.isBlank() || model.isDownloading || model.isDownloaded) return

        updateModelItem(modelId) { it.copy(isDownloading = true, downloadProgress = 0.01f, loadError = "") }

        activeDownloadJob = scope.launch(Dispatchers.IO) {
            val archive = archiveFor(modelId)
            try {
                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android) Spark/1.0")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty body")
                    val totalBytes = if (body.contentLength() > 0) body.contentLength() else model.sizeBytes
                    var bytesRead = 0L
                    val buffer = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        FileOutputStream(archive).use { output ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                val p = (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                updateModelItem(modelId) { it.copy(downloadProgress = p) }
                            }
                        }
                    }
                }

                // Extract
                val dest = modelDirFor(modelId)
                dest.deleteRecursively()
                dest.mkdirs()
                if (model.downloadUrl.endsWith(".tar.bz2")) extractTarBz2(archive, dest)
                else extractZip(archive, dest)
                archive.delete()

                if (!isExtracted(modelId)) {
                    dest.deleteRecursively()
                    error("Extracted model failed verification")
                }
                updateModelItem(modelId) {
                    it.copy(isDownloaded = true, isDownloading = false, downloadProgress = 1f)
                }
                // Auto-select what the user just downloaded — that's the intent.
                selectModel(modelId)
            } catch (e: Throwable) {
                archive.delete()
                updateModelItem(modelId) {
                    it.copy(isDownloaded = false, isDownloading = false, downloadProgress = 0f,
                        loadError = e.message ?: "Download failed")
                }
            }
        }
    }

    private fun extractTarBz2(archive: File, dest: File) {
        FileInputStream(archive).buffered().use { fis ->
            BZip2CompressorInputStream(fis).use { bzin ->
                TarArchiveInputStream(bzin).use { tin ->
                    var entry = tin.nextEntry
                    while (entry != null) {
                        val out = sanitize(dest, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { fos -> tin.copyTo(fos) }
                        }
                        entry = tin.nextEntry
                    }
                }
            }
        }
        flattenSingleTopDir(dest)
    }

    private fun extractZip(archive: File, dest: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val out = sanitize(dest, entry.name)
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                }
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        flattenSingleTopDir(dest)
    }

    /** Zip-slip guard. */
    private fun sanitize(dest: File, name: String): File {
        val out = File(dest, name)
        if (!out.canonicalPath.startsWith(dest.canonicalPath + File.separator))
            throw java.io.IOException("Bad archive path: $name")
        return out
    }

    /** Models ship inside one top-level folder; move contents up so paths are stable. */
    private fun flattenSingleTopDir(dest: File) {
        val children = dest.listFiles() ?: return
        if (children.size == 1 && children[0].isDirectory) {
            val top = children[0]
            top.listFiles()?.forEach { it.renameTo(File(dest, it.name)) }
            top.delete()
        }
    }

    fun deleteModel(modelId: String) {
        if (modelId.endsWith("_system")) return
        modelDirFor(modelId).deleteRecursively()
        archiveFor(modelId).delete()
        val model = _models.value.find { it.id == modelId } ?: return
        when (modelId) {
            "tts_kokoro" -> kokoroEngine.release()
            else -> if (modelId.startsWith("stt_vosk")) voskEngine.release()
        }
        updateModelItem(modelId) {
            it.copy(isDownloaded = false, isDownloading = false, downloadProgress = 0f, isSelected = false, loadError = "")
        }
        if (model.isSelected) {
            selectModel(if (model.type == VoiceModelType.STT) "stt_system" else "tts_system")
        }
    }

    fun setLanServerUrl(url: String) {
        lanEngine.setServer(url)
        prefs.edit().putString("lan_tts_url", url).apply()
        scope.launch(Dispatchers.IO) {
            val ok = lanEngine.probe()
            updateModelItem("tts_lan") {
                it.copy(loadError = if (ok) "" else "Unreachable at " + lanEngine.getServer())
            }
        }
    }

    fun getLanServerUrl(): String = prefs.getString("lan_tts_url", "") ?: ""

    fun getVoiceSystemPrompt(): String =
        prefs.getString("voice_system_prompt", null) ?: DEFAULT_VOICE_SYSTEM_PROMPT

    fun setVoiceSystemPrompt(text: String) {
        prefs.edit().putString("voice_system_prompt", text.trim()).apply()
    }

    fun selectModel(modelId: String) {
        val target = _models.value.find { it.id == modelId } ?: return
        if (!target.isDownloaded && target.downloadUrl.isNotBlank()) return
        applySelection(target.type, modelId)
        prefs.edit()
            .putString(if (target.type == VoiceModelType.STT) "stt" else "tts", modelId)
            .apply()
        // Bring the real engine up for the new selection.
        scope.launch(Dispatchers.IO) {
            when {
                modelId == "tts_kokoro" -> ensureKokoroLoaded()
                modelId.startsWith("stt_vosk") -> ensureVoskLoaded(modelId)
            }
        }
    }

    private fun ensureKokoroLoaded() {
        val dir = modelDirFor("tts_kokoro")
        if (!isExtracted("tts_kokoro")) return
        if (kokoroCandidateDir == dir.canonicalPath && kokoroEngine.isReady) return
        val err = kokoroEngine.load(dir)
        kokoroCandidateDir = dir.canonicalPath
        updateModelItem("tts_kokoro") { it.copy(loadError = err) }
    }

    private fun ensureVoskLoaded(modelId: String) {
        val dir = modelDirFor(modelId)
        if (!isExtracted(modelId)) return
        if (voskEngine.isReady) return
        val err = voskEngine.load(dir)
        updateModelItem(modelId) { it.copy(loadError = err) }
    }

    private fun updateModelItem(id: String, transform: (VoiceModelInfo) -> VoiceModelInfo) {
        val list = _models.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = transform(list[index])
            _models.value = list
        }
    }

    // ───────────────────────── system TTS ─────────────────────────

    private fun initializeTts() {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsInitialized = true
                    tts.setPitch(1.0f)
                    tts.setSpeechRate(1.05f)
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _voiceState.value = VoiceState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            _voiceState.value = VoiceState.IDLE
                            if (isConversationEnabled()) scheduleAutoListen()
                        }

                        override fun onError(utteranceId: String?) {
                            _voiceState.value = VoiceState.IDLE
                            if (isConversationEnabled()) scheduleAutoListen()
                        }
                    })
                }
            }
        }
    }

    // ───────────────────────── listening ─────────────────────────

    /**
     * Starts listening with the SELECTED engine: Vosk when a downloaded Vosk
     * model is selected & loaded, otherwise the Android system recognizer.
     */
    fun startListening(onTranscriptionComplete: (String) -> Unit) {
        stopSpeaking()
        val stt = selectedSttModel
        if (stt != null && stt.id.startsWith("stt_vosk")) {
            // Ensure engine is loaded for the selected model (async, then fall through next tap)
            if (!voskEngine.isReady) {
                ensureVoskLoaded(stt.id)
                if (!voskEngine.isReady) {
                    _voiceState.value = VoiceState.IDLE
                    return
                }
            }
            _liveSpokenText.value = ""
            _voiceState.value = VoiceState.LISTENING
            mainHandler.post {
                val started = voskEngine.startListening(
                    sampleRate = 16000f,
                    onPartial = { text ->
                        _liveSpokenText.value = text
                        _rmsDecibels.value = 5f // Vosk gives no rms; mid-level pulse
                    },
                    onFinal = { text ->
                        _liveSpokenText.value = text
                        _rmsDecibels.value = 0f
                        voskEngine.stop()
                        if (text.isNotBlank()) {
                            errorStreak = 0
                            _voiceState.value = VoiceState.THINKING
                            onTranscriptionComplete(text)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                            // silence in conversation mode: try again, cap the loop
                            if (isConversationEnabled() && errorStreak < 3) scheduleAutoListen()
                            else if (isConversationEnabled()) endConversation()
                        }
                    },
                    onError = { _ ->
                        _rmsDecibels.value = 0f
                        voskEngine.stop()
                        _voiceState.value = VoiceState.IDLE
                        if (isConversationEnabled() && errorStreak < 3) {
                            errorStreak++
                            scheduleAutoListen()
                        } else if (isConversationEnabled()) endConversation()
                    }
                )
                if (!started) {
                    _voiceState.value = VoiceState.IDLE
                    if (isConversationEnabled() && errorStreak < 3) {
                        errorStreak++
                        scheduleAutoListen()
                    }
                }
            }
            return
        }

        // System recognizer
        mainHandler.post {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _voiceState.value = VoiceState.LISTENING
                            _liveSpokenText.value = ""
                        }

                        override fun onBeginningOfSpeech() {
                            _voiceState.value = VoiceState.LISTENING
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            _rmsDecibels.value = rmsdB.coerceIn(0f, 10f)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _voiceState.value = VoiceState.THINKING
                            _rmsDecibels.value = 0f
                        }

                        override fun onError(error: Int) {
                            _voiceState.value = VoiceState.IDLE
                            _rmsDecibels.value = 0f
                            // RESTART/TIMEOUT/CLIENT are the recognizer's natural
                            // end-of-utterance path in conversation mode — re-arm.
                            if (isConversationEnabled() &&
                                (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT)) {
                                if (errorStreak < 3) scheduleAutoListen() else endConversation()
                            } else if (isConversationEnabled() && errorStreak < 3) {
                                errorStreak++
                                scheduleAutoListen()
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            _liveSpokenText.value = text
                            _voiceState.value = VoiceState.THINKING
                            _rmsDecibels.value = 0f
                            if (text.isNotBlank()) {
                                errorStreak = 0
                                onTranscriptionComplete(text)
                            } else {
                                _voiceState.value = VoiceState.IDLE
                                if (isConversationEnabled() && errorStreak < 3) scheduleAutoListen()
                                else if (isConversationEnabled()) endConversation()
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            _liveSpokenText.value = text
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                speechRecognizer?.startListening(intent)
            } else {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                voskEngine.stopListening()
            } catch (_: Exception) {}
            _voiceState.value = VoiceState.IDLE
            _rmsDecibels.value = 0f
        }
    }

    // ───────────────────────── speaking ─────────────────────────

    // ───────────────────── conversation mode ─────────────────────
    //
    // When enabled, the assistant loops: listen → transcribe → LLM → speak →
    // listen again — no mic taps. Talking while it speaks barges in: a raw
    // AudioRecord energy monitor (VOICE_COMMUNICATION = platform AEC) stops
    // playback and hands the mic to the recognizer.

    @Volatile private var conversationActive = false
    private var onAutoListen: (() -> Unit)? = null
    private var onBarge: (() -> Unit)? = null
    @Volatile private var bargeRunning = false
    private var bargeThread: Thread? = null
    @Volatile private var bargeRecord: android.media.AudioRecord? = null
    private var errorStreak = 0

    fun isConversationEnabled(): Boolean = prefs.getBoolean("conversation_mode", false)
    fun setConversationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("conversation_mode", enabled).apply()
    }

    /** Barge-in works in push-to-talk mode too, not only hands-free. */
    /** VOICE_COMMUNICATION forces call-mode audio (AEC+NS) which on Pixels
     *  adds an audible high-pitch whine to playback. Default to the plain
     *  recognition mic; the calibration-floor handles echo without AEC. */
    fun isBargeCallMic(): Boolean = prefs.getBoolean("barge_call_mic", false)
    fun setBargeCallMic(on: Boolean) { prefs.edit().putBoolean("barge_call_mic", on).apply() }

    fun isBargeEnabled(): Boolean = prefs.getBoolean("barge_enabled", true)
    fun setBargeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("barge_enabled", enabled).apply()
        if (!enabled) stopBargeMonitor()
    }

    // Tunables (persisted) — surfaced in the TTS settings sheet
    var ttsSpeed: Float
        get() = prefs.getFloat("tts_speed", 1.0f).coerceIn(0.5f, 2.0f)
        set(v) { prefs.edit().putFloat("tts_speed", v.coerceIn(0.5f, 2.0f)).apply() }
    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f).coerceIn(0.5f, 2.0f)
        set(v) { prefs.edit().putFloat("tts_pitch", v.coerceIn(0.5f, 2.0f)).apply() }
    var ttsSpeakerId: Int
        get() = prefs.getInt("tts_sid", 0).coerceIn(0, 53)
        set(v) { prefs.edit().putInt("tts_sid", v.coerceIn(0, 53)).apply() }
    var bargeThreshold: Float
        get() = prefs.getFloat("barge_threshold", 500f).coerceIn(150f, 3000f)
        set(v) { prefs.edit().putFloat("barge_threshold", v.coerceIn(150f, 3000f)).apply() }
    var bargeMultiplier: Float
        get() = prefs.getFloat("barge_multiplier", 1.8f).coerceIn(1.2f, 5.0f)
        set(v) { prefs.edit().putFloat("barge_multiplier", v.coerceIn(1.2f, 5.0f)).apply() }
    var bargeWindowMs: Int
        get() = prefs.getInt("barge_window_ms", 260).coerceIn(120, 800)
        set(v) { prefs.edit().putInt("barge_window_ms", v.coerceIn(120, 800)).apply() }
    var autoListenDelayMs: Int
        get() = prefs.getInt("auto_listen_delay_ms", 500).coerceIn(0, 2000)
        set(v) { prefs.edit().putInt("auto_listen_delay_ms", v.coerceIn(0, 2000)).apply() }

    fun startConversation(onAutoListen: () -> Unit, onBarge: () -> Unit) {
        this.onAutoListen = onAutoListen
        this.onBarge = onBarge
        conversationActive = true
        errorStreak = 0
    }

    fun endConversation() {
        conversationActive = false
        onAutoListen = null
        onBarge = null
        stopBargeMonitor()
    }

    /** Called by ViewModel when a turn ends without speech (e.g. error bubble). */
    fun notifyTurnComplete() = scheduleAutoListen()

    private fun scheduleAutoListen() {
        if (!conversationActive || !isConversationEnabled()) return
        val cb = onAutoListen ?: return
        mainHandler.postDelayed({ if (conversationActive && isConversationEnabled()) cb() }, autoListenDelayMs.toLong())
    }

    private fun startBargeMonitor() {
        if (!isBargeEnabled()) return
        val cb = onBarge ?: return // set when voice overlay is open
        if (bargeRunning) return
        bargeRunning = true
        bargeThread = Thread {
            try {
                val sr = 16000
                val minBuf = android.media.AudioRecord.getMinBufferSize(
                    sr,
                    android.media.AudioFormat.CHANNEL_IN_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return@Thread
                val rec = try {
                    android.media.AudioRecord(
                        if (isBargeCallMic())
                            android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION
                        else
                            android.media.MediaRecorder.AudioSource.MIC,
                        sr,
                        android.media.AudioFormat.CHANNEL_IN_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuf * 2, 4096)
                    )
                } catch (_: Throwable) { null } ?: return@Thread
                if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) {
                    try { rec.release() } catch (_: Throwable) {}
                    return@Thread
                }
                bargeRecord = rec
                val buf = ShortArray(1024)
                val framesNeeded = (bargeWindowMs.toLong() * 16 / buf.size).coerceAtLeast(1).toInt()
                var voiced = 0
                rec.startRecording()

                fun frameRms(): Double {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) return -1.0
                    var sum = 0.0
                    for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                    return kotlin.math.sqrt(sum / n)
                }

                // Calibration must track ACTUAL speech, not wall-clock: the
                // mic opens when speaking starts, but the first audio arrives
                // ~1s later (LLM + synth latency). Calibrating on the silence
                // before that leaves a near-zero floor, so the first word's
                // own speaker echo blows past it = instant self-interrupt.
                // Now: ignore everything until the mic hears continuous audio
                // (the assistant's echo), then set the barge floor from the
                // echo's own loudness, slowly-adapting upward.
                val gate = 120.0            // rms above room silence
                val warmCap = (6000L * 16 / buf.size).coerceAtLeast(4).toInt()
                var calFrames = 0
                var consecLoud = 0
                var armed = false
                var floor = 1.0
                var fired = false
                while (bargeRunning) {
                    val rms = frameRms()
                    if (rms < 0) break
                    val trig = maxOf(bargeThreshold, (floor * bargeMultiplier).toFloat())
                    if (!armed) {
                        if (rms > gate) consecLoud++ else consecLoud = 0
                        if (rms > floor) floor = rms
                        calFrames++
                        // arm once real audio has been seen for ~200ms
                        if (consecLoud >= 3 && calFrames >= 3) armed = true
                        if (calFrames >= warmCap) armed = true
                    } else {
                        // slow rising adaptation only (guards louder sentences
                        // without swallowing a user barge: 260ms trigger is
                        // far faster than the ~4s adaptation time constant)
                        if (rms > floor) floor += (rms - floor) * 0.004
                    }
                    if (rms > trig) voiced++ else voiced = 0
                    if (voiced >= framesNeeded) { fired = true; break }
                }
                if (fired) {
                    bargeRunning = false
                    mainHandler.post {
                        stopBargeMonitor()
                        cb?.invoke()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                bargeRunning = false
                val r = bargeRecord
                bargeRecord = null
                try { r?.stop() } catch (_: Throwable) {}
                try { r?.release() } catch (_: Throwable) {}
            }
        }.also { it.isDaemon = true; it.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> }; it.start() }
    }

    private fun stopBargeMonitor() {
        bargeRunning = false
        val r = bargeRecord
        bargeRecord = null
        try { r?.stop() } catch (_: Throwable) {}
        try { r?.release() } catch (_: Throwable) {}
        bargeThread = null
    }

    /** True when the LAN Kokoro server is the selected, healthy TTS engine. */
    fun canStreamViaLan(): Boolean =
        selectedTtsModel?.id == "tts_lan" && lanEngine.isHealthy

    @Volatile private var activeLanSession: LanTtsEngine.LanTtsSession? = null

    /** Public markdown scrubber for feeding gate sentences from the ViewModel. */
    fun cleanForSpeech(raw: String): String = cleanTextForSpeech(raw)

    fun setSpeakingState() {
        withMain { _voiceState.value = VoiceState.SPEAKING }
        startBargeMonitor()
    }

    /** Update the on-screen live speech caption while tokens stream in. */
    fun setLiveSpeechText(text: String) { withMain { _liveAiSpeechText.value = text } }

    /**
     * Opens a streaming LAN session (no state change — caller flips to
     * SPEAKING when the first audio actually arrives). Returns null when the
     * LAN engine isn't usable. Caller must run session.join() concurrently
     * with the token feed, then finish() it when the LLM stream ends.
     */
    fun openLanSpeechSession(): LanTtsEngine.LanTtsSession? {
        if (!canStreamViaLan()) return null
        lanEngine.sid = ttsSpeakerId
        return lanEngine.openSession(speed = ttsSpeed.toDouble()).also { activeLanSession = it }
    }

    /**
     * Speaks complete text over LAN using the session pipeline (sentence gate
     * + prefetching AudioTrack). Blocking — call on IO dispatcher.
     */
    fun speakTextStreaming(text: String): Boolean {
        val session = openLanSpeechSession() ?: return false
        activeLanSession = session
        val clean = cleanTextForSpeech(text)
        setSpeakingState()
        if (clean.isNotBlank()) _liveAiSpeechText.value = clean
        val gate = SentenceGate(minWords = 3) { s -> session.enqueue(s) }
        gate.feed(clean)
        gate.flush()
        session.finish()
        val r = session.join()
        if (activeLanSession === session) activeLanSession = null
        withMain { _voiceState.value = VoiceState.IDLE }
        if (isConversationEnabled()) scheduleAutoListen()
        return r
    }

    /**
     * Speaks with the SELECTED engine: Kokoro neural voice when downloaded,
     * loaded and selected — otherwise the system TextToSpeech.
     */
    fun speakText(text: String) {
        val clean = cleanTextForSpeech(text)
        if (clean.isBlank()) return

        _liveAiSpeechText.value = clean
        _voiceState.value = VoiceState.SPEAKING

        val useLan = selectedTtsModel?.id == "tts_lan" && lanEngine.isHealthy
        val useKokoro = selectedTtsModel?.id == "tts_kokoro" && kokoroEngine.isReady
        if (useLan) {
            scope.launch(Dispatchers.Default) {
                val ok = speakTextStreaming(clean)
                withMain { _voiceState.value = VoiceState.IDLE }
                // LAN dead mid-playback -> fall back to on-device Kokoro, then system
                if (!ok) {
                    if (kokoroEngine.isReady) {
                        kokoroEngine.speakBlocking(clean)
                        withMain { _voiceState.value = VoiceState.IDLE }
                        if (isConversationEnabled()) scheduleAutoListen()
                    } else {
                        speakViaSystem(clean)
                    }
                }
            }
            return
        }
        if (useKokoro) {
            scope.launch(Dispatchers.Default) {
                val ok = kokoroEngine.speakBlocking(clean)
                withMain { _voiceState.value = VoiceState.IDLE }
                if (!ok) speakViaSystem(clean)
                else withMain { _voiceState.value = VoiceState.IDLE }
                if (ok && isConversationEnabled()) scheduleAutoListen()
            }
            return
        }
        speakViaSystem(clean)
    }

    private fun speakViaSystem(clean: String) {
        if (isTtsInitialized) {
            textToSpeech?.setSpeechRate(ttsSpeed * 1.05f)
            textToSpeech?.setPitch(ttsPitch)
            textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tailnode_voice_speech")
        } else {
            _voiceState.value = VoiceState.IDLE
        }
    }

    private fun withMain(block: () -> Unit) = mainHandler.post(block)

    fun stopSpeaking() {
        if (isTtsInitialized) {
            textToSpeech?.stop()
        }
        kokoroEngine.stopPlayback()
        activeLanSession?.abort()
        activeLanSession = null
        lanEngine.stop()
        stopBargeMonitor()
        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun replayLastSpeech() {
        val last = _liveAiSpeechText.value
        if (last.isNotBlank()) {
            speakText(last)
        }
    }

    fun clearLiveTranscripts() {
        _liveSpokenText.value = ""
        _liveAiSpeechText.value = ""
    }

    fun setThinkingState() {
        _voiceState.value = VoiceState.THINKING
    }

    fun setIdleState() {
        _voiceState.value = VoiceState.IDLE
    }

    private fun cleanTextForSpeech(raw: String): String {
        return raw
            .replace("```[\\s\\S]*?```".toRegex(), " Code block omitted. ")
            .replace("`[^`]+`".toRegex(), "")
            .replace("#+".toRegex(), "")
            .replace("\\*+".toRegex(), "")
            .replace("https?://\\S+".toRegex(), "")
            .trim()
    }

    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        kokoroEngine.release()
        voskEngine.release()
        lanEngine.stop()
        activeDownloadJob?.cancel()
    }
}
