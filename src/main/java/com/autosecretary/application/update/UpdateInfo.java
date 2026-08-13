package com.autosecretary.application.update;

public record UpdateInfo(
        int versionCode,
        String versionName,
        String apkUrl,
        String sha256) {
    public UpdateInfo {
        if (versionCode < 1) throw new IllegalArgumentException("versionCode muss positiv sein");
        if (versionName == null || versionName.isBlank()) versionName = Integer.toString(versionCode);
        if (apkUrl == null
                || !apkUrl.startsWith("https://github.com/ThonkTank/AI-Secretary/")) {
            throw new IllegalArgumentException("APK-URL muss aus dem Produktions-Repository stammen");
        }
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Ungültiger APK-Hash");
        }
        sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
    }
}
