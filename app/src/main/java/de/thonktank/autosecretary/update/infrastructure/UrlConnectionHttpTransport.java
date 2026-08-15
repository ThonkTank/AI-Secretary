package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateTrustPolicy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.util.function.IntConsumer;

/** HTTPS transport with bounded retries and explicit validation of every redirect hop. */
public final class UrlConnectionHttpTransport implements HttpTransport {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_REDIRECTS = 5;
    private static final long INITIAL_BACKOFF_MS = 250L;

    private final UpdateTrustPolicy trust;
    private final HttpConnectionFactory connections;
    private final BackoffSleeper sleeper;

    public UrlConnectionHttpTransport(UpdateTrustPolicy trust) {
        this(trust, url -> (HttpURLConnection) url.openConnection(), Thread::sleep);
    }

    UrlConnectionHttpTransport(UpdateTrustPolicy trust, HttpConnectionFactory connections,
                               BackoffSleeper sleeper) {
        if (trust == null || connections == null || sleeper == null)
            throw new IllegalArgumentException("HTTP transport dependencies are required");
        this.trust = trust;
        this.connections = connections;
        this.sleeper = sleeper;
    }

    @Override public byte[] get(String url, int maxBytes) throws UpdateFailure {
        return retry(() -> getOnce(url, maxBytes));
    }

    @Override public void download(String url, File destination, long expectedBytes,
                                   long maxBytes, IntConsumer progress) throws UpdateFailure {
        retry(() -> {
            downloadOnce(url, destination, expectedBytes, maxBytes, progress);
            return null;
        });
    }

    private byte[] getOnce(String url, int maxBytes)
            throws UpdateFailure, IOException, RetryableResponse {
        HttpURLConnection connection = openFollowingRedirects(url);
        try {
            requireSuccess(connection);
            int declared = connection.getContentLength();
            if (declared > maxBytes) throw invalid("Response is too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copy(input, output, maxBytes, -1, null);
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private void downloadOnce(String url, File destination, long expectedBytes,
                              long maxBytes, IntConsumer progress)
            throws UpdateFailure, IOException, RetryableResponse {
        HttpURLConnection connection = openFollowingRedirects(url);
        try {
            requireSuccess(connection);
            long declared = connection.getContentLengthLong();
            if (declared > maxBytes || declared >= 0 && declared != expectedBytes)
                throw invalid("Unexpected APK size");
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination, false)) {
                long copied = copy(input, output, maxBytes, expectedBytes, progress);
                output.getFD().sync();
                if (copied != expectedBytes) throw invalid("Incomplete APK download");
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openFollowingRedirects(String initial)
            throws UpdateFailure, IOException, RetryableResponse {
        URI current = trust.requireTrusted(initial);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            checkCancelled();
            HttpURLConnection connection = connections.open(current.toURL());
            configure(connection);
            final int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException | RuntimeException error) {
                connection.disconnect();
                throw error;
            }
            if (!isRedirect(status)) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.trim().isEmpty())
                throw invalid("Update redirect has no destination");
            if (redirects == MAX_REDIRECTS)
                throw invalid("Too many update redirects");
            try {
                current = trust.requireTrusted(current.resolve(location).toString());
            } catch (IllegalArgumentException error) {
                throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                        "Update redirect is invalid", error);
            }
        }
        throw invalid("Too many update redirects");
    }

    private static void configure(HttpURLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AutoSecretary-Android-Updater");
    }

    private static void requireSuccess(HttpURLConnection connection)
            throws UpdateFailure, IOException, RetryableResponse {
        int status = connection.getResponseCode();
        if (status >= 200 && status < 300) return;
        if (status == 429 || status == 403
                && "0".equals(connection.getHeaderField("X-RateLimit-Remaining")))
            throw new RetryableResponse(new UpdateFailure(UpdateFailure.Kind.RATE_LIMITED,
                    "GitHub update rate limit reached"));
        UpdateFailure failure = new UpdateFailure(UpdateFailure.Kind.HTTP, "HTTP " + status);
        if (status >= 500 && status <= 599) throw new RetryableResponse(failure);
        throw failure;
    }

    private <T> T retry(Request<T> request) throws UpdateFailure {
        UpdateFailure last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            checkCancelled();
            try {
                return request.run();
            } catch (RetryableResponse error) {
                last = error.failure;
            } catch (SocketTimeoutException error) {
                last = new UpdateFailure(UpdateFailure.Kind.TIMEOUT,
                        "Update request timed out", error);
            } catch (InterruptedIOException error) {
                Thread.currentThread().interrupt();
                throw cancelled(error);
            } catch (IOException error) {
                last = new UpdateFailure(UpdateFailure.Kind.NETWORK,
                        "Could not transfer update data", error);
            }
            if (attempt + 1 >= MAX_ATTEMPTS) throw last;
            backoff(attempt);
        }
        throw last == null ? new UpdateFailure(UpdateFailure.Kind.NETWORK,
                "Update request failed") : last;
    }

    private void backoff(int attempt) throws UpdateFailure {
        try {
            sleeper.sleep(INITIAL_BACKOFF_MS << attempt);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw cancelled(error);
        }
    }

    private static long copy(InputStream input, java.io.OutputStream output, long maxBytes,
                             long expectedBytes, IntConsumer progress)
            throws IOException, UpdateFailure {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int lastProgress = -1;
        int read;
        while ((read = input.read(buffer)) != -1) {
            checkCancelled();
            total += read;
            if (total > maxBytes) throw invalid("Download is too large");
            output.write(buffer, 0, read);
            if (progress != null && expectedBytes > 0) {
                int current = (int) Math.min(99, total * 100 / expectedBytes);
                if (current != lastProgress) {
                    progress.accept(current);
                    lastProgress = current;
                }
            }
        }
        return total;
    }

    private static void checkCancelled() throws UpdateFailure {
        if (Thread.currentThread().isInterrupted()) throw cancelled(null);
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303
                || status == 307 || status == 308;
    }

    private static UpdateFailure invalid(String message) {
        return new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE, message);
    }

    private static UpdateFailure cancelled(Throwable cause) {
        return new UpdateFailure(UpdateFailure.Kind.CANCELLED,
                "Update request was cancelled", cause);
    }

    private interface Request<T> {
        T run() throws UpdateFailure, IOException, RetryableResponse;
    }

    private static final class RetryableResponse extends Exception {
        final UpdateFailure failure;

        RetryableResponse(UpdateFailure failure) {
            this.failure = failure;
        }
    }
}
