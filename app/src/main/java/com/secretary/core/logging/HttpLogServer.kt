package com.secretary.core.logging

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
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
        serverSocket = ServerSocket(PORT).apply {
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

                // Parse request path
                val path = requestLine.split(" ").getOrNull(1) ?: "/"

                // Generate and send response
                val response = generateResponse(path)
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
     */
    private fun generateResponse(path: String): String = when (path) {
        "/logs" -> {
            // Return all logs
            AppLogger.readLogs().joinToString("\n")
        }

        "/status" -> {
            // Return server status
            """
                AI Secretary Log Server
                Status: Running
                Port: $PORT
                Logs: ${AppLogger.readLogs().size} entries
            """.trimIndent()
        }

        "/" -> {
            // Return help text
            """
                AI Secretary HTTP Log Server

                Available endpoints:
                  GET /         - This help text
                  GET /logs     - View all logs
                  GET /status   - Server status

                Usage from Termux:
                  curl http://localhost:8080/logs
            """.trimIndent()
        }

        else -> "404 Not Found: $path"
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
