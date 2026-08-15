package com.autosecretary.platform.update;

import com.autosecretary.application.update.UpdateInfo;

import java.util.Locale;

record ReleaseMetadata(
        int schemaVersion,
        int versionCode,
        String versionName,
        String packageName,
        String apkUrl,
        long apkSizeBytes,
        String sha256,
        String signerSha256,
        String commitSha) {
    ReleaseMetadata {
        if (schemaVersion < 0 || schemaVersion > 1) {
            throw new IllegalArgumentException("Metadaten-Schema wird nicht unterstützt");
        }
        if (versionCode < 1 || versionName == null || versionName.isBlank()) {
            throw new IllegalArgumentException("Release-Version fehlt");
        }
        if (packageName == null || packageName.isBlank()) {
            throw new IllegalArgumentException("Paket-ID fehlt");
        }
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("APK-Hash ist ungültig");
        }
        if (signerSha256 == null || !signerSha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Signaturfingerabdruck ist ungültig");
        }
        if (schemaVersion == 1 && (apkSizeBytes < 1
                || apkSizeBytes >= 80L * 1024L * 1024L)) {
            throw new IllegalArgumentException("APK-Größe fehlt");
        }
        if (schemaVersion == 1
                && (commitSha == null || !commitSha.matches("[0-9a-fA-F]{40}"))) {
            throw new IllegalArgumentException("Release-Commit fehlt");
        }
        sha256 = sha256.toLowerCase(Locale.ROOT);
        signerSha256 = signerSha256.toLowerCase(Locale.ROOT);
        commitSha = commitSha == null ? "" : commitSha.toLowerCase(Locale.ROOT);
    }

    UpdateInfo toUpdateInfo() {
        return new UpdateInfo(versionCode, versionName, packageName, apkUrl,
                apkSizeBytes, sha256, signerSha256);
    }
}
