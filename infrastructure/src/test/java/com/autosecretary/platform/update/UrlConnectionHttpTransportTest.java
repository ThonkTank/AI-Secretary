package com.autosecretary.platform.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.autosecretary.application.update.UpdateFailure;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class UrlConnectionHttpTransportTest {
    private static final String FEED =
            "https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest";
    private static final UrlTrustPolicy TRUST =
            new UrlTrustPolicy("ThonkTank", "AI-Secretary");

    @Test public void limitsRedirectsToFiveTrustedHttpsHops() {
        UrlConnectionHttpTransport transport = transport(url -> new FakeConnection(
                302, "https://github.com/redirect", new byte[0], false));

        HttpTransportException error = assertThrows(
                HttpTransportException.class, () -> transport.get(FEED, 1024));

        assertEquals(HttpTransportException.Kind.INVALID_RESPONSE, error.kind());
        assertTrue(error.getMessage().contains("fünf Hops"));
    }

    @Test public void mapsRateLimitsAndInvalidResponsesWithoutStringGuessing() {
        HttpTransportException rate = assertThrows(HttpTransportException.class,
                () -> transport(url -> new FakeConnection(429, null, new byte[0], false))
                        .get(FEED, 1024));
        UpdateFailure rateFailure = AndroidUpdateRepository.failure("check", rate).failure();
        assertEquals(UpdateFailure.Kind.RATE_LIMITED, rateFailure.kind());
        assertTrue(rateFailure.retryable());

        HttpTransportException invalid = assertThrows(HttpTransportException.class,
                () -> transport(url -> new FakeConnection(503, null, new byte[0], false))
                        .get(FEED, 1024));
        UpdateFailure invalidFailure = AndroidUpdateRepository.failure("check", invalid).failure();
        assertEquals(UpdateFailure.Kind.INVALID_RELEASE, invalidFailure.kind());
        assertFalse(invalidFailure.retryable());
    }

    @Test public void rejectsOversizedContentAndClassifiesTimeoutAsNetwork() {
        byte[] oversized = "12345".getBytes(StandardCharsets.UTF_8);
        assertThrows(HttpTransportException.class,
                () -> transport(url -> new FakeConnection(200, null, oversized, false))
                        .get(FEED, 4));

        SocketTimeoutException timeout = assertThrows(SocketTimeoutException.class,
                () -> transport(url -> new FakeConnection(200, null, new byte[0], true))
                        .get(FEED, 1024));
        UpdateFailure failure = AndroidUpdateRepository.failure("check", timeout).failure();
        assertEquals(UpdateFailure.Kind.NETWORK, failure.kind());
        assertTrue(failure.retryable());
    }

    @Test public void permitsFiveRedirectsButRejectsAnInsecureHop() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = transport(url -> calls.incrementAndGet() <= 5
                ? new FakeConnection(302, "https://github.com/redirect", new byte[0], false)
                : new FakeConnection(200, null, "ok".getBytes(StandardCharsets.UTF_8), false))
                .get(FEED, 16);
        assertEquals("ok", result);
        assertEquals(6, calls.get());

        assertThrows(SecurityException.class, () -> transport(url -> new FakeConnection(
                302, "http://github.com/insecure", new byte[0], false)).get(FEED, 16));
    }

    @Test public void classifiesMalformedReleaseJsonAsInvalidRelease() {
        UpdateFailure failure = AndroidUpdateRepository.failure("check",
                new org.json.JSONException("invalid json")).failure();

        assertEquals(UpdateFailure.Kind.INVALID_RELEASE, failure.kind());
        assertFalse(failure.retryable());
    }

    @Test public void separatesStoragePermissionAndArtifactSecurityFailures() {
        assertEquals(UpdateFailure.Kind.STORAGE, AndroidUpdateRepository.failure(
                "download", new IllegalStateException("Update-Verzeichnis fehlt"))
                .failure().kind());
        assertEquals(UpdateFailure.Kind.PERMISSION, AndroidUpdateRepository.failure(
                "Update-Download konnte nicht gestartet werden",
                new SecurityException("permission denied")).failure().kind());
        assertEquals(UpdateFailure.Kind.SECURITY_REJECTED, AndroidUpdateRepository.failure(
                "Update-Paket wurde verworfen", new SecurityException("wrong signer"))
                .failure().kind());
    }

    @Test public void readsBoundedContentFromALocalFakeServer() throws Exception {
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket server = new ServerSocket(0, 1, loopback)) {
            Thread responder = new Thread(() -> {
                try (var socket = server.accept();
                     var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                             socket.getInputStream(), StandardCharsets.US_ASCII));
                     var output = socket.getOutputStream()) {
                    while (true) {
                        String line = reader.readLine();
                        if (line == null || line.isEmpty()) break;
                    }
                    byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                            + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.write(body);
                    output.flush();
                } catch (Throwable error) {
                    serverFailure.set(error);
                }
            }, "update-fake-server");
            responder.start();
            String local = "http://127.0.0.1:" + server.getLocalPort() + "/release";
            UrlConnectionHttpTransport transport = transport(url ->
                    (HttpURLConnection) URI.create(url).toURL().openConnection());

            assertEquals("{\"ok\":true}", transport.get(local, 64));
            responder.join(2_000);
            assertFalse(responder.isAlive());
            if (serverFailure.get() != null) throw new AssertionError(serverFailure.get());
        }
    }

    private static UrlConnectionHttpTransport transport(
            UrlConnectionHttpTransport.ConnectionFactory factory) {
        return new UrlConnectionHttpTransport(TRUST, "test", factory);
    }

    private static final class FakeConnection extends HttpURLConnection {
        private final int response;
        private final String location;
        private final byte[] body;
        private final boolean timeout;

        FakeConnection(int response, String location, byte[] body, boolean timeout)
                throws Exception {
            super(new URL(FEED));
            this.response = response;
            this.location = location;
            this.body = body;
            this.timeout = timeout;
        }

        @Override public int getResponseCode() throws java.io.IOException {
            if (timeout) throw new SocketTimeoutException("timed out");
            return response;
        }
        @Override public String getHeaderField(String name) {
            return "Location".equalsIgnoreCase(name) ? location : null;
        }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
