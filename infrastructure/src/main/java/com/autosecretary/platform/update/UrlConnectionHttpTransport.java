package com.autosecretary.platform.update;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class UrlConnectionHttpTransport implements HttpTransport {
    private static final int MAX_REDIRECTS = 5;
    private final UrlTrustPolicy trust;
    private final String userAgent;

    UrlConnectionHttpTransport(UrlTrustPolicy trust, String appVersionName) {
        this.trust = trust;
        this.userAgent = "AutoSecretary/" + appVersionName;
    }

    @Override public String get(String url, int byteLimit) throws Exception {
        String current = url;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (redirects > 0) trust.requireRedirect(current);
            HttpURLConnection connection = (HttpURLConnection) URI.create(current)
                    .toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", userAgent);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String target = connection.getHeaderField("Location");
                connection.disconnect();
                if (target == null || redirects == MAX_REDIRECTS) {
                    throw new IllegalStateException("Update-Weiterleitung ist ungültig");
                }
                current = URI.create(current).resolve(target).toString();
                continue;
            }
            if (status == 403 || status == 429) {
                connection.disconnect();
                throw new IllegalStateException("Update-Server-Rate-Limit: " + status);
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IllegalStateException("Update-Server antwortet mit " + status);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > byteLimit) {
                        throw new IllegalStateException("Update-Antwort ist zu groß");
                    }
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                connection.disconnect();
            }
        }
        throw new IllegalStateException("Zu viele Update-Weiterleitungen");
    }
}
