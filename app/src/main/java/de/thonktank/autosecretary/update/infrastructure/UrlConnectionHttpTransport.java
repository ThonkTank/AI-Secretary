package de.thonktank.autosecretary.update.infrastructure;

import de.thonktank.autosecretary.update.domain.UpdateFailure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.function.IntConsumer;

final class UrlConnectionHttpTransport implements HttpTransport {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    @Override public byte[] get(String url, int maxBytes) throws UpdateFailure {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            requireSuccess(connection);
            int declared = connection.getContentLength();
            if (declared > maxBytes) throw invalid("Response is too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copy(input, output, maxBytes, -1, null);
                return output.toByteArray();
            }
        } catch (UpdateFailure error) {
            throw error;
        } catch (IOException error) {
            throw network("Could not load update data", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override public void download(String url, File destination, long expectedBytes,
                                   long maxBytes, IntConsumer progress) throws UpdateFailure {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            requireSuccess(connection);
            long declared = connection.getContentLengthLong();
            if (declared > maxBytes || declared >= 0 && declared != expectedBytes)
                throw invalid("Unexpected APK size");
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination)) {
                long copied = copy(input, output, maxBytes, expectedBytes, progress);
                output.getFD().sync();
                if (copied != expectedBytes) throw invalid("Incomplete APK download");
            }
        } catch (UpdateFailure error) {
            throw error;
        } catch (IOException error) {
            throw network("Could not download update APK", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static HttpURLConnection open(String value) throws UpdateFailure, IOException {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Update URL is invalid", error);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()))
            throw invalid("Updates require HTTPS");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AutoSecretary-Android-Updater");
        return connection;
    }

    private static void requireSuccess(HttpURLConnection connection)
            throws UpdateFailure, IOException {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300)
            throw new UpdateFailure(UpdateFailure.Kind.HTTP, "HTTP " + status);
        if (!"https".equalsIgnoreCase(connection.getURL().getProtocol()))
            throw invalid("Update redirect left HTTPS");
    }

    private static long copy(InputStream input, java.io.OutputStream output, long maxBytes,
                             long expectedBytes, IntConsumer progress)
            throws IOException, UpdateFailure {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int lastProgress = -1;
        int read;
        while ((read = input.read(buffer)) != -1) {
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

    private static UpdateFailure network(String message, Throwable cause) {
        return new UpdateFailure(UpdateFailure.Kind.NETWORK, message, cause);
    }

    private static UpdateFailure invalid(String message) {
        return new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE, message);
    }
}
