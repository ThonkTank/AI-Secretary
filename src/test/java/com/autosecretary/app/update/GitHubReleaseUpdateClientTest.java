package com.autosecretary.app.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class GitHubReleaseUpdateClientTest extends AutoSecretaryRobolectricTest {
    private TinyHttpServer server;
    private String baseUrl;

    @Before
    public void startServer() throws Exception {
        server = new TinyHttpServer();
        baseUrl = server.baseUrl();
    }

    @After
    public void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void fetchLatestUpdateReadsVersionAndApkAssetsFromGitHubRelease() throws Exception {
        respondText("/version.txt", "42");
        respondBytes("/AutoSecretary.apk", "apk".getBytes(StandardCharsets.UTF_8));
        respondText("/latest", releaseJson(baseUrl + "/version.txt", baseUrl + "/AutoSecretary.apk"));
        GitHubReleaseUpdateClient client = clientFor("/latest");

        AvailableUpdate update = client.fetchLatestUpdate();

        assertEquals(42, update.versionCode());
        assertEquals(baseUrl + "/AutoSecretary.apk", update.apkDownloadUrl());
        assertEquals("AutoSecretary Build 42", update.releaseName());
        assertEquals("https://github.example/releases/build-42", update.releasePageUrl());
    }

    @Test
    public void fetchLatestUpdateFailsWhenApkAssetIsMissing() throws Exception {
        respondText("/version.txt", "42");
        respondText("/latest", """
                {
                  "tag_name": "build-42",
                  "name": "AutoSecretary Build 42",
                  "html_url": "https://github.example/releases/build-42",
                  "assets": [
                    {"name": "version.txt", "browser_download_url": "%s/version.txt"}
                  ]
                }
                """.formatted(baseUrl));
        GitHubReleaseUpdateClient client = clientFor("/latest");

        try {
            client.fetchLatestUpdate();
            fail("Expected missing APK asset to fail");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("AutoSecretary.apk"));
        }
    }

    @Test
    public void fetchLatestUpdateFailsWhenVersionAssetIsNotAnInteger() throws Exception {
        respondText("/version.txt", "not-a-number");
        respondBytes("/AutoSecretary.apk", "apk".getBytes(StandardCharsets.UTF_8));
        respondText("/latest", releaseJson(baseUrl + "/version.txt", baseUrl + "/AutoSecretary.apk"));
        GitHubReleaseUpdateClient client = clientFor("/latest");

        try {
            client.fetchLatestUpdate();
            fail("Expected invalid version asset to fail");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("versionCode"));
        }
    }

    @Test
    public void downloadApkStreamsReleaseAssetToTargetFile() throws Exception {
        byte[] apkBytes = "downloaded-apk".getBytes(StandardCharsets.UTF_8);
        respondBytes("/AutoSecretary.apk", apkBytes);
        GitHubReleaseUpdateClient client = clientFor("/unused");
        File target = File.createTempFile("autosecretary-update", ".apk");

        client.downloadApk(new AvailableUpdate(42, baseUrl + "/AutoSecretary.apk", "release", "page"), target);

        assertEquals("downloaded-apk", readFile(target));
    }

    private GitHubReleaseUpdateClient clientFor(String path) {
        return new GitHubReleaseUpdateClient(baseUrl + path, 1000, 1000, 1000, 1000);
    }

    private String releaseJson(String versionUrl, String apkUrl) {
        return """
                {
                  "tag_name": "build-42",
                  "name": "AutoSecretary Build 42",
                  "html_url": "https://github.example/releases/build-42",
                  "assets": [
                    {"name": "version.txt", "browser_download_url": "%s"},
                    {"name": "AutoSecretary.apk", "browser_download_url": "%s"}
                  ]
                }
                """.formatted(versionUrl, apkUrl);
    }

    private void respondText(String path, String body) {
        respondBytes(path, body.getBytes(StandardCharsets.UTF_8));
    }

    private void respondBytes(String path, byte[] body) {
        server.respond(path, body);
    }

    private String readFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class TinyHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Map<String, byte[]> responses = new HashMap<>();
        private final Thread thread;
        private volatile boolean running = true;

        private TinyHttpServer() throws IOException {
            serverSocket = new ServerSocket(0);
            thread = new Thread(this::serve);
            thread.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        private void respond(String path, byte[] body) {
            responses.put(path, body);
        }

        private void serve() {
            while (running) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    if (running) {
                        throw new AssertionError(e);
                    }
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Drain headers.
            }
            String path = requestLine == null ? "" : requestLine.split(" ")[1];
            byte[] body = responses.get(path);
            int status = body == null ? 404 : 200;
            byte[] responseBody = body == null ? "missing".getBytes(StandardCharsets.UTF_8) : body;

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            response.write(("HTTP/1.1 " + status + " OK\r\n").getBytes(StandardCharsets.UTF_8));
            response.write(("Content-Length: " + responseBody.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            response.write("Connection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            response.write(responseBody);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(response.toByteArray());
            outputStream.flush();
        }

        @Override
        public void close() throws Exception {
            running = false;
            serverSocket.close();
            thread.join(1000);
        }
    }
}
