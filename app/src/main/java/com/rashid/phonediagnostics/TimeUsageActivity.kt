package com.rashid.phonediagnostics

import android.app.TimePickerDialog
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class TimeUsageActivity : AppCompatActivity() {

    private lateinit var startTimeButton: TextView
    private lateinit var endTimeButton: TextView
    private lateinit var checkButton: TextView
    private lateinit var resultsContainer: LinearLayout

    private var startHour = -1
    private var startMinute = -1
    private var endHour = -1
    private var endMinute = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_time_usage)

        startTimeButton = findViewById(R.id.startTimeButton)
        endTimeButton = findViewById(R.id.endTimeButton)
        checkButton = findViewById(R.id.checkButton)
        resultsContainer = findViewById(R.id.resultsContainer)

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        startTimeButton.setOnClickListener {
            pickTime { h, m ->
                startHour = h
                startMinute = m
                startTimeButton.text = "Start: %02d:%02d".format(h, m)
            }
        }

        endTimeButton.setOnClickListener {
            pickTime { h, m ->
                endHour = h
                endMinute = m
                endTimeButton.text = "End: %02d:%02d".format(h, m)
            }
        }

        checkButton.setOnClickListener {
            if (startHour == -1 || endHour == -1) {
                Toast.makeText(this, "প্রথমে Start আর End সময় বাছাই করুন", Toast.LENGTH_SHORT).show()
            } else {
                loadUsageForRange()
            }
        }
    }

    private fun pickTime(onPicked: (Int, Int) -> Unit) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute -> onPicked(hourOfDay, minute) },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            true
        ).show()
    }

    private data class AppTimeEntry(
        val label: String,
        val packageName: String,
        var totalTimeMs: Long
    )

    private fun loadUsageForRange() {
        resultsContainer.removeAllViews()

        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var startMillis = startCal.timeInMillis
        var endMillis = endCal.timeInMillis

        if (endMillis <= startMillis) {
            // End time যদি Start time এর আগে হয়, ধরে নিচ্ছি এটা পরের দিন পর্যন্ত বিস্তৃত
            endCal.add(Calendar.DAY_OF_MONTH, 1)
            endMillis = endCal.timeInMillis
        }

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startMillis, endMillis)

        val openSessions = HashMap<String, Long>()
        val totals = LinkedHashMap<String, AppTimeEntry>()
        val pm = packageManager

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    openSessions[pkg] = event.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val openedAt = openSessions.remove(pkg)
                    if (openedAt != null) {
                        addDuration(totals, pm, pkg, event.timeStamp - openedAt)
                    }
                }
            }
        }

        // যেসব অ্যাপ সময়সীমার শেষেও চালু ছিল (background event পাওয়া যায়নি),
        // সেগুলোর জন্য endMillis পর্যন্ত হিসাব করা হচ্ছে
        for ((pkg, openedAt) in openSessions) {
            addDuration(totals, pm, pkg, endMillis - openedAt)
        }

        if (totals.isEmpty()) {
            resultsContainer.addView(makeInfoText("এই সময়ে ফোন ব্যবহারের কোনো তথ্য পাওয়া যায়নি"))
            return
        }

        val sorted = totals.values.sortedByDescending { it.totalTimeMs }

        for (entry in sorted) {
            val minutes = entry.totalTimeMs / 60000
            if (minutes <= 0) continue

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#161B22"))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
                try {
                    setImageDrawable(pm.getApplicationIcon(entry.packageName))
                } catch (e: Exception) {
                    setImageDrawable(pm.defaultActivityIcon)
                }
            }

            val name = TextView(this).apply {
                text = entry.label
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val time = TextView(this).apply {
                text = formatDuration(minutes)
                setTextColor(Color.parseColor("#9AA0A6"))
                textSize = 13f
            }

            row.addView(icon)
            row.addView(name)
            row.addView(time)
            resultsContainer.addView(row)
        }
    }

    private fun addDuration(
        totals: LinkedHashMap<String, AppTimeEntry>,
        pm: android.content.pm.PackageManager,
        pkg: String,
        durationMs: Long
    ) {
        if (durationMs <= 0) return
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        val existing = totals[label]
        if (existing != null) {
            existing.totalTimeMs += durationMs
        } else {
            totals[label] = AppTimeEntry(label, pkg, durationMs)
        }
    }

    private fun formatDuration(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun makeInfoText(msg: String): TextView {
        return TextView(this).apply {
            text = msg
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
