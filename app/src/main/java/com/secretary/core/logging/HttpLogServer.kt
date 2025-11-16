package com.secretary.core.logging

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * HTTP server for serving logs via localhost:8080.
 * Phase 4.5.3 Wave 3: Converted to Kotlin
 *
 * Provides real-time log access for development and debugging:
 * - GET / - Help text with available endpoints
 * - GET /logs - All logs from AppLogger
 * - GET /status - Server status and log count
 *
 * Renamed from SimpleHttpServer during Phase 4.5.2 refactoring.
 */
class HttpLogServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    @Volatile
    private var running = false

    /**
     * Start HTTP server on port 8080
     * Runs in background thread to avoid blocking main thread
     */
    fun start() {
        // Bind to localhost only (127.0.0.1) for security
        // Phase 1 - Logging Improvements: Fixed network binding
        serverSocket = ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1")).apply {
            reuseAddress = true
        }
        running = true

        serverThread = Thread {
            AppLogger.info(TAG, "Server started on port $PORT")

            while (running) {
                try {
                    serverSocket?.accept()?.let { client ->
                        handleRequest(client)
                    }
                } catch (e: Exception) {
                    if (running) {
                        AppLogger.error(TAG, "Error accepting client: ${e.message}")
                    }
                }
            }
        }.apply {
            start()
        }
    }

    /**
     * Handle individual HTTP request
     * Reads request, generates response, sends back to client
     */
    private fun handleRequest(client: Socket) {
        try {
            client.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val writer = PrintWriter(it.getOutputStream(), true)

                // Read request line
                val requestLine = reader.readLine() ?: return

                AppLogger.debug(TAG, "Request: $requestLine")

                // Skip headers
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }

                // Parse request path and query parameters
                val fullPath = requestLine.split(" ").getOrNull(1) ?: "/"
                val (path, queryString) = if (fullPath.contains("?")) {
                    val parts = fullPath.split("?", limit = 2)
                    parts[0] to parts.getOrNull(1)
                } else {
                    fullPath to null
                }

                // Generate and send response
                val response = generateResponse(path, queryString)
                val responseBytes = response.toByteArray(Charsets.UTF_8)

                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/plain; charset=UTF-8")
                writer.println("Content-Length: ${responseBytes.size}")
                writer.println("Connection: close")
                writer.println()
                writer.print(response)
                writer.flush()
            }
        } catch (e: Exception) {
            AppLogger.error(TAG, "Error handling request: ${e.message}")
        }
    }

    /**
     * Generate HTTP response based on request path
     * Phase 3: Added crash log endpoint and query parameter filtering
     */
    private fun generateResponse(path: String, queryString: String?): String = when (path) {
        "/logs" -> {
            // Return logs with optional filtering
            val logs = AppLogger.readLogs()
            val filtered = filterLogs(logs, queryString)
            filtered.joinToString("\n")
        }

        "/crash" -> {
            // Return crash log if exists
            val crashLog = AppLogger.readCrashLog()
            crashLog ?: "No crash log found"
        }

        "/status" -> {
            // Return server status
            val crashExists = AppLogger.readCrashLog() != null
            """
                AI Secretary Log Server
                Status: Running
                Port: $PORT
                Logs: ${AppLogger.readLogs().size} entries
                Crash log: ${if (crashExists) "Available" else "None"}
            """.trimIndent()
        }

        "/" -> {
            // Return help text
            """
                AI Secretary HTTP Log Server (Phase 3)

                Available endpoints:
                  GET /              - This help text
                  GET /logs          - View all logs
                  GET /logs?level=ERROR - Filter by log level
                  GET /logs?tag=TaskActivity - Filter by component tag
                  GET /logs?search=crash - Search logs
                  GET /crash         - View crash log
                  GET /status        - Server status

                Query parameters for /logs:
                  level=ERROR,WARN   - Filter by log level (comma-separated)
                  tag=TaskActivity   - Filter by tag (case-sensitive)
                  search=keyword     - Search in log messages (case-insensitive)
                  tail=50            - Show only last N entries

                Examples:
                  curl http://localhost:8080/logs
                  curl http://localhost:8080/logs?level=ERROR
                  curl http://localhost:8080/logs?tag=TaskActivity
                  curl http://localhost:8080/logs?search=crash
                  curl http://localhost:8080/logs?level=ERROR&tag=Database
                  curl http://localhost:8080/crash
            """.trimIndent()
        }

        else -> "404 Not Found: $path"
    }

    /**
     * Filter logs based on query parameters
     * Phase 3: Supports level, tag, search, and tail parameters
     */
    private fun filterLogs(logs: List<String>, queryString: String?): List<String> {
        if (queryString == null) return logs

        // Parse query parameters
        val params = parseQueryParams(queryString)
        var filtered = logs

        // Filter by log level (e.g., level=ERROR or level=ERROR,WARN)
        params["level"]?.let { levelParam ->
            val levels = levelParam.split(",").map { it.trim().uppercase() }
            filtered = filtered.filter { log ->
                levels.any { level -> log.contains("[$level]") }
            }
        }

        // Filter by tag (e.g., tag=TaskActivity)
        params["tag"]?.let { tag ->
            filtered = filtered.filter { log ->
                log.contains("[$tag]")
            }
        }

        // Filter by search keyword (case-insensitive)
        params["search"]?.let { keyword ->
            filtered = filtered.filter { log ->
                log.contains(keyword, ignoreCase = true)
            }
        }

        // Tail: return only last N entries
        params["tail"]?.toIntOrNull()?.let { n ->
            filtered = filtered.takeLast(n)
        }

        return filtered
    }

    /**
     * Parse query string into parameter map
     * Example: "level=ERROR&tag=Database" → {"level": "ERROR", "tag": "Database"}
     */
    private fun parseQueryParams(queryString: String): Map<String, String> {
        return queryString.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }
            .toMap()
    }

    /**
     * Stop HTTP server and close socket
     */
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            AppLogger.error(TAG, "Error stopping server: ${e.message}")
        }
        AppLogger.info(TAG, "Server stopped")
    }

    companion object {
        private const val TAG = "HttpLogServer"
        private const val PORT = 8080
    }
}
