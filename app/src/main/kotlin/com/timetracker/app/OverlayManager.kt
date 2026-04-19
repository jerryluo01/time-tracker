package com.timetracker.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Manages the draggable pill-shaped floating overlay that shows both live timers.
 *
 * Layout:  ● APP_TIME  |  ● PHONE_TIME
 *           (app dot)      (phone dot)
 *
 * Dot colors follow the user's current pulse color settings so they stay in sync.
 *
 * Anchor mode (FLAG_NOT_TOUCHABLE):
 *   - All touches pass through to the app behind — fully click-through.
 *   - Drag is also disabled. Toggle via SettingsActivity → "Lock overlay".
 *   - Applied instantly when TimerService receives the settings change callback.
 *
 * Opacity:
 *   - Pill background alpha is read from SettingsManager at build time.
 *   - Default is ~28% (very transparent). Restart the service for changes to apply.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val posPrefs: SharedPreferences =
        context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
    private val settings = SettingsManager(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var pillView: LinearLayout
    private lateinit var appTimerText: TextView
    private lateinit var phoneTimerText: TextView
    private lateinit var appDot: TextView
    private lateinit var phoneDot: TextView
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var isAdded = false
    private var isAnchored = false

    // Drag state
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialParamX = 0
    private var initialParamY = 0

    fun addOverlay() {
        mainHandler.post {
            if (isAdded) return@post
            buildViews()
            windowManager.addView(pillView, layoutParams)
            isAdded = true
            // Apply saved anchor state immediately
            setAnchored(settings.isAnchored)
        }
    }

    fun removeOverlay() {
        mainHandler.post {
            if (!isAdded) return@post
            isAdded = false
            try { windowManager.removeView(pillView) } catch (_: Exception) {}
        }
    }

    /**
     * Tear down the current pill and rebuild it with the latest layout settings
     * (vertical/swap). Called when KEY_PILL_VERTICAL or KEY_TIMER_ORDER_SWAPPED changes.
     * Runs entirely on the main thread so there is no add/remove race.
     */
    fun rebuildOverlay() {
        mainHandler.post {
            if (!isAdded) return@post
            try { windowManager.removeView(pillView) } catch (_: Exception) {}
            isAdded = false
            buildViews()
            windowManager.addView(pillView, layoutParams)
            isAdded = true
            setAnchored(isAnchored)
        }
    }

    fun update(times: SessionTimes, showAppTimer: Boolean = true) {
        mainHandler.post {
            if (!isAdded) return@post
            appTimerText.text = if (showAppTimer) times.appFormatted else "–"
            phoneTimerText.text = times.phoneFormatted
            // Keep dot colors in sync with current settings
            appDot.setTextColor(settings.pulseColorApp)
            phoneDot.setTextColor(settings.pulseColorPhone)
        }
    }

    /** Brief scale pulse on the pill when a milestone fires. */
    fun pulse() {
        mainHandler.post {
            if (!isAdded) return@post
            ValueAnimator.ofFloat(1f, 1.12f, 1f).apply {
                duration = 300
                addUpdateListener { anim ->
                    val s = anim.animatedValue as Float
                    pillView.scaleX = s
                    pillView.scaleY = s
                }
                start()
            }
        }
    }

    /**
     * Apply or remove anchor mode.
     *
     * Anchored   → adds FLAG_NOT_TOUCHABLE: pill is fully click-through, drag disabled.
     * Unanchored → removes FLAG_NOT_TOUCHABLE: pill receives touches and is draggable.
     */
    fun setAnchored(anchored: Boolean) {
        mainHandler.post {
            isAnchored = anchored
            if (!isAdded) return@post
            layoutParams.flags = if (anchored) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            try { windowManager.updateViewLayout(pillView, layoutParams) } catch (_: Exception) {}
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // View construction
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildViews() {
        val dp = context.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()

        // Background: user's chosen opacity over the dark base color
        val alpha = (settings.pillOpacityPercent * 255 / 100).coerceIn(0, 255)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.argb(alpha, 0x1A, 0x1A, 0x2E))
        }

        appDot = TextView(context).apply {
            text = "●"
            textSize = 8f
            setTextColor(settings.pulseColorApp)
            setPadding(px(3f), 0, px(4f), 0)
        }

        appTimerText = TextView(context).apply {
            text = "0:00"
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        val divider = TextView(context).apply {
            text = " | "
            textSize = 12f
            setTextColor(Color.parseColor("#66FFFFFF"))
        }

        phoneDot = TextView(context).apply {
            text = "●"
            textSize = 8f
            setTextColor(settings.pulseColorPhone)
            setPadding(px(3f), 0, px(4f), 0)
        }

        phoneTimerText = TextView(context).apply {
            text = "0:00"
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        val isVertical = settings.isPillVertical
        val isSwapped  = settings.isTimerOrderSwapped

        // First timer slot: either (appDot + appTimerText) or (phoneDot + phoneTimerText)
        fun firstSlot()  = if (isSwapped) listOf(phoneDot, phoneTimerText)
                           else            listOf(appDot,   appTimerText)
        fun secondSlot() = if (isSwapped) listOf(appDot,   appTimerText)
                           else            listOf(phoneDot, phoneTimerText)

        pillView = LinearLayout(context).apply {
            background = bg
            setPadding(px(12f), px(6f), px(12f), px(6f))

            if (isVertical) {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL

                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    firstSlot().forEach { addView(it) }
                }
                val botRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    secondSlot().forEach { addView(it) }
                }
                addView(topRow)
                addView(botRow)
            } else {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                firstSlot().forEach  { addView(it) }
                addView(divider)
                secondSlot().forEach { addView(it) }
            }
        }

        val (savedX, savedY) = loadPosition()

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        pillView.setOnTouchListener { _, event ->
            if (isAnchored) return@setOnTouchListener false  // shouldn't fire, but guard anyway
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    initialParamX = layoutParams.x
                    initialParamY = layoutParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialParamX + (event.rawX - dragStartX).toInt()
                    layoutParams.y = initialParamY + (event.rawY - dragStartY).toInt()
                    if (isAdded) windowManager.updateViewLayout(pillView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    savePosition(layoutParams.x, layoutParams.y)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPosition(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val x = posPrefs.getInt("overlay_x", 40).coerceIn(0, metrics.widthPixels - 200)
        val y = posPrefs.getInt("overlay_y", 80).coerceIn(0, metrics.heightPixels - 100)
        return x to y
    }

    private fun savePosition(x: Int, y: Int) {
        posPrefs.edit().putInt("overlay_x", x).putInt("overlay_y", y).apply()
    }
}
