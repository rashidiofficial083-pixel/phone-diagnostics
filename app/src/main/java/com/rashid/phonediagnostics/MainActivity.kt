package com.rashid.phonediagnostics

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var storageGauge: CircularGaugeView
    private lateinit var storageText: TextView
    private lateinit var ramGauge: CircularGaugeView
    private lateinit var ramText: TextView
    private lateinit var batteryText: TextView
    private lateinit var batteryStatusText: TextView
    private lateinit var batteryBar: ProgressBar
    private lateinit var usedTodayText: TextView
    private lateinit var onChargeText: TextView
    private lateinit var liveMonitorSwitch: Switch
    private lateinit var appUsageContainer: LinearLayout
    private lateinit var cacheScannerRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageGauge = findViewById(R.id.storageGauge)
        storageText = findViewById(R.id.storageText)
        ramGauge = findViewById(R.id.ramGauge)
        ramText = findViewById(R.id.ramText)
        batteryText = findViewById(R.id.batteryText)
        batteryStatusText = findViewById(R.id.batteryStatusText)
        batteryBar = findViewById(R.id.batteryBar)
        usedTodayText = findViewById(R.id.usedTodayText)
        onChargeText = findViewById(R.id.onChargeText)
        liveMonitorSwitch = findViewById(R.id.liveMonitorSwitch)
        appUsageContainer = findViewById(R.id.appUsageContainer)
        cacheScannerRow = findViewById(R.id.cacheScannerRow)

        loadStorageInfo()
        loadRamInfo()
        loadBatteryInfo()
        loadScreenTimeByApp()

        liveMonitorSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) MonitorService.start(this) else MonitorService.stop(this)
        }

        cacheScannerRow.setOnClickListener {
            // TODO: Cache Scanner screen পরে যোগ করব
        }
    }

    override fun onResume() {
        super.onResume()
        loadStorageInfo()
        loadRamInfo()
        loadBatteryInfo()
        loadScreenTimeByApp()
    }

    private fun loadStorageInfo() {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
        val percent = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()

        storageText.text = String.format("%.1f / %.0f GB", usedGb, totalGb)
        storageGauge.setData(percent, "$percent%", "USED", Color.parseColor("#4A9DFF"))
    }

    private fun loadRamInfo() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedGb = totalGb - availGb
        val percent = ((usedGb / totalGb) * 100).toInt()

        ramText.text = String.format("%.1f / %.1f GB", usedGb, totalGb)
        ramGauge.setData(percent, "$percent%", "ACTIVE", Color.parseColor("#FFA836"))
    }

    private fun loadBatteryInfo() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        batteryText.text = "$level%"
        batteryBar.progress = level

        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        batteryStatusText.text = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Normal"
        }

        usedTodayText.text = "--%"
        onChargeText.text = "--"
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // মিনিটকে "2h 53m" অথবা ৬০ মিনিটের কম হলে "45m" আকারে দেখানোর জন্য
    private fun formatDuration(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    // একই app-এর একাধিক package/entry থাকলে একসাথে যোগ করার জন্য একটা ছোট holder class
    private data class AppUsageEntry(
        val label: String,
        val packageName: String,
        var totalTimeMs: Long,
        var icon: Drawable?
    )

    private fun loadScreenTimeByApp() {
        appUsageContainer.removeAllViews()

        if (!hasUsageAccess()) {
            val prompt = TextView(this).apply {
                text = "Screen time দেখতে permission দিতে হবে — এখানে ট্যাপ করুন"
                setTextColor(Color.parseColor("#5B8DEF"))
                setPadding(0, 8, 0, 8)
                setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
            appUsageContainer.addView(prompt)
            return
        }

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.HOURS.toMillis(24)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)

        if (stats.isNullOrEmpty()) {
            appUsageContainer.addView(TextView(this).apply {
                text = "কোনো ডেটা পাওয়া যায়নি"
                setTextColor(Color.parseColor("#9AA0A6"))
            })
            return
        }

        val pm = packageManager

        // প্রথমে app label অনুযায়ী গ্রুপ করে সময় যোগ করা হচ্ছে,
        // যাতে একই app আলাদা package/process এর কারণে দুইবার তালিকায় না আসে
        val grouped = LinkedHashMap<String, AppUsageEntry>()

        for (usage in stats) {
            if (usage.totalTimeInForeground <= 0) continue

            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(usage.packageName, 0)).toString()
            } catch (e: Exception) {
                usage.packageName
            }

            val existing = grouped[appName]
            if (existing != null) {
                existing.totalTimeMs += usage.totalTimeInForeground
            } else {
                val icon = try {
                    pm.getApplicationIcon(usage.packageName)
                } catch (e: Exception) {
                    null
                }
                grouped[appName] = AppUsageEntry(
                    label = appName,
                    packageName = usage.packageName,
                    totalTimeMs = usage.totalTimeInForeground,
                    icon = icon
                )
            }
        }

        val top = grouped.values.sortedByDescending { it.totalTimeMs }.take(5)
        val maxTime = top.firstOrNull()?.totalTimeMs ?: 1L

        for (entry in top) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(entry.totalTimeMs)
            val percent = ((entry.totalTimeMs.toDouble() / maxTime.toDouble()) * 100).toInt()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val nameRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconSizeDp = (28 * resources.displayMetrics.density).toInt()
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSizeDp, iconSizeDp).apply {
                    marginEnd = (10 * resources.displayMetrics.density).toInt()
                }
                if (entry.icon != null) {
                    setImageDrawable(entry.icon)
                } else {
                    setImageDrawable(pm.defaultActivityIcon)
                }
            }
            nameRow.addView(iconView)

            nameRow.addView(TextView(this).apply {
                text = entry.label
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            nameRow.addView(TextView(this).apply {
                text = formatDuration(minutes)
                setTextColor(Color.parseColor("#9AA0A6"))
            })

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12).apply {
                    topMargin = 6
                }
            }

            row.addView(nameRow)
            row.addView(bar)
            appUsageContainer.addView(row)
        }
    }
}
