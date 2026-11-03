package com.example.readaloud

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The read-aloud engine. Runs in the app process while enabled in
 * Accessibility settings. Provides:
 *  - a draggable floating bubble (tap = read screen, tap again = stop)
 *  - an optional always-on voice trigger ("read it" / "stop")
 *  - screen-text capture with optional auto-scroll stitching
 *  - playback through VoiceEngineManager (LAN Kokoro → Kokoro → system)
 */
class ReadAloudService : AccessibilityService() {

    companion object {
        private const val TAG = "ReadAloudService"
        private const val NOTIF_ID = 9_313
        @Volatile var running = false
            private set
    }

    private val main = Handler(Looper.getMainLooper())
    private var bubble: ReaderBubble? = null

    // ── lifecycle ───────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        ReadAloudController.init(applicationContext)
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = 0 // we pull the tree on demand; no event storm
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        ReadAloudController.registerService(
            onCaptureAndRead = { captureAndRead() },
            onStop = { stopEverything() },
            onTestSpeak = { t -> ReadAloudController.speakDirect(t) }
        )
        refreshBubble()
        refreshVoiceTrigger()
        // Live-apply settings changes made in the ReadAloudScreen while the
        // service stays connected (no need to toggle accessibility off/on).
        serviceScope.launch {
            ReadAloudController.bubbleEnabled.collect { refreshBubble() }
        }
        serviceScope.launch {
            ReadAloudController.voiceTriggerEnabled.collect { refreshVoiceTrigger() }
        }
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        main.post {
            stopEverything()
            bubble?.dismiss()
            bubble = null
            stopVoiceTrigger()
            stopForegroundTriggerService()
        }
        ReadAloudController.unregisterService()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ── bubble ──────────────────────────────────────────────────────

    private fun refreshBubble() {
        main.post {
            val want = ReadAloudController.bubbleEnabled.value &&
                com.example.readaloud.ReadAloudController.canDrawOverlays(this)
            if (want && bubble == null) {
                bubble = ReaderBubble(this) { bubbleReadPressed() }.also { it.show() }
            } else if (!want && bubble != null) {
                bubble?.dismiss(); bubble = null
            }
        }
    }

    private fun bubbleReadPressed() {
        when (ReadAloudController.state.value) {
            ReaderState.SPEAKING, ReaderState.CAPTURING -> stopEverything()
            else -> captureAndRead()
        }
    }

    // ── capture + read ──────────────────────────────────────────────

    @Volatile private var capturing = false

