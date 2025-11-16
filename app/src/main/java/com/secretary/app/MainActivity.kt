package com.secretary.app

import com.secretary.R
import com.secretary.TaskActivity
import com.secretary.core.config.AppPreferences
import com.secretary.core.logging.AppLogger
import com.secretary.core.logging.HttpLogServer
import com.secretary.core.network.UpdateChecker
import com.secretary.core.network.UpdateInstaller
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Main Activity - App Entry Point
 * Phase 4.5.3 Wave 7: Converted to Kotlin
 *
 * Landing page with:
 * - "Open Tasks" button → TaskActivity
 * - Settings menu → Update check, View logs
 * - HTTP log server initialization
 * - Real-time log display
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var logger: AppLogger
    private var httpServer: HttpLogServer? = null

    // ========== Lifecycle Methods ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "=== Application onCreate started ===")

        // Initialize Preferences
        AppPreferences.initialize(this)

        // Initialize Logger
        logger = AppLogger.getInstance(this)
        logger.info(TAG, "=== Application started ===")

        // Log current version
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            logger.info(TAG, "App version: ${pInfo.versionName} (code: ${pInfo.versionCode})")
            Log.i(TAG, "App version: ${pInfo.versionName} (code: ${pInfo.versionCode})")
        } catch (e: PackageManager.NameNotFoundException) {
            logger.error(TAG, "Could not get package info", e)
        }

        logger.info(TAG, "Settings menu will be in Action Bar")

        // Start HTTP Log Server
        try {
            val networkEnabled = AppPreferences.isNetworkLogsEnabled()
            httpServer = HttpLogServer(this).apply { start(bindToAllInterfaces = networkEnabled) }
            logger.info(TAG, "HTTP Log Server started successfully")

            if (networkEnabled) {
                val localIp = httpServer?.getLocalNetworkIp()
                logger.info(TAG, "Network mode enabled - Logs accessible at: http://$localIp:8080/logs")
            } else {
                logger.info(TAG, "Access logs from Termux: curl http://localhost:8080/logs")
            }
        } catch (e: Exception) {
            logger.error(TAG, "Failed to start HTTP Log Server", e)
        }

        // Setup Tasks button
        val tasksButton = findViewById<Button>(R.id.openTasksButton)
        if (tasksButton == null) {
            logger.error(TAG, "CRITICAL: Tasks button not found in layout!")
            Log.e(TAG, "CRITICAL: findViewById returned null for R.id.openTasksButton")
        } else {
            logger.info(TAG, "Tasks button found successfully")
            tasksButton.setOnClickListener {
                try {
                    logger.info(TAG, "Opening Tasks activity - button clicked")
                    Log.i(TAG, "DEBUG: About to create Intent for TaskActivity")

                    val intent = Intent(this, TaskActivity::class.java)
                    Log.i(TAG, "DEBUG: Intent created successfully")

                    startActivity(intent)
                    Log.i(TAG, "DEBUG: startActivity() called successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "FATAL: Failed to start TaskActivity", e)
                    logger.error(TAG, "Failed to start TaskActivity: ${e.message}", e)

                    AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("Cannot open Tasks:\n${e.javaClass.simpleName}\n${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            logger.info(TAG, "Tasks button initialized")
        }

        // Display logs
        findViewById<TextView>(R.id.mainLogsTextView)?.let { mainLogsTextView ->
            // Show logs immediately
            updateLogsDisplay(mainLogsTextView)

            // Refresh logs after 1 second (for button details)
            mainLogsTextView.postDelayed({
                updateLogsDisplay(mainLogsTextView)
            }, 1000)

            Log.i(TAG, "Main logs display initialized")
            logger.info(TAG, "Main logs display initialized")
        } ?: run {
            Log.e(TAG, "Main logs TextView NOT FOUND!")
            logger.error(TAG, "Main logs TextView NOT FOUND!")
        }
    }

    private fun updateLogsDisplay(textView: TextView) {
        val logs = logger.readLogs()
        if (logs.isEmpty()) {
            textView.text = "=== NO LOGS YET ===\n\nIf you see this, the app started but no logs were written."
        } else {
            val logText = buildString {
                append("=== APP LOGS (Auto-refresh) ===\n\n")
                logs.forEach { line ->
                    append(line).append("\n")
                }
            }
            textView.text = logText
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        logger.info(TAG, "Action Bar menu created")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                logger.info(TAG, "Settings menu item clicked")
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shutdown HTTP server
        httpServer?.let {
            it.stop()
            logger.info(TAG, "HTTP Log Server stopped")
        }
    }

    // ========== Settings Dialog ==========

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Views in dialog
        val versionText = dialogView.findViewById<TextView>(R.id.settingsVersionText)
        val updateStatus = dialogView.findViewById<TextView>(R.id.settingsUpdateStatus)
        val checkUpdateButton = dialogView.findViewById<Button>(R.id.settingsCheckUpdateButton)
        val viewLogsButton = dialogView.findViewById<Button>(R.id.settingsViewLogsButton)
        val networkCheckbox = dialogView.findViewById<CheckBox>(R.id.settingsNetworkLogsCheckbox)
        val networkInfoText = dialogView.findViewById<TextView>(R.id.settingsNetworkInfoText)
        val whitelistButton = dialogView.findViewById<Button>(R.id.settingsWhitelistButton)
        val closeButton = dialogView.findViewById<Button>(R.id.settingsCloseButton)

        // Display version
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "Version ${pInfo.versionName} (Build ${pInfo.versionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            versionText.text = "Version unknown"
        }

        // Initial: Not checked yet
        updateStatus.text = "Not checked yet"

        // Update Check Button
        checkUpdateButton.setOnClickListener {
            logger.info(TAG, "User initiated update check from settings")
            updateStatus.text = "Checking for updates..."
            checkUpdateButton.isEnabled = false

            UpdateChecker.checkForUpdates(this, object : UpdateChecker.UpdateListener {
                override fun onUpdateAvailable(version: String, downloadUrl: String, changelog: String) {
                    logger.info(TAG, "Update dialog shown to user for version $version")
                    updateStatus.text = "Update available: v$version"
                    checkUpdateButton.isEnabled = true

                    // Show update dialog
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Update Available")
                        .setMessage("Version $version is available!\n\n$changelog")
                        .setPositiveButton("Download & Install") { _, _ ->
                            logger.info(TAG, "User accepted update download")
                            updateStatus.text = "Downloading..."
                            UpdateInstaller.downloadAndInstall(this@MainActivity, downloadUrl, version)
                            Toast.makeText(this@MainActivity, "Download started. Check notifications.", Toast.LENGTH_LONG).show()
                        }
                        .setNegativeButton("Later") { _, _ ->
                            logger.info(TAG, "User declined update")
                        }
                        .show()
                }

                override fun onNoUpdateAvailable() {
                    logger.info(TAG, "No update available")
                    updateStatus.text = "You're up to date!"
                    checkUpdateButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "No updates available", Toast.LENGTH_SHORT).show()
                }

                override fun onError(error: String) {
                    logger.error(TAG, "Update check failed: $error")
                    updateStatus.text = "Error: $error"
                    checkUpdateButton.isEnabled = true
                    Toast.makeText(this@MainActivity, "Error checking updates", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // View Logs Button
        viewLogsButton.setOnClickListener {
            logger.info(TAG, "User opened log viewer")
            showLogsDialog()
        }

        // Network Logs Checkbox - Initialize
        networkCheckbox.isChecked = AppPreferences.isNetworkLogsEnabled()
        updateNetworkInfoDisplay(networkInfoText)

        // Network Logs Checkbox - Change Listener
        networkCheckbox.setOnCheckedChangeListener { _, isChecked ->
            logger.info(TAG, "Network logs ${if (isChecked) "enabled" else "disabled"} by user")
            AppPreferences.setNetworkLogsEnabled(isChecked)
            updateNetworkInfoDisplay(networkInfoText)
            restartHttpServer(isChecked)
        }

        // Whitelist Button
        whitelistButton.setOnClickListener {
            logger.info(TAG, "User clicked whitelist button")
            showIpWhitelistDialog(networkInfoText)
        }

        // Close Button
        closeButton.setOnClickListener {
            logger.info(TAG, "Settings dialog closed")
            dialog.dismiss()
        }

        dialog.show()
    }

    // ========== Network Settings Helpers ==========

    /**
     * Update network info text display based on current settings.
     */
    private fun updateNetworkInfoDisplay(networkInfoText: TextView) {
        val networkEnabled = AppPreferences.isNetworkLogsEnabled()
        val whitelistedIp = AppPreferences.getWhitelistedIp()

        networkInfoText.text = if (networkEnabled) {
            val localIp = httpServer?.getLocalNetworkIp()
            buildString {
                append("Network logs enabled\n")
                if (localIp != null) {
                    append("Access at: http://$localIp:8080/logs\n")
                }
                if (whitelistedIp != null) {
                    append("Whitelisted IP: $whitelistedIp")
                } else {
                    append("No IP whitelist (all network IPs allowed)")
                }
            }
        } else {
            "Network logs disabled (localhost only)"
        }
    }

    /**
     * Restart HTTP server with new binding mode.
     */
    private fun restartHttpServer(bindToAllInterfaces: Boolean) {
        try {
            // Stop existing server
            httpServer?.stop()

            // Start with new binding
            httpServer = HttpLogServer(this).apply { start(bindToAllInterfaces = bindToAllInterfaces) }

            val mode = if (bindToAllInterfaces) "NETWORK" else "LOCALHOST"
            logger.info(TAG, "HTTP server restarted in $mode mode")

            if (bindToAllInterfaces) {
                val localIp = httpServer?.getLocalNetworkIp()
                Toast.makeText(this, "Network logs enabled at http://$localIp:8080/logs", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Network logs disabled (localhost only)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            logger.error(TAG, "Failed to restart HTTP server", e)
            Toast.makeText(this, "Error restarting server: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show dialog for entering laptop IP to whitelist.
     */
    private fun showIpWhitelistDialog(networkInfoText: TextView) {
        val inputField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = "192.168.1.100"
            setText(AppPreferences.getWhitelistedIp() ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle("Whitelist Laptop IP")
            .setMessage("Enter the IP address of your laptop to allow access to logs.\n\nLeave empty to allow all network IPs.")
            .setView(inputField)
            .setPositiveButton("Save") { _, _ ->
                val ip = inputField.text.toString().trim()
                if (ip.isEmpty()) {
                    AppPreferences.clearWhitelistedIp()
                    logger.info(TAG, "Whitelist cleared - all IPs allowed")
                    Toast.makeText(this, "Whitelist cleared (all network IPs allowed)", Toast.LENGTH_SHORT).show()
                } else {
                    // Basic IP validation (simple check)
                    if (ip.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}\$"))) {
                        AppPreferences.setWhitelistedIp(ip)
                        logger.info(TAG, "Whitelisted IP set to: $ip")
                        Toast.makeText(this, "Whitelisted IP: $ip", Toast.LENGTH_SHORT).show()
                    } else {
                        logger.error(TAG, "Invalid IP format: $ip")
                        Toast.makeText(this, "Invalid IP format. Please use format: 192.168.1.100", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                }
                updateNetworkInfoDisplay(networkInfoText)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Logs Dialog ==========

    private fun showLogsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logs, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Views in dialog
        val logsTextView = dialogView.findViewById<TextView>(R.id.logsTextView)
        val copyButton = dialogView.findViewById<Button>(R.id.logsCopyButton)
        val closeButton = dialogView.findViewById<Button>(R.id.logsCloseButton)

        // Load and display logs
        val logs = logger.readLogs()
        if (logs.isEmpty()) {
            logsTextView.text = "No logs available yet.\n\nLogs are stored in-memory and accessible via HTTP server:\ncurl http://localhost:8080/logs"
        } else {
            val logText = buildString {
                logs.forEach { line ->
                    append(line).append("\n")
                }
            }
            logsTextView.text = logText
        }

        // Copy Button
        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("App Logs", logsTextView.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
            logger.info(TAG, "User copied logs to clipboard")
        }

        // Close Button
        closeButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
