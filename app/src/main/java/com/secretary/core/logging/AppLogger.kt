package com.secretary.core.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Singleton AppLogger for centralized in-memory logging.
 *
 * Logs are stored in memory (max 1500 lines) and accessible via HTTP server
 * on localhost:8080/logs. Also writes to Android Logcat for redundancy.
 *
 * Converted to Kotlin in Phase 4.5.3 (Kotlin Migration)
 * Buffer increased from 500 to 1500 entries in Phase 1 (Logging Improvements)
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOG_LINES = 1500  // Increased from 500 (Phase 1 - Logging Improvements)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val logLines = mutableListOf<String>()

    @Volatile
    private var initialized = false

    private lateinit var crashLogFile: File
    private var appContext: Context? = null

    /**
     * Initialize the logger with context.
     * Safe to call multiple times - initialization happens only once.
     */
    @Synchronized
    fun initialize(context: Context) {
        if (!initialized) {
            initialized = true
            appContext = context.applicationContext

            // Setup crash log file in app-specific directory (no permissions needed)
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            logsDir.mkdirs()
            crashLogFile = File(logsDir, "crash.log")

            // Install crash handler
            setupCrashHandler()

            info(TAG, "AppLogger initialized (IN-MEMORY + CRASH LOGGING)")
            info(TAG, "Logs accessible via HTTP server: curl http://localhost:8080/logs")
            info(TAG, "Crash logs saved to: ${crashLogFile.absolutePath}")
        }
    }

    /**
     * Setup uncaught exception handler to save crash logs
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Log to memory
                error(TAG, "UNCAUGHT EXCEPTION in thread ${thread.name}", throwable)

                // Save crash log to file
                saveCrashLog(throwable, thread)

                // Call default handler
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash log", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Save crash information to persistent file
     */
    private fun saveCrashLog(throwable: Throwable, thread: Thread) {
        try {
            FileWriter(crashLogFile, false).use { writer ->
                writer.write("=== AI SECRETARY CRASH LOG ===\n")
                writer.write("Time: ${dateFormat.format(Date())}\n")
                writer.write("Thread: ${thread.name}\n")
                writer.write("Exception: ${throwable.javaClass.name}\n")
                writer.write("Message: ${throwable.message}\n\n")

                writer.write("=== STACK TRACE ===\n")
                writer.write(Log.getStackTraceString(throwable))
                writer.write("\n\n")

                writer.write("=== RECENT LOGS ===\n")
                readLogs().forEach { line ->
                    writer.write(line)
                    writer.write("\n")
                }

                writer.flush()
            }

            Log.e(TAG, "Crash log saved to: ${crashLogFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log file", e)
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
     * Read crash log from file (if exists)
     * Returns null if no crash log found
     */
    fun readCrashLog(): String? {
        return try {
            if (::crashLogFile.isInitialized && crashLogFile.exists()) {
                crashLogFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read crash log", e)
            null
        }
    }

    /**
     * Delete crash log file
     */
    fun deleteCrashLog(): Boolean {
        return try {
            if (::crashLogFile.isInitialized && crashLogFile.exists()) {
                crashLogFile.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete crash log", e)
            false
        }
    }

    /**
     * Get crash log file path for external access
     */
    fun getCrashLogPath(): String? {
        return if (::crashLogFile.isInitialized) {
            crashLogFile.absolutePath
        } else {
            null
        }
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
