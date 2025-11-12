package com.secretary.helloworld;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Minimal embedded HTTP server based on NanoHTTPD concepts.
 * Simplified for AI Secretary log serving needs.
 */
public abstract class NanoHTTPD {
    private final int port;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;

    public NanoHTTPD(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        serverSocket.setReuseAddress(true);

        running = true;
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (IOException e) {
                        if (running) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleClient(final Socket client) {
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(client.getInputStream()));
                    BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(client.getOutputStream()));

                    // Parse request line
                    String requestLine = reader.readLine();
                    if (requestLine == null || requestLine.isEmpty()) {
                        client.close();
                        return;
                    }

                    String[] parts = requestLine.split(" ");
                    if (parts.length < 3) {
                        client.close();
                        return;
                    }

                    String method = parts[0];
                    String fullUri = parts[1];

                    // Parse URI and query parameters
                    String uri = fullUri;
                    Map<String, String> params = new HashMap<>();
                    int queryIndex = fullUri.indexOf('?');
                    if (queryIndex != -1) {
                        uri = fullUri.substring(0, queryIndex);
                        String query = fullUri.substring(queryIndex + 1);
                        parseQuery(query, params);
                    }

                    // Read headers (simplified - just consume them)
                    String line;
                    Map<String, String> headers = new HashMap<>();
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        int colonIndex = line.indexOf(':');
                        if (colonIndex > 0) {
                            String key = line.substring(0, colonIndex).trim();
                            String value = line.substring(colonIndex + 1).trim();
                            headers.put(key.toLowerCase(), value);
                        }
                    }

                    // Create session
                    SimpleSession session = new SimpleSession(method, uri, params, headers);

                    // Serve the request
                    Response response = serve(session);

                    // Send response
                    writer.write("HTTP/1.1 " + response.status + "\r\n");
                    writer.write("Content-Type: " + response.mimeType + "\r\n");
                    writer.write("Content-Length: " + response.data.length() + "\r\n");
                    writer.write("Access-Control-Allow-Origin: *\r\n");
                    writer.write("Connection: close\r\n");
                    writer.write("\r\n");
                    writer.write(response.data);
                    writer.flush();

                    client.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        client.close();
                    } catch (IOException ignore) {}
                }
            }
        });
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void parseQuery(String query, Map<String, String> params) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0) {
                    params.put(
                        URLDecoder.decode(pair.substring(0, idx), "UTF-8"),
                        URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    );
                } else if (!pair.isEmpty()) {
                    params.put(URLDecoder.decode(pair, "UTF-8"), "");
                }
            } catch (UnsupportedEncodingException e) {
                // Ignore
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public abstract Response serve(IHTTPSession session);

    // Response class
    public static class Response {
        private final String status;
        private final String mimeType;
        private final String data;

        public Response(String status, String mimeType, String data) {
            this.status = status;
            this.mimeType = mimeType;
            this.data = data;
        }

        public static Response newFixedLengthResponse(String status, String mimeType, String data) {
            return new Response(status, mimeType, data);
        }
    }

    // Session interface
    public interface IHTTPSession {
        String getMethod();
        String getUri();
        Map<String, String> getParms();
        Map<String, String> getHeaders();
    }

    // Simple session implementation
    private static class SimpleSession implements IHTTPSession {
        private final String method;
        private final String uri;
        private final Map<String, String> params;
        private final Map<String, String> headers;

        public SimpleSession(String method, String uri, Map<String, String> params, Map<String, String> headers) {
            this.method = method;
            this.uri = uri;
            this.params = params;
            this.headers = headers;
        }

        @Override
        public String getMethod() { return method; }

        @Override
        public String getUri() { return uri; }

        @Override
        public Map<String, String> getParms() { return params; }

        @Override
        public Map<String, String> getHeaders() { return headers; }
    }

    // Status codes
    public static class Status {
        public static final String OK = "200 OK";
        public static final String NOT_FOUND = "404 Not Found";
        public static final String INTERNAL_ERROR = "500 Internal Server Error";
    }
}