    private fun captureAndRead() {
        if (capturing) return
        capturing = true
        stopVoiceListener()
        ReadAloudController.onCaptureStarted()
        Thread {
            try {
                var text = TextExtractor.extract(rootInActiveWindow)
                if (text.isBlank()) {
                    text = captureWithAutoScroll()
                } else if (ReadAloudController.autoScrollEnabled.value) {
                    text = captureWithAutoScroll()
                }
                if (text.isBlank()) {
                    ReadAloudController.speakDirect("I can't find any readable text on this screen.")
                } else {
                    ReadAloudController.speakDirect(text)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "capture failed", e)
                ReadAloudController.onSpeechEnded()
            } finally {
                capturing = false
                if (voiceTriggerWanted.get()) startVoiceListener()
            }
        }.apply {
            name = "ra-capture"
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, t -> Log.e(TAG, "capture thread", t) }
        }.start()
    }

    /**
     * Scroll the foreground window forward repeatedly, extracting new lines
     * each step and appending. Stops when two consecutive scrolls add nothing
     * or after MAX_SCROLLS.
     */
    private fun captureWithAutoScroll(): String {
        val collected = ArrayList<String>()
        val seen = HashSet<String>()
        var noNewRounds = 0
        var scrolls = 0
        val maxScrolls = if (ReadAloudController.autoScrollEnabled.value) 14 else 0

        while (true) {
            val root: AccessibilityNodeInfo = rootInActiveWindow ?: break
            val text = TextExtractor.extract(root)
            var added = 0
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isNotEmpty() && seen.add(line)) { collected.add(line); added++ }
            }
            if (scrolls >= maxScrolls) break
            if (added == 0) { noNewRounds++; if (noNewRounds >= 2) break } else noNewRounds = 0
            if (!scrollForward()) break
            scrolls++
            Thread.sleep(380) // let the window settle + re-render
        }
        return collected.joinToString("\n")
    }

    /** Tries node ACTION_SCROLL_FORWARD first, falls back to a swipe. */
    private fun scrollForward(): Boolean {
        try {
            val root = rootInActiveWindow ?: return false
            val scroller = findScrollable(root)
            if (scroller != null &&
                scroller.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        } catch (_: Throwable) {}
        // swipe up (content moves up) in the middle third of the screen
        try {
            val dm = resources.displayMetrics
            val x = dm.widthPixels / 2f
            val path = Path().apply {
                moveTo(x, dm.heightPixels * 0.72f)
                lineTo(x, dm.heightPixels * 0.30f)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 260)).build()
            return performGesture(gesture)
        } catch (_: Throwable) { return false }
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        try {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val found = findScrollable(node.getChild(i))
                if (found != null) return found
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun stopEverything() {
        capturing = false
        ReadAloudController.engine().stopSpeaking()
        ReadAloudController.onSpeechEnded()
    }

    // ── voice trigger ("read it" / "stop") ──────────────────────────

    private val voiceTriggerWanted = AtomicBoolean(false)
    private var trigger: SpeechRecognizer? = null
    private var triggerStreak = 0
    @Volatile private var listenerActive = false

    private fun refreshVoiceTrigger() {
        voiceTriggerWanted.set(ReadAloudController.voiceTriggerEnabled.value)
        main.post {
            if (voiceTriggerWanted.get()) {
                startForegroundTriggerService()
                startVoiceListener()
            } else {
                stopVoiceListener()
                stopForegroundTriggerService()
            }
        }
    }


    private fun startVoiceListener() {
        if (listenerActive || capturing) return
        if (!voiceTriggerWanted.get()) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        listenerActive = true
        ReadAloudController.setStateListening(true)
        main.post {
            try {
                trigger?.destroy()
                trigger = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {
                            ReadAloudController.setStateListening(true)
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            listenerActive = false
                            ReadAloudController.setStateListening(false)
                            if (!voiceTriggerWanted.get()) return
                            triggerStreak++
                            if (triggerStreak > 6) {
                                // mic starved (no speech / other app holds it):
                                // back off loudly instead of burning battery
                                main.postDelayed({ if (voiceTriggerWanted.get()) { triggerStreak = 0; startVoiceListener() } }, 8_000)
                            } else {
                                main.postDelayed({ if (voiceTriggerWanted.get()) startVoiceListener() }, 450)
                            }
                        }
                        override fun onResults(results: android.os.Bundle?) {
                            listenerActive = false
                            ReadAloudController.setStateListening(false)
                            val heard = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()?.lowercase()?.trim().orEmpty()
                            if (heard.isNotEmpty()) triggerStreak = 0
                            val norm = heard.replace(Regex("[^a-z ]"), "").trim()
                            when {
                                norm.contains("stop") -> { stopEverything() }
                                norm.contains("read") -> { bubbleReadPressed() }
                            }
                            if (voiceTriggerWanted.get()) {
                                main.postDelayed({ if (voiceTriggerWanted.get() && !capturing) startVoiceListener() }, 350)
                            }
                        }
                        override fun onPartialResults(partialResults: android.os.Bundle?) {}
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                }
                trigger?.startListening(intent)
            } catch (_: Exception) {
                listenerActive = false
            }
        }
    }

    private fun stopVoiceListener() {
        listenerActive = false
        main.post {
            try { trigger?.destroy() } catch (_: Exception) {}
            trigger = null
        }
    }

    private fun stopVoiceTrigger() {
        voiceTriggerWanted.set(false)
        stopVoiceListener()
    }

    // While the always-on trigger runs we must be a microphone foreground
    // service, or Android 11+ blocks mic access once the app leaves view.
    private fun startForegroundTriggerService() {
        val channelId = "spark_read_aloud"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        channelId, "Read Aloud",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        @SuppressLint("WrongConstant")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        builder.setContentTitle("Read Aloud listening")
            .setContentText("Say \"read it\" to read the screen. Turn off in Spark > Read Aloud.")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, builder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, builder.build())
            }
        } catch (_: Exception) {}
    }

    private fun stopForegroundTriggerService() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }
}
