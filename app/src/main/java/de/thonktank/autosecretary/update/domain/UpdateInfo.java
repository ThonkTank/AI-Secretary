package de.thonktank.autosecretary.update.domain;

import java.net.URI;

/** A complete update offer: validated release metadata plus its HTTPS APK location. */
public final class UpdateInfo {
    public final long versionCode;
    public final String versionName;
    public final long sizeBytes;
    private final ReleaseMetadata metadata;
    private final URI apkUri;

    private UpdateInfo(ReleaseMetadata metadata, URI apkUri) {
        this.versionCode = metadata.versionCode;
        this.versionName = metadata.versionName;
        this.sizeBytes = metadata.apkSizeBytes;
        this.metadata = metadata;
        this.apkUri = apkUri;
    }

    public static UpdateInfo from(ReleaseMetadata metadata, String apkUrl)
            throws UpdateFailure {
        if (metadata == null || apkUrl == null)
            throw invalid("Update release contract is incomplete", null);
        try {
            URI uri = URI.create(apkUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null)
                throw invalid("Update APK location is not a valid HTTPS URL", null);
            return new UpdateInfo(metadata, uri);
        } catch (IllegalArgumentException error) {
            throw invalid("Update APK location is invalid", error);
        }
    }

    public ReleaseMetadata metadata() {
        return metadata;
    }

    public URI apkUri() {
        return apkUri;
    }

    private static UpdateFailure invalid(String message, Throwable cause) {
        return new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE, message, cause);
    }
}
