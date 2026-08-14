package com.autosecretary.platform.update;

import java.net.URI;

/** Structured trust boundary for the public GitHub feed and its CDN redirects. */
final class UrlTrustPolicy {
    private final String owner;
    private final String repository;

    UrlTrustPolicy(String owner, String repository) {
        this.owner = owner;
        this.repository = repository;
    }

    void requireFeed(String value) {
        URI uri = https(value);
        String expected = "/repos/" + owner + "/" + repository + "/releases/latest";
        if (!"api.github.com".equalsIgnoreCase(uri.getHost())
                || !expected.equals(uri.getPath())) {
            throw new SecurityException("Update-Feed stammt nicht aus diesem Repository");
        }
    }

    void requireReleaseAsset(String value, String asset) {
        URI uri = https(value);
        String prefix = "/" + owner + "/" + repository + "/releases/download/";
        if (!"github.com".equalsIgnoreCase(uri.getHost())
                || !uri.getPath().startsWith(prefix)
                || !uri.getPath().endsWith("/" + asset)) {
            throw new SecurityException("Release-Asset stammt nicht aus diesem Repository");
        }
    }

    void requireRedirect(String value) {
        URI uri = https(value);
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!(host.equals("github.com") || host.equals("api.github.com")
                || host.endsWith(".githubusercontent.com"))) {
            throw new SecurityException("Update-Weiterleitung verlässt GitHub");
        }
    }

    private static URI https(String value) {
        URI uri;
        try { uri = URI.create(value); }
        catch (RuntimeException error) { throw new SecurityException("Update-URL ist ungültig", error); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new SecurityException("Update-URL ist nicht sicher");
        }
        return uri;
    }
}
