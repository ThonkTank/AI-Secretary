package de.thonktank.autosecretary.update;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;

final class UrlConnectionHttpTransport implements HttpTransport {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    @Override public byte[] get(String url, int maxBytes) throws Exception {
        HttpURLConnection connection = open(url);
        try {
            requireSuccess(connection);
            int declared = connection.getContentLength();
            if (declared > maxBytes) throw new SecurityException("Response is too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copy(input, output, maxBytes, -1, null);
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    @Override public void download(String url, File destination, long expectedBytes,
                                   long maxBytes, IntConsumer progress) throws Exception {
        HttpURLConnection connection = open(url);
        try {
            requireSuccess(connection);
            long declared = connection.getContentLengthLong();
            if (declared > maxBytes || declared >= 0 && declared != expectedBytes)
                throw new SecurityException("Unexpected APK size");
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(destination)) {
                long copied = copy(input, output, maxBytes, expectedBytes, progress);
                output.getFD().sync();
                if (copied != expectedBytes) throw new SecurityException("Incomplete APK download");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String value) throws Exception {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()))
            throw new SecurityException("Updates require HTTPS");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AutoSecretary-Android-Updater");
        return connection;
    }

    private static void requireSuccess(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new java.io.IOException("HTTP " + status);
        if (!"https".equalsIgnoreCase(connection.getURL().getProtocol()))
            throw new SecurityException("Update redirect left HTTPS");
    }

    private static long copy(InputStream input, java.io.OutputStream output, long maxBytes,
                             long expectedBytes, IntConsumer progress) throws Exception {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int lastProgress = -1;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new SecurityException("Download is too large");
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
}
