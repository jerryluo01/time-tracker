package com.timetracker.app

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal launch screen that walks the user through granting the three required
 * permissions before starting TimerService.
 *
 * Permissions required:
 *  1. SYSTEM_ALERT_WINDOW — draw the floating overlay
 *  2. PACKAGE_USAGE_STATS — detect the foreground app
 *  3. REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — prevent the service from being killed (fix #6)
 *
 * onResume() re-checks all three states so buttons update after the user returns
 * from the Settings screens.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnUsage: Button
    private lateinit var btnBattery: Button
    private lateinit var btnStart: Button
    private lateinit var statusOverlay: ImageView
    private lateinit var statusUsage: ImageView
    private lateinit var statusBattery: ImageView
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        val hasOverlay  = Settings.canDrawOverlays(this)
        val hasUsage    = hasUsageStatsPermission()
        val hasBattery  = isIgnoringBatteryOptimizations()
        val allGranted  = hasOverlay && hasUsage && hasBattery

        setRowState(btnOverlay,  statusOverlay,  hasOverlay)
        setRowState(btnUsage,    statusUsage,    hasUsage)
        setRowState(btnBattery,  statusBattery,  hasBattery)

        btnStart.visibility = if (allGranted) View.VISIBLE else View.GONE

        if (allGranted) {
            if (TimerService.running) {
                btnStart.text = "Stop Tracking"
                btnStart.setBackgroundColor(android.graphics.Color.parseColor("#C62828"))
                btnStart.setOnClickListener {
                    settings.trackingEnabled = false
                    TimerService.running = false
                    TimerService.stop(this)
                    refreshPermissionState()
                }
            } else {
                btnStart.text = "Start Tracking"
                btnStart.setBackgroundColor(android.graphics.Color.parseColor("#378ADD"))
                btnStart.setOnClickListener {
                    settings.trackingEnabled = true
                    TimerService.start(this)
                    finish()
                }
                if (settings.trackingEnabled) {
                    TimerService.start(this)
                }
            }
        }
    }

    private fun setRowState(btn: Button, icon: ImageView, granted: Boolean) {
        btn.isEnabled = !granted
        btn.alpha = if (granted) 0.4f else 1f
        icon.setImageResource(
            if (granted) android.R.drawable.checkbox_on_background
            else android.R.drawable.checkbox_off_background
        )
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Permission request actions
    // ──────────────────────────────────────────────────────────────────────────

    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun requestUsagePermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    @Suppress("BatteryLife")
    private fun requestBatteryExemption() {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Programmatic layout — keeps this file self-contained, no XML needed
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildLayout(): View {
        val density = resources.displayMetrics.density

        fun px(dp: Float) = (dp * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), px(48f), px(24f), px(24f))
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        val title = TextView(this).apply {
            text = "Time Tracker"
            textSize = 26f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, px(8f))
        }

        val subtitle = TextView(this).apply {
            text = "Grant the three permissions below to start tracking."
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, px(32f))
        }

        root.addView(title)
        root.addView(subtitle)

        // Row builders
        fun permRow(
            label: String,
            detail: String,
            btnRef: (Button) -> Unit,
            iconRef: (ImageView) -> Unit,
            onClick: () -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(8f), 0, px(8f))
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(px(24f), px(24f)).apply {
                    rightMargin = px(12f)
                    topMargin = px(4f)
                }
                setImageResource(android.R.drawable.checkbox_off_background)
            }
            iconRef(icon)

            val textBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textBlock.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(android.graphics.Color.WHITE)
            })
            textBlock.addView(TextView(this).apply {
                text = detail
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#888888"))
                setPadding(0, px(2f), 0, px(6f))
            })
            val btn = Button(this).apply {
                text = "Grant"
                textSize = 13f
                setOnClickListener { onClick() }
            }
            btnRef(btn)
            textBlock.addView(btn)

            row.addView(icon)
            row.addView(textBlock)
            root.addView(row)
        }

        permRow(
            label  = "Display over other apps",
            detail = "Required to show the floating timer pill",
            btnRef = { btnOverlay = it },
            iconRef = { statusOverlay = it },
            onClick = ::requestOverlayPermission
        )

        permRow(
            label  = "Usage access",
            detail = "Required to detect which app is in the foreground",
            btnRef = { btnUsage = it },
            iconRef = { statusUsage = it },
            onClick = ::requestUsagePermission
        )

        permRow(
            label  = "Ignore battery optimization",
            detail = "Prevents Android from killing the timer service",
            btnRef = { btnBattery = it },
            iconRef = { statusBattery = it },
            onClick = ::requestBatteryExemption
        )

        btnStart = Button(this).apply {
            text = "Start Tracking"
            textSize = 16f
            visibility = View.GONE
            setBackgroundColor(android.graphics.Color.parseColor("#378ADD"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(32f) }
        }
        root.addView(btnStart)

        // Secondary action buttons — only shown once all permissions are granted
        val secondaryButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = px(8f) }
        }

        fun secondaryBtn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            textSize = 13f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

        secondaryButtons.addView(secondaryBtn("Choose apps") {
            startActivity(Intent(this@MainActivity, AppPickerActivity::class.java))
        })
        secondaryButtons.addView(secondaryBtn("Settings") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        })

        root.addView(secondaryButtons)

        // Sync secondary button row visibility with btnStart
        btnStart.viewTreeObserver.addOnGlobalLayoutListener {
            secondaryButtons.visibility = btnStart.visibility
        }

        return root
    }
}
