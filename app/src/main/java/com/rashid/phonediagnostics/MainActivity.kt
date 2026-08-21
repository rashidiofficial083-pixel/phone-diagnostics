package com.rashid.phonediagnostics

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
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

        val top = stats.filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(5)
        val maxTime = top.firstOrNull()?.totalTimeInForeground ?: 1L
        val pm = packageManager

        for (usage in top) {
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(usage.packageName, 0)).toString()
            } catch (e: Exception) { usage.packageName }

            val minutes = TimeUnit.MILLISECONDS.toMinutes(usage.totalTimeInForeground)
            val percent = ((usage.totalTimeInForeground.toDouble() / maxTime.toDouble()) * 100).toInt()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val nameRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            nameRow.addView(TextView(this).apply {
                text = appName
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            nameRow.addView(TextView(this).apply {
                text = "${minutes}m"
                setTextColor(Color.parseColor("#9AA0A6"))
            })
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percent
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 12).apply { topMargin = 6 }
            }
            row.addView(nameRow)
            row.addView(bar)
            appUsageContainer.addView(row)
        }
    }
}
