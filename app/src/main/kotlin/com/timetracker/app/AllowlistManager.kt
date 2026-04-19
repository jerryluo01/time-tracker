package com.timetracker.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the set of app packages the user has chosen to track.
 *
 * When the allowlist is empty, every app is tracked (backwards-compatible default).
 * When non-empty, only the listed packages drive the app timer; the phone timer is
 * always running regardless.
 */
class AllowlistManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("allowlist", Context.MODE_PRIVATE)

    fun getTrackedPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()

    fun setTrackedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    /**
     * Returns true if [pkg] should drive the app timer.
     * Always true when the allowlist is empty (track-everything mode).
     */
    fun isTracked(pkg: String): Boolean {
        val tracked = getTrackedPackages()
        return tracked.isEmpty() || tracked.contains(pkg)
    }

    fun isEmpty(): Boolean = getTrackedPackages().isEmpty()

    companion object {
        private const val KEY_PACKAGES = "tracked_packages"
    }
}
