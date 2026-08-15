package de.thonktank.autosecretary.update.domain;

import java.util.Locale;

/** Validated schema-v1 metadata from a production release. */
public final class ReleaseMetadata {
    public static final long MAX_APK_BYTES = 10L * 1024L * 1024L;

    public final long versionCode;
    public final String versionName;
    public final String packageName;
    public final String apkAsset;
    public final long apkSizeBytes;
    public final String sha256;
    public final String signerSha256;
    public final String commitSha;

    private ReleaseMetadata(long versionCode, String versionName, String packageName,
                            String apkAsset, long apkSizeBytes, String sha256,
                            String signerSha256, String commitSha) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.packageName = packageName;
        this.apkAsset = apkAsset;
        this.apkSizeBytes = apkSizeBytes;
        this.sha256 = sha256;
        this.signerSha256 = signerSha256;
        this.commitSha = commitSha;
    }

    public static ReleaseMetadata create(long versionCode, String versionName,
                                         String packageName, String apkAsset,
                                         long apkSizeBytes, String sha256,
                                         String signerSha256, String commitSha)
            throws UpdateFailure {
        if (versionCode <= 0 || apkSizeBytes <= 0 || apkSizeBytes > MAX_APK_BYTES)
            throw invalid("Invalid release version or size");
        String name = required(versionName, "versionName", 64);
        String packageId = required(packageName, "packageName", 128);
        String asset = required(apkAsset, "apkAsset", 128);
        String hash = digest(sha256, "sha256", 64);
        String signer = digest(signerSha256, "signerSha256", 64);
        String commit = digest(commitSha, "commitSha", 40);
        return new ReleaseMetadata(versionCode, name, packageId, asset, apkSizeBytes,
                hash, signer, commit);
    }

    private static String required(String value, String field, int maxLength)
            throws UpdateFailure {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength)
            throw invalid("Invalid " + field);
        return value;
    }

    private static String digest(String value, String field, int length)
            throws UpdateFailure {
        String normalized = required(value, field, length).toLowerCase(Locale.ROOT);
        if (normalized.length() != length || !normalized.matches("[0-9a-f]+"))
            throw invalid("Invalid " + field);
        return normalized;
    }

    private static UpdateFailure invalid(String message) {
        return new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE, message);
    }
}
