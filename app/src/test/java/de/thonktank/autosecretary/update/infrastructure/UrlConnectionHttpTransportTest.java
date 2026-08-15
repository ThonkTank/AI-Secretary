package de.thonktank.autosecretary.update.infrastructure;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateTrustPolicy;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UrlConnectionHttpTransportTest {
    private static final String API = "https://api.github.com/releases";
    private static final String ASSET = "https://release-assets.githubusercontent.com/app.apk";

    @Test public void trustedRedirectIsFollowedAndEveryHopIsValidated() throws Exception {
        FakeConnections connections = new FakeConnections(
                Response.status(302).header("Location", ASSET),
                Response.ok("release"));
        RecordingSleeper sleeper = new RecordingSleeper();
        UrlConnectionHttpTransport transport = transport(connections, sleeper);

        assertArrayEquals("release".getBytes(StandardCharsets.UTF_8),
                transport.get(API, 100));
        assertEquals(List.of(API, ASSET), connections.urls);
        assertTrue(connections.allDisconnected());
        assertTrue(sleeper.delays.isEmpty());

        FakeConnections hostile = new FakeConnections(Response.status(302)
                .header("Location", "https://github.example/file.apk"));
        assertFailure(UpdateFailure.Kind.UNTRUSTED_HOST,
                () -> transport(hostile, new RecordingSleeper()).get(API, 100));
        assertEquals(1, hostile.urls.size());
        assertTrue(hostile.allDisconnected());
    }

    @Test public void httpAndRateLimitResponsesHaveTypedRetryBehavior() throws Exception {
        FakeConnections notFound = new FakeConnections(Response.status(404));
        RecordingSleeper noRetry = new RecordingSleeper();
        assertFailure(UpdateFailure.Kind.HTTP,
                () -> transport(notFound, noRetry).get(API, 100));
        assertEquals(1, notFound.urls.size());
        assertTrue(noRetry.delays.isEmpty());

        FakeConnections limited = new FakeConnections(
                Response.status(429), Response.ok("available"));
        RecordingSleeper retried = new RecordingSleeper();
        assertArrayEquals("available".getBytes(StandardCharsets.UTF_8),
                transport(limited, retried).get(API, 100));
        assertEquals(List.of(250L), retried.delays);
        assertEquals(2, limited.urls.size());

        FakeConnections githubLimit = new FakeConnections(
                Response.status(403).header("X-RateLimit-Remaining", "0"),
                Response.ok("available"));
        RecordingSleeper githubRetry = new RecordingSleeper();
        transport(githubLimit, githubRetry).get(API, 100);
        assertEquals(List.of(250L), githubRetry.delays);
    }

    @Test public void timeoutsAreBoundedRetriedAndTyped() throws Exception {
        FakeConnections connections = new FakeConnections(
                Response.timeout(), Response.timeout(), Response.timeout());
        RecordingSleeper sleeper = new RecordingSleeper();

        assertFailure(UpdateFailure.Kind.TIMEOUT,
                () -> transport(connections, sleeper).get(API, 100));

        assertEquals(3, connections.urls.size());
        assertEquals(List.of(250L, 500L), sleeper.delays);
        assertTrue(connections.allDisconnected());
    }

    @Test public void declaredAndStreamingOversizeResponsesAreRejected() throws Exception {
        FakeConnections declared = new FakeConnections(Response.ok("too large").length(9));
        assertFailure(UpdateFailure.Kind.INVALID_RELEASE,
                () -> transport(declared, new RecordingSleeper()).get(API, 4));

        FakeConnections streamed = new FakeConnections(Response.ok("too large").length(-1));
        assertFailure(UpdateFailure.Kind.INVALID_RELEASE,
                () -> transport(streamed, new RecordingSleeper()).get(API, 4));
    }

    @Test public void downloadIsExactAndInterruptionCancelsBeforeNetwork() throws Exception {
        File directory = Files.createTempDirectory("update-http-test").toFile();
        File destination = new File(directory, "candidate.partial");
        FakeConnections connections = new FakeConnections(Response.ok("apk").length(3));
        List<Integer> progress = new ArrayList<>();

        transport(connections, new RecordingSleeper())
                .download(ASSET, destination, 3, 10, progress::add);

        assertEquals("apk", new String(Files.readAllBytes(destination.toPath()),
                StandardCharsets.UTF_8));
        assertEquals(List.of(99), progress);

        FakeConnections cancelled = new FakeConnections(Response.ok("unused"));
        Thread.currentThread().interrupt();
        try {
            assertFailure(UpdateFailure.Kind.CANCELLED,
                    () -> transport(cancelled, new RecordingSleeper()).get(API, 100));
            assertTrue(cancelled.urls.isEmpty());
        } finally {
            Thread.interrupted();
            destination.delete();
            directory.delete();
        }
    }

    private static UrlConnectionHttpTransport transport(FakeConnections connections,
                                                        RecordingSleeper sleeper) {
        return new UrlConnectionHttpTransport(UpdateTrustPolicy.github(), connections, sleeper);
    }

    private static void assertFailure(UpdateFailure.Kind expected, FailingAction action)
            throws Exception {
        try {
            action.run();
            fail("Expected update failure " + expected);
        } catch (UpdateFailure error) {
            assertEquals(expected, error.kind());
        }
    }

    private static final class RecordingSleeper implements BackoffSleeper {
        final List<Long> delays = new ArrayList<>();
        @Override public void sleep(long millis) { delays.add(millis); }
    }

    private static final class FakeConnections implements HttpConnectionFactory {
        final Deque<Response> responses = new ArrayDeque<>();
        final List<String> urls = new ArrayList<>();
        final List<FakeConnection> opened = new ArrayList<>();

        FakeConnections(Response... responses) {
            for (Response response : responses) this.responses.add(response);
        }

        @Override public HttpURLConnection open(URL url) {
            if (responses.isEmpty()) throw new AssertionError("Unexpected request " + url);
            urls.add(url.toString());
            FakeConnection connection = new FakeConnection(url, responses.remove());
            opened.add(connection);
            return connection;
        }

        boolean allDisconnected() {
            return !opened.isEmpty() && opened.stream().allMatch(item -> item.disconnected);
        }
    }

    private static final class Response {
        int status;
        byte[] body = new byte[0];
        long length = 0;
        IOException responseFailure;
        final Map<String, String> headers = new HashMap<>();

        static Response status(int status) {
            Response response = new Response();
            response.status = status;
            return response;
        }

        static Response ok(String body) {
            Response response = status(200);
            response.body = body.getBytes(StandardCharsets.UTF_8);
            response.length = response.body.length;
            return response;
        }

        static Response timeout() {
            Response response = status(0);
            response.responseFailure = new SocketTimeoutException("timeout");
            return response;
        }

        Response header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        Response length(long value) {
            length = value;
            return this;
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final Response response;
        boolean disconnected;

        FakeConnection(URL url, Response response) {
            super(url);
            this.response = response;
        }

        @Override public int getResponseCode() throws IOException {
            if (response.responseFailure != null) throw response.responseFailure;
            return response.status;
        }

        @Override public String getHeaderField(String name) {
            return response.headers.get(name);
        }

        @Override public int getContentLength() { return (int) response.length; }
        @Override public long getContentLengthLong() { return response.length; }
        @Override public InputStream getInputStream() {
            return new ByteArrayInputStream(response.body);
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }

    @FunctionalInterface private interface FailingAction {
        void run() throws Exception;
    }
}
