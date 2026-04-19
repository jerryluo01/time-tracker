package com.timetracker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts TimerService after device reboot.
 * Declared in AndroidManifest with RECEIVE_BOOT_COMPLETED intent-filter.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TimerService.start(context)
        }
    }
}
