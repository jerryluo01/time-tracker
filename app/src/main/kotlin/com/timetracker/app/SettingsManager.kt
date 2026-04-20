package com.timetracker.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Central store for all user-adjustable preferences.
 * Backed by SharedPreferences ("settings") so changes persist across restarts.
 *
 * TimerService registers a SharedPreferences.OnSharedPreferenceChangeListener so that
 * anchor/opacity changes take effect immediately without restarting the service.
 */
class SettingsManager(context: Context) {

    val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Minutes between milestone nudges. Default 15. Options: 5,10,15,20,30,45,60. */
    var milestoneIntervalMin: Int
        get() = prefs.getInt(KEY_MILESTONE_INTERVAL, 15)
        set(v) = prefs.edit().putInt(KEY_MILESTONE_INTERVAL, v).apply()

    /** ARGB color used for the app-session pulse glow. Default green. */
    var pulseColorApp: Int
        get() = prefs.getInt(KEY_PULSE_COLOR_APP, Color.parseColor("#FF639922"))
        set(v) = prefs.edit().putInt(KEY_PULSE_COLOR_APP, v).apply()

    /** ARGB color used for the phone-session pulse glow. Default blue. */
    var pulseColorPhone: Int
        get() = prefs.getInt(KEY_PULSE_COLOR_PHONE, Color.parseColor("#FF378ADD"))
        set(v) = prefs.edit().putInt(KEY_PULSE_COLOR_PHONE, v).apply()

    /**
     * When true the overlay has FLAG_NOT_TOUCHABLE — all taps pass through to the app beneath.
     * Drag is also disabled. Toggle from SettingsActivity; changes apply immediately.
     */
    var isAnchored: Boolean
        get() = prefs.getBoolean(KEY_IS_ANCHORED, false)
        set(v) = prefs.edit().putBoolean(KEY_IS_ANCHORED, v).apply()

    /**
     * Pill background opacity, 0–100. Default 28 (≈28%).
     * Low enough to be unobtrusive; high enough to keep text legible.
     * Applied when the overlay is (re)built — restart the service for changes to show.
     */
    var pillOpacityPercent: Int
        get() = prefs.getInt(KEY_PILL_OPACITY, 28)
        set(v) = prefs.edit().putInt(KEY_PILL_OPACITY, v).apply()

    /**
     * When true, phone timer appears first (left/top), app timer second.
     * Default false = app timer first.
     */
    var isTimerOrderSwapped: Boolean
        get() = prefs.getBoolean(KEY_TIMER_ORDER_SWAPPED, false)
        set(v) = prefs.edit().putBoolean(KEY_TIMER_ORDER_SWAPPED, v).apply()

    /**
     * When true, the pill stacks both timers vertically instead of side-by-side.
     * Changes apply immediately — overlay is rebuilt via OnSharedPreferenceChangeListener.
     */
    var isPillVertical: Boolean
        get() = prefs.getBoolean(KEY_PILL_VERTICAL, false)
        set(v) = prefs.edit().putBoolean(KEY_PILL_VERTICAL, v).apply()

    /** False after the user explicitly taps Stop Tracking; prevents auto-restart on re-open. */
    var trackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_TRACKING_ENABLED, v).apply()

    /** Minutes of inactivity before timers reset. Default 15. */
    var gapThresholdMin: Int
        get() = prefs.getInt(KEY_GAP_THRESHOLD, 15)
        set(v) = prefs.edit().putInt(KEY_GAP_THRESHOLD, v).apply()

    /** Pill size as a percentage of the base size. Default 100 (Medium). */
    var pillSizePct: Int
        get() = prefs.getInt(KEY_PILL_SIZE, 100)
        set(v) = prefs.edit().putInt(KEY_PILL_SIZE, v).apply()

    companion object {
        const val KEY_MILESTONE_INTERVAL  = "milestone_interval"
        const val KEY_PULSE_COLOR_APP     = "pulse_color_app"
        const val KEY_PULSE_COLOR_PHONE   = "pulse_color_phone"
        const val KEY_IS_ANCHORED         = "is_anchored"
        const val KEY_PILL_OPACITY        = "pill_opacity"
        const val KEY_TIMER_ORDER_SWAPPED = "timer_order_swapped"
        const val KEY_PILL_VERTICAL       = "pill_vertical"
        const val KEY_TRACKING_ENABLED    = "tracking_enabled"
        const val KEY_GAP_THRESHOLD       = "gap_threshold_min"
        const val KEY_PILL_SIZE           = "pill_size_pct"

        /** Palette offered in SettingsActivity for pulse color selection. */
        val PALETTE = listOf(
            "#FF639922" to "Green",
            "#FF378ADD" to "Blue",
            "#FF00BCD4" to "Cyan",
            "#FFFF6B35" to "Orange",
            "#FF9B59B6" to "Purple",
            "#FFE74C3C" to "Red",
            "#FFE91E63" to "Pink",
            "#FFF5F5F5" to "White"
        )

        val MILESTONE_OPTIONS      = listOf(5, 10, 15, 20, 30, 45, 60)
        val OPACITY_OPTIONS        = listOf(15 to "Ghost", 28 to "Light", 45 to "Medium", 65 to "Solid")
        val GAP_THRESHOLD_OPTIONS  = listOf(1, 2, 5, 10, 15, 20, 30)
        val SIZE_OPTIONS           = listOf(75 to "Small", 100 to "Medium", 130 to "Large")
    }
}
