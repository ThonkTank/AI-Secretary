package com.secretary.helloworld;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * HTTP Server for providing log access to external tools like Termux.
 * Runs on localhost:8080 and provides a simple REST API for logs.
 */
public class LogServer extends NanoHTTPD {
    private static final String TAG = "LogServer";
    private static final int PORT = 8080;
    private Context context;
    private AppLogger logger;

    public LogServer(Context context) throws IOException {
        super(PORT);
        this.context = context;
        this.logger = AppLogger.getInstance(context);

        // Start the server
        start();
        logger.info(TAG, "HTTP Log Server started on port " + PORT);
        logger.info(TAG, "Access logs via: curl http://localhost:" + PORT + "/logs");
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String method = session.getMethod();

        logger.debug(TAG, "HTTP request: " + method + " " + uri);

        // Handle different endpoints
        if ("/logs".equals(uri)) {
            return serveLogs(session);
        } else if ("/logs/clear".equals(uri) && "POST".equals(method)) {
            return clearLogs();
        } else if ("/status".equals(uri)) {
            return serverStatus();
        } else if ("/".equals(uri)) {
            return serveHomepage();
        }

        // 404 for unknown paths
        return Response.newFixedLengthResponse(Status.NOT_FOUND,
            "text/plain", "Not found: " + uri);
    }

    private Response serveLogs(IHTTPSession session) {
        // Get query parameters
        Map<String, String> params = session.getParms();
        String format = params.getOrDefault("format", "text");

        List<String> logs = logger.readLogs();

        if ("json".equals(format)) {
            // Return logs as JSON array
            StringBuilder json = new StringBuilder();
            json.append("{\"logs\":[\n");
            for (int i = 0; i < logs.size(); i++) {
                json.append("  \"").append(escapeJson(logs.get(i))).append("\"");
                if (i < logs.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]}");
            return Response.newFixedLengthResponse(Status.OK,
                "application/json", json.toString());
        } else {
            // Default: plain text
            StringBuilder text = new StringBuilder();
            for (String log : logs) {
                text.append(log).append("\n");
            }
            return Response.newFixedLengthResponse(Status.OK,
                "text/plain; charset=utf-8", text.toString());
        }
    }

    private Response clearLogs() {
        logger.clearLogs();
        return Response.newFixedLengthResponse(Status.OK,
            "text/plain", "Logs cleared successfully");
    }

    private Response serverStatus() {
        String status = String.format(
            "AI Secretary Log Server\n" +
            "Status: Running\n" +
            "Port: %d\n" +
            "Log entries: %d\n" +
            "Endpoints:\n" +
            "  GET  /          - This help page\n" +
            "  GET  /logs      - Get all logs (add ?format=json for JSON)\n" +
            "  POST /logs/clear - Clear all logs\n" +
            "  GET  /status    - Server status\n",
            PORT, logger.readLogs().size()
        );
        return Response.newFixedLengthResponse(Status.OK,
            "text/plain", status);
    }

    private Response serveHomepage() {
        String html =
            "<html><head><title>AI Secretary Log Server</title></head>\n" +
            "<body style='font-family: monospace; padding: 20px;'>\n" +
            "<h1>AI Secretary Log Server</h1>\n" +
            "<p>Server is running on port " + PORT + "</p>\n" +
            "<h2>Available Endpoints:</h2>\n" +
            "<ul>\n" +
            "<li><a href='/logs'>/logs</a> - View all logs (plain text)</li>\n" +
            "<li><a href='/logs?format=json'>/logs?format=json</a> - View logs as JSON</li>\n" +
            "<li>/logs/clear (POST) - Clear all logs</li>\n" +
            "<li><a href='/status'>/status</a> - Server status</li>\n" +
            "</ul>\n" +
            "<h2>Termux Usage:</h2>\n" +
            "<pre>\n" +
            "# Get logs as text\n" +
            "curl http://localhost:8080/logs\n\n" +
            "# Get logs as JSON\n" +
            "curl http://localhost:8080/logs?format=json\n\n" +
            "# Clear logs\n" +
            "curl -X POST http://localhost:8080/logs/clear\n\n" +
            "# Check status\n" +
            "curl http://localhost:8080/status\n" +
            "</pre>\n" +
            "</body></html>";

        return Response.newFixedLengthResponse(Status.OK,
            "text/html", html);
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    public void shutdown() {
        stop();
        logger.info(TAG, "HTTP Log Server stopped");
    }
}