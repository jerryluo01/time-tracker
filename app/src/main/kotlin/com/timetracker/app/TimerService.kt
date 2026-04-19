package com.timetracker.app

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    companion object {
        private const val CHANNEL_ID       = "timer_service"
        private const val NOTIFICATION_ID  = 1
        private const val POLL_INTERVAL_MS = 1500L

        /** True while the service is alive — read by MainActivity to show Stop vs Start. */
        var running = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TimerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }

    private lateinit var engine: ContinuityEngine
    private lateinit var allowlist: AllowlistManager
    private lateinit var settings: SettingsManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var pulseApp: PulseOverlay
    private lateinit var pulsePhone: PulseOverlay
    private lateinit var usageStats: UsageStatsManager
    private lateinit var notificationManager: NotificationManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isScreenOn = true
    private var isRunning  = false   // guards onStartCommand against double-init
    private var currentPackage = "unknown"
    private var isCurrentAppTracked = true
    private var isOnHomeScreen = false

    // Milestone fire-once guards — re-arm when the minute exits the boundary
    private var lastPhoneMinFired = -1L
    private var lastAppMinFired   = -1L

    // Cached set of home-launcher package names (rarely changes after first query)
    private val homeLauncherPackages: Set<String> by lazy {
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }, 0
        ).map { it.activityInfo.packageName }.toSet()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Settings listener — anchor/opacity changes apply instantly without restart
    // ──────────────────────────────────────────────────────────────────────────
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SettingsManager.KEY_IS_ANCHORED         -> overlayManager.setAnchored(settings.isAnchored)
            SettingsManager.KEY_PILL_VERTICAL,
            SettingsManager.KEY_TIMER_ORDER_SWAPPED -> overlayManager.rebuildOverlay()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Screen on/off receiver — must be dynamically registered
    // ──────────────────────────────────────────────────────────────────────────
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    engine.onPause()
                    stopPolling()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    currentPackage      = getForegroundPackage() ?: currentPackage
                    isOnHomeScreen      = currentPackage in homeLauncherPackages
                    isCurrentAppTracked = !isOnHomeScreen && allowlist.isTracked(currentPackage)
                    engine.onResume(currentPackage, isCurrentAppTracked)
                    startPolling()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine         = ContinuityEngine(this)
        allowlist      = AllowlistManager(this)
        settings       = SettingsManager(this)
        overlayManager = OverlayManager(this)

        // Color providers are lambdas — PulseOverlay reads the current color at trigger time,
        // so changing colors in SettingsActivity takes effect on the very next milestone.
        pulseApp   = PulseOverlay(this, colorProvider = { settings.pulseColorApp })
        pulsePhone = PulseOverlay(this, colorProvider = { settings.pulseColorPhone })

        usageStats          = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })

        // React instantly when the user toggles anchor in SettingsActivity
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("App: --  |  Phone: --"))

        // Guard: if already running (e.g. MainActivity called start() again on re-open),
        // skip re-initialisation so the engine's resume-time is not overwritten.
        if (!isRunning) {
            isRunning           = true
            running             = true
            currentPackage      = getForegroundPackage() ?: "unknown"
            isOnHomeScreen      = currentPackage in homeLauncherPackages
            isCurrentAppTracked = !isOnHomeScreen && allowlist.isTracked(currentPackage)
            engine.onResume(currentPackage, isCurrentAppTracked)
            overlayManager.addOverlay()
            startPolling()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        running   = false
        stopPolling()
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        unregisterReceiver(screenReceiver)
        overlayManager.removeOverlay()
        engine.onPause()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ──────────────────────────────────────────────────────────────────────────
    // Polling loop
    // ──────────────────────────────────────────────────────────────────────────

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn) {
                poll()
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private fun startPolling() {
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.post(pollRunnable)
    }

    private fun stopPolling() {
        mainHandler.removeCallbacks(pollRunnable)
    }

    private fun poll() {
        val now = System.currentTimeMillis()

        // Detect foreground app — only touch the app timer, never the phone timer
        val newPkg = getForegroundPackage()
        if (newPkg != null && newPkg != currentPackage) {
            val wasTracked      = isCurrentAppTracked
            currentPackage      = newPkg
            isOnHomeScreen      = currentPackage in homeLauncherPackages
            // Home screen is always treated as untracked regardless of allowlist
            isCurrentAppTracked = !isOnHomeScreen && allowlist.isTracked(currentPackage)
            lastAppMinFired     = -1L

            when {
                isCurrentAppTracked -> engine.resumeAppTimer(currentPackage, now)
                wasTracked          -> engine.pauseAppTimer(now)
            }
        }

        val times    = engine.getCurrentTimes(now)
        val phoneMin = times.phoneMs / 60_000L
        val appMin   = times.appMs   / 60_000L

        // Read interval from settings each poll — changes take effect immediately
        val interval = settings.milestoneIntervalMin.toLong()

        if (phoneMin > 0 && phoneMin % interval == 0L && phoneMin != lastPhoneMinFired) {
            lastPhoneMinFired = phoneMin
            pulsePhone.trigger()
            overlayManager.pulse()
        }
        if (phoneMin % interval != 0L) lastPhoneMinFired = -1L

        if (appMin > 0 && appMin % interval == 0L && appMin != lastAppMinFired) {
            lastAppMinFired = appMin
            pulseApp.trigger()
            overlayManager.pulse()
        }
        if (appMin % interval != 0L) lastAppMinFired = -1L

        // Show app timer only when on a tracked app (not home screen, not untracked)
        val showApp = !isOnHomeScreen && (isCurrentAppTracked || allowlist.isEmpty())
        overlayManager.update(times, showAppTimer = showApp)

        val appDisplay = if (showApp) times.appFormatted else "–"
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification("App: $appDisplay  |  Phone: ${times.phoneFormatted}")
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Foreground app detection via UsageEvents
    // ──────────────────────────────────────────────────────────────────────────
    private fun getForegroundPackage(): String? {
        if (!hasUsagePermission()) return null

        val now    = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - 3000L, now)
        val event  = UsageEvents.Event()
        var lastFg: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) lastFg = event.packageName
        }

        if (lastFg == null) {
            lastFg = usageStats.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 5000L, now)
                ?.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        return lastFg
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Timer Service", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Live phone and app session times"; setShowBadge(false) }
        )
    }

    private fun buildNotification(contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Time Tracker")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
}
