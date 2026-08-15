package de.thonktank.autosecretary.update;

import org.json.JSONObject;

import java.util.Locale;

final class ReleaseMetadata {
    static final long MAX_APK_BYTES = 10L * 1024L * 1024L;

    final long versionCode;
    final String versionName;
    final String packageName;
    final String apkAsset;
    final long apkSizeBytes;
    final String sha256;
    final String signerSha256;
    final String commitSha;

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

    static ReleaseMetadata parse(String json) throws Exception {
        JSONObject source = new JSONObject(json);
        if (source.getInt("schemaVersion") != 1)
            throw new SecurityException("Unsupported release metadata schema");
        long versionCode = source.getLong("versionCode");
        long size = source.getLong("apkSizeBytes");
        String versionName = required(source, "versionName", 64);
        String packageName = required(source, "packageName", 128);
        String apkAsset = required(source, "apkAsset", 128);
        String sha = required(source, "sha256", 64).toLowerCase(Locale.ROOT);
        String signer = required(source, "signerSha256", 64).toLowerCase(Locale.ROOT);
        String commit = required(source, "commitSha", 40).toLowerCase(Locale.ROOT);
        if (versionCode <= 0 || size <= 0 || size > MAX_APK_BYTES)
            throw new SecurityException("Invalid release version or size");
        if (!sha.matches("[0-9a-f]{64}") || !signer.matches("[0-9a-f]{64}")
                || !commit.matches("[0-9a-f]{40}"))
            throw new SecurityException("Invalid release digest");
        return new ReleaseMetadata(versionCode, versionName, packageName, apkAsset,
                size, sha, signer, commit);
    }

    private static String required(JSONObject source, String key, int maxLength) throws Exception {
        String value = source.getString(key);
        if (value.isEmpty() || value.length() > maxLength) throw new SecurityException("Invalid " + key);
        return value;
    }
}
