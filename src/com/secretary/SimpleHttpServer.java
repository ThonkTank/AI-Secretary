package com.secretary.helloworld;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.List;

/**
 * Simplified HTTP Server for serving logs.
 * Minimal implementation focused on reliability.
 */
public class SimpleHttpServer {
    private static final String TAG = "SimpleHttpServer";
    private static final int PORT = 8080;
    private Context context;
    private AppLogger logger;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;

    public SimpleHttpServer(Context context) {
        this.context = context;
        this.logger = AppLogger.getInstance(context);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(PORT);
        serverSocket.setReuseAddress(true);
        running = true;

        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                logger.info(TAG, "Server started on port " + PORT);

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleRequest(client);
                    } catch (IOException e) {
                        if (running) {
                            logger.error(TAG, "Error accepting client: " + e.getMessage());
                        }
                    }
                }
            }
        });

        serverThread.start();
    }

    private void handleRequest(Socket client) {
        try {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(
                client.getOutputStream(), true);

            // Read request line
            String requestLine = in.readLine();
            if (requestLine == null) {
                client.close();
                return;
            }

            logger.debug(TAG, "Request: " + requestLine);

            // Read headers (and ignore them for simplicity)
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Skip headers
            }

            // Parse request
            String[] parts = requestLine.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";

            // Generate response based on path
            String response = generateResponse(path);

            // Send HTTP response
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain; charset=UTF-8");
            out.println("Content-Length: " + response.getBytes("UTF-8").length);
            out.println("Connection: close");
            out.println();
            out.print(response);
            out.flush();

            // Close connection
            client.close();

        } catch (Exception e) {
            logger.error(TAG, "Error handling request: " + e.getMessage());
            try {
                client.close();
            } catch (IOException ignore) {}
        }
    }

    private String generateResponse(String path) {
        if ("/logs".equals(path)) {
            // Return logs
            List<String> logs = logger.readLogs();
            StringBuilder sb = new StringBuilder();
            for (String log : logs) {
                sb.append(log).append("\n");
            }
            return sb.toString();

        } else if ("/status".equals(path)) {
            // Return status
            return "AI Secretary Log Server\n" +
                   "Status: Running\n" +
                   "Port: " + PORT + "\n" +
                   "Logs: " + logger.readLogs().size() + " entries\n";

        } else if ("/".equals(path)) {
            // Return help
            return "AI Secretary HTTP Log Server\n\n" +
                   "Available endpoints:\n" +
                   "  GET /         - This help text\n" +
                   "  GET /logs     - View all logs\n" +
                   "  GET /status   - Server status\n\n" +
                   "Usage from Termux:\n" +
                   "  curl http://localhost:8080/logs\n";

        } else {
            return "404 Not Found: " + path;
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error(TAG, "Error stopping server: " + e.getMessage());
        }
        logger.info(TAG, "Server stopped");
    }
}