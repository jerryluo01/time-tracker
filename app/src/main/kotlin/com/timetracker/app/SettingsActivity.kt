package com.timetracker.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen — all user-adjustable preferences in one place.
 *
 * Sections:
 *   1. Nudge interval  — how many minutes between milestone pulses
 *   2. App timer color — glow color for the app-session milestone
 *   3. Phone timer color — glow color for the phone-session milestone
 *   4. Overlay opacity — how transparent the pill is (Ghost → Solid)
 *   5. Lock overlay   — toggle FLAG_NOT_TOUCHABLE (click-through + no drag)
 *
 * All changes save immediately to SettingsManager (SharedPreferences).
 * Anchor changes take effect in TimerService instantly via OnSharedPreferenceChangeListener.
 * Opacity changes take effect on the next service restart.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsManager(this)
        setContentView(buildLayout())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Layout
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildLayout(): View {
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(48f), px(20f), px(32f))
        }

        // ── Title ──────────────────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, px(28f))
        })

        // ── Nudge interval ─────────────────────────────────────────────────
        root.addView(sectionLabel("Nudge every … minutes"))
        root.addView(chipRow(
            options = SettingsManager.MILESTONE_OPTIONS.map { it.toString() },
            selected = settings.milestoneIntervalMin.toString(),
            onSelect = { settings.milestoneIntervalMin = it.toInt() }
        ))

        root.addView(spacer(px(24f)))

        // ── App timer pulse color ───────────────────────────────────────────
        root.addView(sectionLabel("App timer pulse color"))
        root.addView(hint("Default: green  ●  fires when your current-app timer hits the interval"))
        root.addView(colorRow(
            current = settings.pulseColorApp,
            onSelect = { settings.pulseColorApp = it }
        ))

        root.addView(spacer(px(24f)))

        // ── Phone timer pulse color ─────────────────────────────────────────
        root.addView(sectionLabel("Phone timer pulse color"))
        root.addView(hint("Default: blue  ●  fires when total phone-on time hits the interval"))
        root.addView(colorRow(
            current = settings.pulseColorPhone,
            onSelect = { settings.pulseColorPhone = it }
        ))

        root.addView(spacer(px(24f)))

        // ── Overlay opacity ─────────────────────────────────────────────────
        root.addView(sectionLabel("Overlay opacity"))
        root.addView(chipRow(
            options = SettingsManager.OPACITY_OPTIONS.map { it.second },
            selected = SettingsManager.OPACITY_OPTIONS
                .minByOrNull { Math.abs(it.first - settings.pillOpacityPercent) }!!.second,
            onSelect = { label ->
                settings.pillOpacityPercent =
                    SettingsManager.OPACITY_OPTIONS.first { it.second == label }.first
            }
        ))

        root.addView(spacer(px(24f)))

        // ── Pill size ────────────────────────────────────────────────────────
        root.addView(sectionLabel("Pill size"))
        root.addView(chipRow(
            options = SettingsManager.SIZE_OPTIONS.map { it.second },
            selected = SettingsManager.SIZE_OPTIONS
                .minByOrNull { Math.abs(it.first - settings.pillSizePct) }!!.second,
            onSelect = { label ->
                settings.pillSizePct =
                    SettingsManager.SIZE_OPTIONS.first { it.second == label }.first
            }
        ))

        root.addView(spacer(px(24f)))

        // ── Reset threshold ──────────────────────────────────────────────────
        root.addView(sectionLabel("Reset timers after … minutes away"))
        root.addView(hint("If the screen is off longer than this, both timers reset to zero"))
        root.addView(chipRow(
            options = SettingsManager.GAP_THRESHOLD_OPTIONS.map { it.toString() },
            selected = settings.gapThresholdMin.toString(),
            onSelect = { settings.gapThresholdMin = it.toInt() }
        ))

        root.addView(spacer(px(24f)))

        // ── Lock overlay ────────────────────────────────────────────────────
        root.addView(sectionLabel("Lock overlay position"))
        root.addView(hint("When locked: pill is click-through and cannot be dragged accidentally"))
        root.addView(buildAnchorToggle())

        root.addView(spacer(px(24f)))

        // ── Pill layout ─────────────────────────────────────────────────────
        root.addView(sectionLabel("Pill layout"))
        root.addView(hint("Changes apply immediately"))
        root.addView(buildToggleRow(
            label   = "Vertical pill  (stack timers top/bottom)",
            checked = settings.isPillVertical,
            onToggle = { settings.isPillVertical = it }
        ))
        root.addView(buildToggleRow(
            label   = "Swap timer order  (phone first, then app)",
            checked = settings.isTimerOrderSwapped,
            onToggle = { settings.isTimerOrderSwapped = it }
        ))

        root.addView(spacer(px(32f)))

        // ── Back / restart hint ─────────────────────────────────────────────
        root.addView(TextView(this).apply {
            text = "← Back"
            textSize = 14f
            setTextColor(Color.parseColor("#378ADD"))
            setOnClickListener { finish() }
        })

        scroll.addView(root)
        return scroll
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Widget builders
    // ──────────────────────────────────────────────────────────────────────────

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        val d = resources.displayMetrics.density
        setPadding(0, 0, 0, (6 * d).toInt())
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#777777"))
        val d = resources.displayMetrics.density
        setPadding(0, 0, 0, (8 * d).toInt())
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
    }

    /** Horizontally scrollable row of pill-shaped chips. */
    private fun chipRow(
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ): View {
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()

        val hsv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        val chips = mutableListOf<TextView>()

        fun styleChip(chip: TextView, active: Boolean) {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                if (active) setColor(Color.parseColor("#378ADD"))
                else { setColor(Color.TRANSPARENT); setStroke(1, Color.parseColor("#444444")) }
            }
            chip.background = bg
            chip.setTextColor(if (active) Color.WHITE else Color.parseColor("#AAAAAA"))
        }

        options.forEach { label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(px(14f), px(7f), px(14f), px(7f))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = px(8f) }
                setOnClickListener {
                    chips.forEach { styleChip(it, it.text == label) }
                    onSelect(label)
                }
            }
            styleChip(chip, label == selected)
            chips.add(chip)
            row.addView(chip)
        }

        hsv.addView(row)
        return hsv
    }

    /** Horizontal row of colored circles for pulse color selection. */
    private fun colorRow(current: Int, onSelect: (Int) -> Unit): View {
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()
        val size = px(36f)
        val padding = px(4f)

        val hsv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(4f), 0, px(4f))
        }

        val swatches = mutableListOf<View>()

        fun styleRing(view: View, active: Boolean) {
            val outer = view as LinearLayout
            val ring = outer.background as GradientDrawable
            ring.setStroke(if (active) px(2.5f) else 0, Color.WHITE)
        }

        SettingsManager.PALETTE.forEach { (hex, _) ->
            val color = Color.parseColor(hex)
            val isSelected = color == current

            val container = LinearLayout(this).apply {
                val ring = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(if (isSelected) px(2.5f) else 0, Color.WHITE)
                }
                background = ring
                setPadding(padding, padding, padding, padding)
                layoutParams = LinearLayout.LayoutParams(
                    size + padding * 2, size + padding * 2
                ).apply { rightMargin = px(10f) }
            }

            val dot = View(this).apply {
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(size, size)
            }

            container.addView(dot)
            container.setOnClickListener {
                swatches.forEach { styleRing(it, false) }
                styleRing(container, true)
                onSelect(color)
            }
            swatches.add(container)
            row.addView(container)
        }

        hsv.addView(row)
        return hsv
    }

    @Suppress("DEPRECATION")
    private fun buildToggleRow(
        label: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, px(8f), 0, px(8f))
        }

        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        @Suppress("DEPRECATION")
        val toggle = android.widget.Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _: android.widget.CompoundButton, isChecked: Boolean ->
                onToggle(isChecked)
            }
        }

        row.addView(tv)
        row.addView(toggle)
        return row
    }

    @Suppress("DEPRECATION")
    private fun buildAnchorToggle(): View {
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(8f), 0, px(8f))
        }

        val label = TextView(this).apply {
            text = if (settings.isAnchored) "Locked — tap to unlock" else "Unlocked — tap to lock"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val toggle = Switch(this).apply {
            isChecked = settings.isAnchored
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                settings.isAnchored = checked
                label.text = if (checked) "Locked — tap to unlock" else "Unlocked — tap to lock"
            }
        }

        row.addView(label)
        row.addView(toggle)
        return row
    }
}
