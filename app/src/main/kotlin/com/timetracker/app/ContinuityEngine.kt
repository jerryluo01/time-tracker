package com.timetracker.app

import android.content.Context
import android.content.SharedPreferences

class ContinuityEngine(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("continuity_engine", Context.MODE_PRIVATE)
    private val settings = SettingsManager(context)

    private val gapThresholdMs get() = settings.gapThresholdMin * 60_000L

    companion object {

        private const val KEY_PHONE_ACCUMULATED = "phone_accumulated_ms"
        private const val KEY_PHONE_RESUME_TIME  = "phone_resume_time_ms"
        private const val KEY_PHONE_ACTIVE       = "phone_is_active"

        private const val KEY_APP_CURRENT_PACKAGE = "app_current_package"
        private const val KEY_APP_RESUME_TIME    = "app_resume_time_ms"
        private const val KEY_APP_ACTIVE         = "app_is_active"

        private const val KEY_LAST_ACTIVE_TIME      = "last_active_timestamp_ms"
        // Separate gap clock for the app timer — can diverge from phone timer
        // when the user is in an untracked app (app timer paused, phone timer running).
        private const val KEY_LAST_APP_ACTIVE_TIME  = "last_app_active_timestamp_ms"
    }

    /**
     * Called on screen-on or service start.
     * Always resumes the phone timer. Only resumes the app timer when [isAppTracked] is true
     * (i.e. the current foreground app is in the user's allowlist, or the allowlist is empty).
     */
    fun onResume(currentPackage: String, isAppTracked: Boolean = true, now: Long = System.currentTimeMillis()) {
        val lastActive = prefs.getLong(KEY_LAST_ACTIVE_TIME, 0L)
        val gap = if (lastActive > 0) now - lastActive else 0L

        if (gap >= gapThresholdMs) {
            resetPhoneSession(now)
            if (isAppTracked) resetAppSession(currentPackage, now)
        } else {
            resumePhoneSession(now)
            if (isAppTracked) resumeAppSession(currentPackage, now)
        }

        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, now).apply()
        if (isAppTracked) prefs.edit().putLong(KEY_LAST_APP_ACTIVE_TIME, now).apply()
    }

    fun onPause(now: Long = System.currentTimeMillis()) {
        snapshotPhoneSession(now)
        snapshotAppSession(now)
        // Only advance the app-timer gap clock if the app timer is active.
        // If already paused (user is in an untracked app), preserving the earlier
        // timestamp ensures resumeAppTimer() sees the true gap since the tracked app.
        val edit = prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, now)
        if (prefs.getBoolean(KEY_APP_ACTIVE, false)) {
            edit.putLong(KEY_LAST_APP_ACTIVE_TIME, now)
        }
        edit.apply()
    }

    /**
     * Pause only the app timer — called when switching to an untracked app.
     * The phone timer keeps running unaffected.
     */
    fun pauseAppTimer(now: Long = System.currentTimeMillis()) {
        snapshotAppSession(now)
        prefs.edit().putLong(KEY_LAST_APP_ACTIVE_TIME, now).apply()
    }

    /**
     * Resume the app timer when switching back to a tracked app.
     * Uses its own gap clock so a long detour through untracked apps only resets
     * the app timer — not the phone timer.
     */
    fun resumeAppTimer(pkg: String, now: Long = System.currentTimeMillis()) {
        val lastAppActive = prefs.getLong(KEY_LAST_APP_ACTIVE_TIME, 0L)
        val gap = if (lastAppActive > 0) now - lastAppActive else 0L
        if (gap >= gapThresholdMs) {
            resetAppSession(pkg, now)
        } else {
            resumeAppSession(pkg, now)
        }
        prefs.edit().putLong(KEY_LAST_APP_ACTIVE_TIME, now).apply()
    }

    fun getCurrentTimes(now: Long = System.currentTimeMillis()): SessionTimes {
        return SessionTimes(livePhoneMs(now), liveAppMs(now))
    }

    fun checkMilestones(now: Long = System.currentTimeMillis()): MilestoneEvent {
        val times = getCurrentTimes(now)
        val phoneMin = times.phoneMs / 60_000L
        val appMin   = times.appMs  / 60_000L
        val phoneHit = phoneMin > 0 && phoneMin % 15 == 0L && times.phoneMs % 60_000L < 1_500L
        val appHit   = appMin > 0 && appMin % 15 == 0L && times.appMs % 60_000L < 1_500L
        return MilestoneEvent(phoneMilestone = phoneHit, appMilestone = appHit)
    }

    private fun resumePhoneSession(now: Long) {
        prefs.edit().putLong(KEY_PHONE_RESUME_TIME, now).putBoolean(KEY_PHONE_ACTIVE, true).apply()
    }
    private fun resetPhoneSession(now: Long) {
        prefs.edit().putLong(KEY_PHONE_ACCUMULATED, 0L).putLong(KEY_PHONE_RESUME_TIME, now).putBoolean(KEY_PHONE_ACTIVE, true).apply()
    }
    private fun snapshotPhoneSession(now: Long) {
        prefs.edit().putLong(KEY_PHONE_ACCUMULATED, livePhoneMs(now)).putBoolean(KEY_PHONE_ACTIVE, false).apply()
    }
    private fun livePhoneMs(now: Long): Long {
        val acc = prefs.getLong(KEY_PHONE_ACCUMULATED, 0L)
        val active = prefs.getBoolean(KEY_PHONE_ACTIVE, false)
        val resume = prefs.getLong(KEY_PHONE_RESUME_TIME, now)
        return if (active) acc + (now - resume) else acc
    }

    private fun appAccKey(pkg: String) = "app_acc_$pkg"

    private fun resumeAppSession(pkg: String, now: Long) {
        snapshotAppSession(now)
        prefs.edit()
            .putString(KEY_APP_CURRENT_PACKAGE, pkg)
            .putLong(KEY_APP_RESUME_TIME, now)
            .putBoolean(KEY_APP_ACTIVE, true)
            .apply()
    }
    private fun resetAppSession(pkg: String, now: Long) {
        snapshotAppSession(now)
        prefs.edit()
            .putString(KEY_APP_CURRENT_PACKAGE, pkg)
            .putLong(appAccKey(pkg), 0L)
            .putLong(KEY_APP_RESUME_TIME, now)
            .putBoolean(KEY_APP_ACTIVE, true)
            .apply()
    }
    private fun snapshotAppSession(now: Long) {
        val pkg = prefs.getString(KEY_APP_CURRENT_PACKAGE, null) ?: return
        prefs.edit()
            .putLong(appAccKey(pkg), liveAppMs(now))
            .putBoolean(KEY_APP_ACTIVE, false)
            .apply()
    }
    private fun liveAppMs(now: Long): Long {
        val pkg = prefs.getString(KEY_APP_CURRENT_PACKAGE, null) ?: return 0L
        val acc = prefs.getLong(appAccKey(pkg), 0L)
        val active = prefs.getBoolean(KEY_APP_ACTIVE, false)
        val resume = prefs.getLong(KEY_APP_RESUME_TIME, now)
        return if (active) acc + (now - resume) else acc
    }
}

data class SessionTimes(val phoneMs: Long, val appMs: Long) {
    val phoneFormatted: String get() = formatMs(phoneMs)
    val appFormatted: String   get() = formatMs(appMs)
    private fun formatMs(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}

data class MilestoneEvent(val phoneMilestone: Boolean, val appMilestone: Boolean) {
    val any: Boolean get() = phoneMilestone || appMilestone
}
