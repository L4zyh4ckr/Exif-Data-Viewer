package com.example.exifdataviewer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var metadataTextView: TextView
    private lateinit var pickButton: Button

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var granted = true
        permissions.entries.forEach {
            if (!it.value) granted = false
        }
        if (granted) {
            startForegroundService()
        } else {
            metadataTextView.text = "Permissions denied. App won’t work."
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            displayMetadata(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = ScrollView(this).apply {
            metadataTextView = TextView(this@MainActivity).apply {
                textSize = 14f
                setPadding(20, 20, 20, 20)
            }
            pickButton = Button(this@MainActivity).apply {
                text = "Pick Media File"
                setOnClickListener { pickFile() }
            }
            addView(pickButton)
            addView(metadataTextView)
        }
        setContentView(layout)

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Below Android 13 fallback
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            startForegroundService()
        }
    }

    private fun startForegroundService() {
        val intent = android.content.Intent(this, ForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun pickFile() {
        filePickerLauncher.launch("*/*")
    }

    private fun displayMetadata(uri: Uri) {
        val scheme = uri.scheme ?: ""
        metadataTextView.text = ""

        if (scheme == "content") {
            val mimeType = contentResolver.getType(uri) ?: "unknown"
            metadataTextView.append("MIME type: $mimeType\n\n")

            if (mimeType.startsWith("image")) {
                try {
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    inputStream?.let {
                        val exif = ExifInterface(it)
                        val tags = listOf(
                            ExifInterface.TAG_DATETIME,
                            ExifInterface.TAG_MAKE,
                            ExifInterface.TAG_MODEL,
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.TAG_GPS_LATITUDE,
                            ExifInterface.TAG_GPS_LONGITUDE
                        )
                        for (tag in tags) {
                            val value = exif.getAttribute(tag)
                            if (value != null) {
                                metadataTextView.append("$tag: $value\n")
                            }
                        }
                        inputStream.close()
                    }
                } catch (e: Exception) {
                    metadataTextView.append("Failed to read EXIF: ${e.message}\n")
                }
            } else if (mimeType.startsWith("video") || mimeType.startsWith("audio")) {
                try {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(this, uri)
                    val metadataKeys = listOf(
                        MediaMetadataRetriever.METADATA_KEY_ALBUM,
                        MediaMetadataRetriever.METADATA_KEY_ARTIST,
                        MediaMetadataRetriever.METADATA_KEY_DURATION,
                        MediaMetadataRetriever.METADATA_KEY_GENRE,
                        MediaMetadataRetriever.METADATA_KEY_TITLE,
                        MediaMetadataRetriever.METADATA_KEY_DATE,
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )
                    for (key in metadataKeys) {
                        val value = mmr.extractMetadata(key)
                        if (!value.isNullOrEmpty()) {
                            metadataTextView.append("$key: $value\n")
                        }
                    }
                    mmr.release()
                } catch (e: Exception) {
                    metadataTextView.append("Failed to read metadata: ${e.message}\n")
                }
            } else {
                metadataTextView.append("Unsupported file type for metadata extraction.\n")
            }
        } else {
            metadataTextView.append("Unsupported URI scheme: $scheme\n")
        }
    }
}
