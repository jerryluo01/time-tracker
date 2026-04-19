package com.timetracker.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

/**
 * Full-screen transparent overlay that animates a glowing border around the screen edges.
 *
 * Two independent instances are created by TimerService — one for the app-session milestone,
 * one for the phone-session milestone — so both can fire simultaneously.
 *
 * Color and total duration are read at trigger time from the provided lambdas, so changes in
 * SettingsActivity take effect on the very next milestone without restarting anything.
 *
 * Animation: fade-in (15%) → hold → fade-out (15%), total = [durationProvider]ms.
 */
class PulseOverlay(
    private val context: Context,
    private val colorProvider: () -> Int,
    private val durationProvider: () -> Long = { 2200L }
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isShowing = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    )

    fun trigger() {
        mainHandler.post {
            if (isShowing) return@post
            isShowing = true

            // Create a fresh view on each trigger so it always uses the current color.
            val view = GlowBorderView(context, colorProvider())
            view.alpha = 0f
            windowManager.addView(view, layoutParams)
            startAnimation(view)
        }
    }

    private fun startAnimation(view: View) {
        val total = durationProvider().coerceAtLeast(600L)
        val ramp  = (total * 0.15f).toLong()   // 15% fade-in
        val hold  = total - ramp * 2            // 70% hold
        // 15% fade-out

        val fadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ramp
            addUpdateListener { view.alpha = it.animatedValue as Float }
        }
        val holdAnim = ValueAnimator.ofFloat(1f, 1f).apply { duration = hold }
        val fadeOut = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = ramp
            addUpdateListener { view.alpha = it.animatedValue as Float }
        }

        fadeIn.start()
        fadeIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                holdAnim.start()
                holdAnim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        fadeOut.start()
                        fadeOut.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                mainHandler.post { dismiss(view) }
                            }
                        })
                    }
                })
            }
        })
    }

    private fun dismiss(view: View) {
        if (!isShowing) return
        isShowing = false
        try { windowManager.removeView(view) } catch (_: Exception) {}
    }

    private class GlowBorderView(context: Context, color: Int) : View(context) {

        private val strokeWidth = 28f
        private val blurRadius  = 40f

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = this@GlowBorderView.strokeWidth
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.SOLID)
        }

        override fun onDraw(canvas: Canvas) {
            val inset = strokeWidth / 2f + blurRadius / 2f
            val rect  = RectF(inset, inset, width - inset, height - inset)
            val path  = Path().apply { addRoundRect(rect, 0f, 0f, Path.Direction.CW) }
            canvas.drawPath(path, paint)
        }
    }
}
