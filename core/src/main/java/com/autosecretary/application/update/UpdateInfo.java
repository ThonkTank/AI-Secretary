package com.autosecretary.application.update;

public record UpdateInfo(
        int versionCode,
        String versionName,
        String packageName,
        String apkUrl,
        long apkSizeBytes,
        String sha256,
        String signerSha256) {
    public UpdateInfo(
            int versionCode,
            String versionName,
            String packageName,
            String apkUrl,
            String sha256,
            String signerSha256) {
        this(versionCode, versionName, packageName, apkUrl, 0, sha256, signerSha256);
    }

    public UpdateInfo {
        if (versionCode < 1) throw new IllegalArgumentException("versionCode muss positiv sein");
        if (versionName == null || versionName.isBlank()) versionName = Integer.toString(versionCode);
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("Paket-ID fehlt");
        }
        java.net.URI source;
        try { source = java.net.URI.create(apkUrl); }
        catch (RuntimeException error) { throw new IllegalArgumentException("APK-URL ist ungültig", error); }
        if (!"https".equalsIgnoreCase(source.getScheme()) || source.getHost() == null) {
            throw new IllegalArgumentException("APK-URL muss HTTPS verwenden");
        }
        if (apkSizeBytes < 0 || apkSizeBytes > 80L * 1024L * 1024L) {
            throw new IllegalArgumentException("Ungültige APK-Größe");
        }
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Ungültiger APK-Hash");
        }
        sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
        if (signerSha256 == null || !signerSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Ungültiger Signaturfingerabdruck");
        }
        signerSha256 = signerSha256.toLowerCase(java.util.Locale.ROOT);
    }
}
