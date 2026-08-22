package com.rashid.phonediagnostics

import android.Manifest
import android.app.RecoverableSecurityException
import android.app.usage.StorageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CacheScannerActivity : AppCompatActivity() {

    private lateinit var appCacheContainer: LinearLayout
    private lateinit var largeFilesContainer: LinearLayout

    private val storagePermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cache_scanner)

        appCacheContainer = findViewById(R.id.appCacheContainer)
        largeFilesContainer = findViewById(R.id.largeFilesContainer)

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        loadAppCache()
        requestStoragePermissionAndLoadFiles()
    }

    private fun loadAppCache() {
        appCacheContainer.removeAllViews()

        val storageStatsManager = getSystemService(STORAGE_STATS_SERVICE) as? StorageStatsManager
        if (storageStatsManager == null) {
            appCacheContainer.addView(makeInfoText("এই ফোনে App Cache তথ্য পাওয়া যাচ্ছে না"))
            return
        }

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        data class AppCache(val label: String, val packageName: String, val cacheBytes: Long)

        val list = mutableListOf<AppCache>()

        for (appInfo in apps) {
            try {
                val stats = storageStatsManager.queryStatsForUid(StorageManager.UUID_DEFAULT, appInfo.uid)
                if (stats.cacheBytes > 0) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    list.add(AppCache(label, appInfo.packageName, stats.cacheBytes))
                }
            } catch (e: Exception) {
                // এই অ্যাপের তথ্য না পেলে স্কিপ করা হচ্ছে
            }
        }

        if (list.isEmpty()) {
            appCacheContainer.addView(makeInfoText("App Cache দেখতে Usage Access permission দরকার (Screen Time-এর জন্য আগে যেভাবে দিয়েছিলেন)"))
            return
        }

        val top = list.sortedByDescending { it.cacheBytes }.take(15)

        for (item in top) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#161B22"))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${item.packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@CacheScannerActivity, "খোলা যায়নি", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
                try {
                    setImageDrawable(packageManager.getApplicationIcon(item.packageName))
                } catch (e: Exception) {
                    setImageDrawable(packageManager.defaultActivityIcon)
                }
            }

            val name = TextView(this).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val size = TextView(this).apply {
                text = formatBytes(item.cacheBytes)
                setTextColor(Color.parseColor("#9AA0A6"))
                textSize = 13f
            }

            row.addView(icon)
            row.addView(name)
            row.addView(size)
            appCacheContainer.addView(row)
        }
    }

    private fun requestStoragePermissionAndLoadFiles() {
        val notGranted = storagePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) {
            loadLargeFiles()
        } else {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), STORAGE_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadLargeFiles()
            } else {
                largeFilesContainer.removeAllViews()
                largeFilesContainer.addView(makeInfoText("Large Files দেখতে Storage permission দরকার"))
            }
        }
    }

    private data class LargeFile(val uri: Uri, val name: String, val size: Long)

    private fun loadLargeFiles() {
        largeFilesContainer.removeAllViews()

        val minSizeBytes = 50L * 1024 * 1024
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > ?"
        val selectionArgs = arrayOf(minSizeBytes.toString())
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        val results = mutableListOf<LargeFile>()

        try {
            contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext() && results.size < 30) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Unknown"
                    val size = cursor.getLong(sizeCol)
                    val uri = Uri.withAppendedPath(collection, id.toString())
                    results.add(LargeFile(uri, name, size))
                }
            }
        } catch (e: Exception) {
            largeFilesContainer.addView(makeInfoText("Large Files খুঁজতে সমস্যা হয়েছে"))
            return
        }

        if (results.isEmpty()) {
            largeFilesContainer.addView(makeInfoText("৫০ MB এর বেশি কোনো ফাইল পাওয়া যায়নি"))
            return
        }

        for (file in results) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#161B22"))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
            }

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(TextView(this).apply {
                text = file.name
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
            })
            info.addView(TextView(this).apply {
                text = formatBytes(file.size)
                setTextColor(Color.parseColor("#9AA0A6"))
                textSize = 12f
            })

            val deleteBtn = TextView(this).apply {
                text = "Delete"
                setTextColor(Color.parseColor("#FF6B6B"))
                textSize = 13f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                isClickable = true
                isFocusable = true
                setOnClickListener { confirmDelete(file, row) }
            }

            row.addView(info)
            row.addView(deleteBtn)
            largeFilesContainer.addView(row)
        }
    }

    private fun confirmDelete(file: LargeFile, rowView: LinearLayout) {
        android.app.AlertDialog.Builder(this)
            .setTitle("ডিলিট করবেন?")
            .setMessage("${file.name} (${formatBytes(file.size)}) মুছে ফেলা হবে")
            .setPositiveButton("Delete") { _, _ -> deleteFile(file, rowView) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFile(file: LargeFile, rowView: LinearLayout) {
        try {
            val rows = contentResolver.delete(file.uri, null, null)
            if (rows > 0) {
                largeFilesContainer.removeView(rowView)
                Toast.makeText(this, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                try {
                    val sender = e.userAction.actionIntent.intentSender
                    startIntentSenderForResult(sender, DELETE_REQUEST_CODE, null, 0, 0, 0)
                } catch (ex: Exception) {
                    Toast.makeText(this, "ডিলিট করা যায়নি", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "ডিলিট করার অনুমতি নেই", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "ডিলিট করা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DELETE_REQUEST_CODE) {
            loadLargeFiles()
        }
    }

    private fun makeInfoText(msg: String): TextView {
        return TextView(this).apply {
            text = msg
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1) {
            String.format("%.2f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.1f MB", mb)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val STORAGE_PERMISSION_CODE = 501
        private const val DELETE_REQUEST_CODE = 502
    }
}
