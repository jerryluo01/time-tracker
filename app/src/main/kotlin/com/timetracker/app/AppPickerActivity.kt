package com.timetracker.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings screen: lets the user pick which apps the app-session timer tracks.
 *
 * Shows all user-installed apps (launcher apps) alphabetically with checkboxes.
 * Selection is saved immediately when the user leaves the screen (onStop).
 *
 * When nothing is selected, every app is tracked (track-everything mode —
 * shown via the subtitle hint).
 */
class AppPickerActivity : AppCompatActivity() {

    private data class AppInfo(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )

    private lateinit var allowlist: AllowlistManager
    private lateinit var adapter: AppAdapter
    private val selectedPackages = mutableSetOf<String>()
    private var allApps: List<AppInfo> = emptyList()
    private var currentSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowlist = AllowlistManager(this)
        selectedPackages.addAll(allowlist.getTrackedPackages())
        allApps = loadInstalledApps()

        setContentView(buildLayout())
    }

    override fun onStop() {
        super.onStop()
        allowlist.setTrackedPackages(selectedPackages.toSet())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // App list loading
    // ──────────────────────────────────────────────────────────────────────────

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { isUserApp(it) && it.packageName != packageName }
            .map { AppInfo(pm.getApplicationLabel(it).toString(), it.packageName, pm.getApplicationIcon(it)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun isUserApp(info: ApplicationInfo): Boolean =
        (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0

    private fun applyFilter(query: String) {
        currentSearchQuery = query
        val filtered = if (query.isBlank()) allApps
                       else allApps.filter { it.label.contains(query, ignoreCase = true) }
        adapter.clear()
        adapter.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun refreshApps() {
        Thread {
            val fresh = loadInstalledApps()
            runOnUiThread {
                allApps = fresh
                applyFilter(currentSearchQuery)
                updateSubtitle()
            }
        }.start()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // List adapter
    // ──────────────────────────────────────────────────────────────────────────

    private inner class AppAdapter(apps: List<AppInfo>) :
        ArrayAdapter<AppInfo>(this, 0, apps) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val app = getItem(position)!!
            val density = context.resources.displayMetrics.density
            fun px(dp: Float) = (dp * density).toInt()

            val row = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(16f), px(10f), px(16f), px(10f))
            }
            row.removeAllViews()

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(px(36f), px(36f)).apply { rightMargin = px(14f) }
                setImageDrawable(app.icon)
            }

            val label = TextView(context).apply {
                text = app.label
                textSize = 14f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val check = CheckBox(context).apply {
                isChecked = selectedPackages.contains(app.packageName)
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#378ADD"))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedPackages.add(app.packageName)
                    else selectedPackages.remove(app.packageName)
                    updateSubtitle()
                }
            }

            row.addView(icon)
            row.addView(label)
            row.addView(check)
            return row
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Layout
    // ──────────────────────────────────────────────────────────────────────────

    private lateinit var subtitleView: TextView

    private fun updateSubtitle() {
        subtitleView.text = if (selectedPackages.isEmpty())
            "No apps selected — all apps are tracked"
        else
            "${selectedPackages.size} app${if (selectedPackages.size == 1) "" else "s"} tracked"
    }

    private fun buildLayout(): View {
        val density = resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(48f), px(20f), px(12f))
        }

        header.addView(TextView(this).apply {
            text = "← Back"
            textSize = 14f
            setTextColor(Color.parseColor("#378ADD"))
            setPadding(0, 0, 0, px(10f))
            setOnClickListener { finish() }
        })

        header.addView(TextView(this).apply {
            text = "Track these apps"
            textSize = 22f
            setTextColor(Color.WHITE)
        })

        subtitleView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, px(4f), 0, 0)
        }
        updateSubtitle()
        header.addView(subtitleView)

        // Clear / Select-all row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(20f), px(8f), px(20f), px(8f))
        }

        fun smallBtn(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#378ADD"))
            setPadding(0, px(4f), px(24f), px(4f))
            setOnClickListener { onClick() }
        }

        btnRow.addView(smallBtn("Select all") {
            adapter.count.let { count ->
                for (i in 0 until count) {
                    adapter.getItem(i)?.let { selectedPackages.add(it.packageName) }
                }
            }
            adapter.notifyDataSetChanged()
            updateSubtitle()
        })
        btnRow.addView(smallBtn("Clear all") {
            selectedPackages.clear()
            adapter.notifyDataSetChanged()
            updateSubtitle()
        })
        btnRow.addView(smallBtn("Refresh") { refreshApps() })

        header.addView(btnRow)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#333333"))
        }

        // Search bar
        val searchBar = EditText(this).apply {
            hint = "Search apps…"
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(px(16f), px(10f), px(16f), px(10f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString() ?: "") }
            })
        }

        // App list
        adapter = AppAdapter(allApps)
        val listView = ListView(this).apply {
            this.adapter = this@AppPickerActivity.adapter
            setBackgroundColor(Color.parseColor("#121212"))
            setDivider(null)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(header)
        root.addView(divider)
        root.addView(searchBar)
        root.addView(listView)
        return root
    }
}
