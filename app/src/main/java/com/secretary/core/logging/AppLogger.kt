package com.secretary.core.logging

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleton AppLogger for centralized in-memory logging.
 *
 * Logs are stored in memory (max 500 lines) and accessible via HTTP server
 * on localhost:8080/logs. Also writes to Android Logcat for redundancy.
 *
 * Converted to Kotlin in Phase 4.5.3 (Kotlin Migration)
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOG_LINES = 500

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val logLines = mutableListOf<String>()

    @Volatile
    private var initialized = false

    /**
     * Initialize the logger with context.
     * Safe to call multiple times - initialization happens only once.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (!initialized) {
            initialized = true
            info(TAG, "AppLogger initialized (IN-MEMORY ONLY)")
            info(TAG, "Logs accessible via HTTP server: curl http://localhost:8080/logs")
        }
    }

    /**
     * Log INFO level message
     */
    fun info(tag: String, message: String) {
        writeLog("INFO", tag, message)
    }

    /**
     * Log DEBUG level message
     */
    fun debug(tag: String, message: String) {
        writeLog("DEBUG", tag, message)
    }

    /**
     * Log ERROR level message
     */
    fun error(tag: String, message: String) {
        writeLog("ERROR", tag, message)
    }

    /**
     * Log ERROR level message with throwable
     */
    fun error(tag: String, message: String, throwable: Throwable) {
        val stackTrace = Log.getStackTraceString(throwable)
        writeLog("ERROR", tag, "$message\n$stackTrace")
    }

    /**
     * Write log entry to memory and Logcat
     */
    @Synchronized
    private fun writeLog(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] [$level] [$tag] $message"

        // Write to Logcat
        when (level) {
            "ERROR" -> Log.e(tag, message)
            "DEBUG" -> Log.d(tag, message)
            else -> Log.i(tag, message)
        }

        // Store in memory
        logLines.add(logEntry)

        // Auto-trim if too many lines
        if (logLines.size > MAX_LOG_LINES) {
            // Keep only the last MAX_LOG_LINES
            val startIndex = logLines.size - MAX_LOG_LINES
            val trimmedLogs = logLines.subList(startIndex, logLines.size).toMutableList()
            logLines.clear()
            logLines.addAll(trimmedLogs)
        }
    }

    /**
     * Read all logs from memory
     */
    @Synchronized
    fun readLogs(): List<String> {
        return ArrayList(logLines)
    }

    /**
     * Clear all logs from memory
     */
    @Synchronized
    fun clearLogs() {
        logLines.clear()
        info(TAG, "Logs manually cleared by user")
    }

    /**
     * Get instance for compatibility with Java code during migration.
     * In Kotlin, just call AppLogger.method() directly.
     *
     * @deprecated Use AppLogger.method() directly in Kotlin
     */
    @Deprecated("Use AppLogger.method() directly", ReplaceWith("AppLogger"))
    @JvmStatic
    fun getInstance(context: Context): AppLogger {
        initialize(context)
        return this
    }
}
