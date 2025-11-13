package com.secretary.helloworld.core.network

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.secretary.helloworld.core.logging.AppLogger

/**
 * APK download and installation via DownloadManager
 * Phase 4.5.3 Wave 4: Converted to Kotlin
 *
 * Downloads APK from URL and launches Android installer.
 * Uses Handler-based polling to monitor download status.
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    /**
     * Download APK and install when complete
     */
    fun downloadAndInstall(context: Context, downloadUrl: String, version: String) {
        try {
            AppLogger.info(TAG, "Starting download for version $version")
            AppLogger.debug(TAG, "Download URL: $downloadUrl")

            // Use DownloadManager
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val uri = Uri.parse(downloadUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("AI Secretary Update")
                setDescription("Downloading version $version")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AISecretary-$version.apk")
            }

            val downloadId = downloadManager.enqueue(request)
            AppLogger.info(TAG, "Download enqueued with ID: $downloadId")

            // Poll download status using Handler
            // This is more reliable than BroadcastReceiver on newer Android versions
            val handler = Handler(Looper.getMainLooper())
            val checkDownload = object : Runnable {
                override fun run() {
                    val query = DownloadManager.Query().apply {
                        setFilterById(downloadId)
                    }

                    downloadManager.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = cursor.getInt(statusIndex)

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    AppLogger.info(TAG, "Download completed successfully")

                                    // Get download URI from DownloadManager (content:// URI)
                                    val downloadUri = downloadManager.getUriForDownloadedFile(downloadId)

                                    if (downloadUri != null) {
                                        AppLogger.info(TAG, "Starting installation with URI: $downloadUri")
                                        Toast.makeText(context, "Download complete! Starting installation...", Toast.LENGTH_LONG).show()
                                        installApk(context, downloadUri)
                                    } else {
                                        AppLogger.error(TAG, "Failed to get download URI")
                                        Toast.makeText(context, "Installation failed. Please install manually from Downloads.", Toast.LENGTH_LONG).show()
                                    }
                                }

                                DownloadManager.STATUS_FAILED -> {
                                    AppLogger.error(TAG, "Download failed")
                                    Toast.makeText(context, "Download failed. Please try again.", Toast.LENGTH_LONG).show()
                                }

                                DownloadManager.STATUS_RUNNING -> {
                                    // Get download progress
                                    val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                    val bytes = cursor.getInt(bytesIndex)
                                    val total = cursor.getInt(totalIndex)

                                    if (total > 0) {
                                        val progress = (bytes * 100) / total
                                        AppLogger.debug(TAG, "Download progress: $progress%")
                                    }

                                    // Check again in 500ms
                                    handler.postDelayed(this, 500)
                                }

                                DownloadManager.STATUS_PENDING,
                                DownloadManager.STATUS_PAUSED -> {
                                    AppLogger.debug(TAG, "Download status: $status")
                                    // Check again in 500ms
                                    handler.postDelayed(this, 500)
                                }
                            }
                        } else {
                            AppLogger.error(TAG, "Download cursor is null or empty")
                        }
                    } ?: run {
                        AppLogger.error(TAG, "Download cursor is null")
                    }
                }
            }

            AppLogger.info(TAG, "Starting download status monitoring")
            // Start checking download status after 100ms
            handler.postDelayed(checkDownload, 100)

        } catch (e: Exception) {
            AppLogger.error(TAG, "Error downloading update", e)
        }
    }

    /**
     * Install APK using Android Intent
     */
    private fun installApk(context: Context, apkUri: Uri) {
        try {
            AppLogger.info(TAG, "Launching APK installer with URI: $apkUri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

                // For Android 7+ (API 24+), must use content:// URIs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // URI should already be a content:// URI from DownloadManager
                    AppLogger.info(TAG, "Using content URI for Android 7+")
                }
            }

            context.startActivity(intent)
            AppLogger.info(TAG, "APK installer intent launched successfully")
            Toast.makeText(context, "Opening installer...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.error(TAG, "Error installing APK", e)
            Toast.makeText(context, "Failed to open installer. Please install manually from Downloads.", Toast.LENGTH_LONG).show()
        }
    }
}
