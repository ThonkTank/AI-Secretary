package de.thonktank.autosecretary.update.domain;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Explicit HTTPS host allowlist for release metadata, APKs and every redirect hop. */
public final class UpdateTrustPolicy {
    private final Set<String> allowedHosts;

    private UpdateTrustPolicy(Set<String> allowedHosts) {
        this.allowedHosts = Collections.unmodifiableSet(allowedHosts);
    }

    public static UpdateTrustPolicy github() {
        return allowing("api.github.com", "github.com",
                "release-assets.githubusercontent.com", "objects.githubusercontent.com",
                "github-releases.githubusercontent.com");
    }

    public static UpdateTrustPolicy allowing(String... hosts) {
        Set<String> normalized = new HashSet<>();
        if (hosts != null) for (String host : Arrays.asList(hosts)) {
            if (host == null || host.trim().isEmpty())
                throw new IllegalArgumentException("Trusted update host is required");
            normalized.add(host.toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty())
            throw new IllegalArgumentException("At least one trusted update host is required");
        return new UpdateTrustPolicy(normalized);
    }

    public URI requireTrusted(String value) throws UpdateFailure {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException error) {
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Update URL is invalid", error);
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443)
            throw new UpdateFailure(UpdateFailure.Kind.UNTRUSTED_HOST,
                    "Update URL is not trusted HTTPS");
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT)))
            throw new UpdateFailure(UpdateFailure.Kind.UNTRUSTED_HOST,
                    "Update host is not trusted: " + host);
        return uri;
    }
}
