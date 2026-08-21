package com.rashid.phonediagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var storageText: TextView
    private lateinit var storageBar: ProgressBar
    private lateinit var ramText: TextView
    private lateinit var ramBar: ProgressBar
    private lateinit var batteryText: TextView
    private lateinit var batteryStatusText: TextView
    private lateinit var usedTodayText: TextView
    private lateinit var onChargeText: TextView
    private lateinit var liveMonitorSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storageText = findViewById(R.id.storageText)
        storageBar = findViewById(R.id.storageBar)
        ramText = findViewById(R.id.ramText)
        ramBar = findViewById(R.id.ramBar)
        batteryText = findViewById(R.id.batteryText)
        batteryStatusText = findViewById(R.id.batteryStatusText)
        usedTodayText = findViewById(R.id.usedTodayText)
        onChargeText = findViewById(R.id.onChargeText)
        liveMonitorSwitch = findViewById(R.id.liveMonitorSwitch)

        loadStorageInfo()
        loadRamInfo()
        loadBatteryInfo()

        liveMonitorSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                MonitorService.start(this)
            } else {
                MonitorService.stop(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadStorageInfo()
        loadRamInfo()
        loadBatteryInfo()
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
        storageBar.progress = percent
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
        ramBar.progress = percent
    }

    private fun loadBatteryInfo() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        batteryText.text = "$level%"

        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Fully Charged"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Normal"
        }
        batteryStatusText.text = statusStr

        usedTodayText.text = "--%"
        onChargeText.text = "--"
    }
}
