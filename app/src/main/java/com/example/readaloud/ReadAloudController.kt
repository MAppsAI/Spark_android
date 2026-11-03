package com.example.readaloud

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.voice.VoiceEngineManager
import com.example.voice.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReaderState { IDLE, LISTENING, CAPTURING, SPEAKING }

/**
 * Shared state + speech plumbing for Read Aloud. Lives in the app process;
 * the AccessibilityService (owner of capture) and the settings screen both
 * talk to this object. Persisted toggles live in prefs "read_aloud".
 */
object ReadAloudController {

    @Volatile private var initialised = false
    private lateinit var appContext: Context

    private lateinit var prefs: android.content.SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var _engine: VoiceEngineManager? = null

    // Own VoiceEngineManager so reading works even when the app UI was never
    // opened this boot (accessibility services start at boot). Shares the
    // "voice_engine" prefs with the chat voice mode — same voice, LAN URL.
    fun engine(): VoiceEngineManager {
        _engine?.let { return it }
        synchronized(this) {
            _engine?.let { return it }
            return VoiceEngineManager(appContext, scope).also { _engine = it }
        }
    }

    private fun prefBoolean(key: String, def: Boolean): Boolean =
        prefs.getBoolean(key, def)

    private val _bubbleEnabled = MutableStateFlow(false)
    val bubbleEnabled = _bubbleEnabled.asStateFlow()

    private val _voiceTriggerEnabled = MutableStateFlow(false)
    val voiceTriggerEnabled = _voiceTriggerEnabled.asStateFlow()

    private val _autoScrollEnabled = MutableStateFlow(true)
    val autoScrollEnabled = _autoScrollEnabled.asStateFlow()

    private val _state = MutableStateFlow(ReaderState.IDLE)
    val state = _state.asStateFlow()

    private val stateListeners = java.util.Collections.synchronizedList(
        mutableListOf<(ReaderState) -> Unit>()
    )

    fun addStateListener(l: (ReaderState) -> Unit) {
        stateListeners.add(l); l(_state.value)
    }

    fun removeStateListener(l: (ReaderState) -> Unit) { stateListeners.remove(l) }

    private fun setState(s: ReaderState) {
        _state.value = s
        synchronized(stateListeners) { stateListeners.toList() }.forEach { it(s) }
    }

    /** Last extraction result, for the settings screen to preview. */
    private val _lastText = MutableStateFlow("")
    val lastText = _lastText.asStateFlow()

    private var wakeLock: PowerManager.WakeLock? = null

    fun init(context: Context) {
        if (initialised) return
        synchronized(this) {
            if (initialised) return
            appContext = context.applicationContext
            prefs = appContext.getSharedPreferences("read_aloud", Context.MODE_PRIVATE)
            _bubbleEnabled.value = prefBoolean("bubble", true)
            _voiceTriggerEnabled.value = prefBoolean("voice_trigger", false)
            _autoScrollEnabled.value = prefBoolean("auto_scroll", true)
            initialised = true
            // Watch the engine's own state: when playback finishes naturally
            // (no bubble press), release the wake lock and go IDLE.
            scope.launch {
                engine().voiceState.collect { vs ->
                    if (vs == VoiceState.IDLE && _state.value == ReaderState.SPEAKING) {
                        onSpeechEnded()
                    }
                }
            }
        }
    }

    private fun requirePrefs(): android.content.SharedPreferences = prefs

    fun setBubbleEnabled(on: Boolean) {
        requirePrefs().edit().putBoolean("bubble", on).apply()
        _bubbleEnabled.value = on
    }

    fun setVoiceTriggerEnabled(on: Boolean) {
        requirePrefs().edit().putBoolean("voice_trigger", on).apply()
        _voiceTriggerEnabled.value = on
    }

    fun setAutoScrollEnabled(on: Boolean) {
        requirePrefs().edit().putBoolean("auto_scroll", on).apply()
        _autoScrollEnabled.value = on
    }

    fun isReady(): Boolean = initialised

    // ── capture hand-off ────────────────────────────────────────────
    // The AccessibilityService registers its capture function here; the
    // settings screen calls readScreen() which delegates to it.

    private var captureAndRead: (() -> Unit)? = null
    private var stopAction: (() -> Unit)? = null
    private var testSpeakAction: ((String) -> Unit)? = null

    fun registerService(
        onCaptureAndRead: () -> Unit,
        onStop: () -> Unit,
        onTestSpeak: (String) -> Unit
    ) {
        captureAndRead = onCaptureAndRead
        stopAction = onStop
        testSpeakAction = onTestSpeak
    }

    fun unregisterService() {
        captureAndRead = null
        stopAction = null
        testSpeakAction = null
        setState(ReaderState.IDLE)
    }

    /** True when the accessibility service is connected right now. */
    fun serviceConnected(): Boolean = captureAndRead != null

    fun readScreen() {
        val action = captureAndRead
        if (action == null) {
            // Service not enabled — jump the user straight to a11y settings.
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { appContext.startActivity(intent) } catch (_: Exception) {}
            return
        }
        action()
    }

    fun stop() { stopAction?.invoke() }

    fun testSpeak(text: String) {
        val action = testSpeakAction
        if (action != null) action(text)
        else speakDirect(text)
    }

    // ── speaking ────────────────────────────────────────────────────

    fun onCaptureStarted() { setState(ReaderState.CAPTURING) }

    /** True when the engine can stream audio sentence-by-sentence right now. */
    fun canStream(): Boolean = try { engine().canStreamViaLan() } catch (_: Exception) { false }

    /** Streaming path: audio begins while capture is still scrolling. */
    fun onStreamingSpeechStarted() {
        setState(ReaderState.SPEAKING)
        acquireWake()
        engine().setSpeakingState()
    }

    fun setLastText(text: String) { _lastText.value = text }

    fun onSpeechStarted(text: String) {
        _lastText.value = text
        setState(ReaderState.SPEAKING)
        acquireWake()
        engine().setSpeakingState()
    }

    fun speakDirect(text: String) {
        val clean = engine().cleanForSpeech(text)
        if (clean.isBlank()) { setState(ReaderState.IDLE); return }
        onSpeechStarted(clean)
        engine().speakText(clean)
        // Watchdog: if the engine never actually reached SPEAKING (system TTS
        // init failure right after boot etc.), don't sit stuck holding the wakelock.
        scope.launch {
            kotlinx.coroutines.delay(4_000)
            if (_state.value == ReaderState.SPEAKING &&
                engine().voiceState.value != VoiceState.SPEAKING) {
                onSpeechEnded()
            }
        }
    }

    fun onSpeechEnded() {
        setState(ReaderState.IDLE)
        releaseWake()
    }

    fun setStateListening(listening: Boolean) {
        if (listening && _state.value == ReaderState.IDLE) setState(ReaderState.LISTENING)
        else if (!listening && _state.value == ReaderState.LISTENING) setState(ReaderState.IDLE)
    }

    // ── wake lock: LAN TTS keeps fetching audio while the phone idles ──

    private fun acquireWake() {
        try {
            if (wakeLock == null) {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spark:readaloud")
                    .apply { setReferenceCounted(false) }
            }
            wakeLock?.takeIf { !it.isHeld }?.acquire(10 * 60 * 1000L)
        } catch (_: Exception) {}
    }

    private fun releaseWake() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
    }

    // ── permission helpers ──────────────────────────────────────────

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${ReadAloudService::class.java.name}"
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun canDrawOverlays(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(context)
}
