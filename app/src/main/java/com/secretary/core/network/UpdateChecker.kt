package com.secretary.core.network

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.secretary.core.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update checker for GitHub Releases
 * Phase 4.5.3 Wave 4: Converted to Kotlin
 *
 * Checks for new app versions via GitHub Releases API
 * and notifies via sealed class results.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_REPO = "ThonkTank/AI-Secretary"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    /**
     * Sealed class for update check results
     */
    sealed class UpdateResult {
        data class Available(
            val version: String,
            val downloadUrl: String,
            val changelog: String
        ) : UpdateResult()

        object NoUpdate : UpdateResult()

        data class Error(val message: String) : UpdateResult()
    }

    /**
     * Java-compatible callback interface
     */
    interface UpdateListener {
        fun onUpdateAvailable(version: String, downloadUrl: String, changelog: String)
        fun onNoUpdateAvailable()
        fun onError(error: String)
    }

    /**
     * Java-compatible wrapper method with callback
     * Launches coroutine internally and converts sealed class results to callbacks
     */
    @JvmStatic
    fun checkForUpdates(context: Context, listener: UpdateListener) {
        CoroutineScope(Dispatchers.Main).launch {
            when (val result = checkForUpdatesInternal(context)) {
                is UpdateResult.Available -> listener.onUpdateAvailable(result.version, result.downloadUrl, result.changelog)
                is UpdateResult.NoUpdate -> listener.onNoUpdateAvailable()
                is UpdateResult.Error -> listener.onError(result.message)
            }
        }
    }

    /**
     * Check for updates on GitHub Releases
     * Uses coroutines for async network call
     * Internal suspend function used by Java-compatible wrapper
     */
    private suspend fun checkForUpdatesInternal(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // Get current version
            val currentVersionCode = context.getVersionCode()
            val currentVersionName = context.getVersionName()

            AppLogger.info(TAG, "Starting update check. Current version: $currentVersionName (code: $currentVersionCode)")

            // Query GitHub Releases API
            val url = URL(GITHUB_API_URL)
            val conn = url.openConnection() as HttpURLConnection

            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                // No Authorization header needed for public repos
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            AppLogger.debug(TAG, "GitHub API response code: $responseCode")

            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)

                // Parse JSON
                val release = JSONObject(response)
                val latestVersionName = release.getString("tag_name").replace("v", "")
                val changelog = release.optString("body", "Keine Changelog-Information verfügbar")

                AppLogger.info(TAG, "Latest version from GitHub: $latestVersionName")

                // Find APK asset
                var downloadUrl: String? = null
                if (release.has("assets")) {
                    val assets = release.getJSONArray("assets")
                    AppLogger.debug(TAG, "Found ${assets.length()} assets in release")

                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            AppLogger.info(TAG, "Found APK asset: $name")
                            break
                        }
                    }
                }

                // Compare versions
                when {
                    downloadUrl != null && latestVersionName != currentVersionName -> {
                        AppLogger.info(TAG, "Update available! $currentVersionName -> $latestVersionName")
                        UpdateResult.Available(latestVersionName, downloadUrl, changelog)
                    }
                    else -> {
                        AppLogger.info(TAG, "No update available. Current version is up to date.")
                        UpdateResult.NoUpdate
                    }
                }
            } else {
                val error = "GitHub API returned $responseCode"
                AppLogger.error(TAG, error)
                UpdateResult.Error(error)
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Error checking for updates", e)
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Extension function to get version code from Context
     */
    private fun Context.getVersionCode(): Int {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    /**
     * Extension function to get version name from Context
     */
    private fun Context.getVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }
}
