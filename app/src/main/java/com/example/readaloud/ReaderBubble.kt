package com.example.readaloud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Draggable overlay bubble. Tap = read/stop (via [onPress]); drag = move.
 * Ring colour reflects reader state (idle cyan / speaking green /
 * listening amber), driven through setState().
 */
class ReaderBubble(
    private val context: Context,
    private val onPress: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val sizePx = (58 * density).toInt()

    private val ringPaintIdle = Color.parseColor("#3FC8F5")
    private val ringPaintBusy = Color.parseColor("#3DDC97")
    private val ringPaintListen = Color.parseColor("#F5A623")

    private var view: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var ringColor = ringPaintIdle
    private var glyph = "\u25B6" // ▶

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val tv = TextView(context)
        tv.text = glyph
        tv.textSize = 20f
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.setCircle(ringColor)

        params = WindowManager.LayoutParams(
            sizePx, sizePx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (density * 20).toInt()
            y = (density * 300).toInt()
        }

        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragged = false

        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragged && (abs(dx) > 18 * density || abs(dy) > 18 * density)) dragged = true
                    if (dragged) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try { wm.updateViewLayout(tv, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragged) onPress()
                    true
                }
                else -> true
            }
        }

        view = tv
        try { wm.addView(tv, params) } catch (_: Exception) { view = null }

        ReadAloudController.addStateListener(stateListener)
    }

    private val stateListener: (ReaderState) -> Unit = { s ->
        Handler(Looper.getMainLooper()).post { apply(s) }
    }

    private fun apply(s: ReaderState) {
        val tv = view ?: return
        ringColor = when (s) {
            ReaderState.IDLE -> ringPaintIdle
            ReaderState.LISTENING -> ringPaintListen
            ReaderState.CAPTURING -> ringPaintListen
            ReaderState.SPEAKING -> ringPaintBusy
        }
        glyph = when (s) {
            ReaderState.SPEAKING, ReaderState.CAPTURING -> "\u25A0" // ■
            else -> "\u25B6" // ▶
        }
        tv.text = glyph
        tv.setCircle(ringColor)
    }

    fun dismiss() {
        ReadAloudController.removeStateListener(stateListener)
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun TextView.setCircle(ring: Int) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E6101420"))
            setStroke((2 * density).toInt(), ring)
        }
        background = bg
        elevation = 8 * density
    }
}
