package com.autosecretary.platform.update;

import com.autosecretary.application.update.UpdateInfo;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;

/** Pure APK validation policy separated from Android package/archive inspection. */
public final class UpdateArtifactVerifier {
    public record PackageEvidence(String packageName, long versionCode, Set<String> signers) {
        public PackageEvidence {
            signers = Set.copyOf(signers == null ? Set.of() : signers);
        }
    }

    private UpdateArtifactVerifier() { }

    public static void verify(
            File apk,
            UpdateInfo update,
            long installedVersion,
            String expectedPackage,
            PackageEvidence installed,
            PackageEvidence archive) throws Exception {
        if (apk == null || !apk.isFile() || apk.length() <= 0) {
            throw new SecurityException("Update-Paket fehlt");
        }
        if (!sha256(apk).equals(update.sha256())) {
            throw new SecurityException("APK-Prüfsumme stimmt nicht");
        }
        if (!expectedPackage.equals(update.packageName())) {
            throw new SecurityException("Freigabe gehört nicht zu dieser App");
        }
        if (archive == null || !expectedPackage.equals(archive.packageName())) {
            throw new SecurityException("APK gehört nicht zu dieser App");
        }
        if (archive.versionCode() != update.versionCode()
                || archive.versionCode() <= installedVersion) {
            throw new SecurityException("APK-Version stimmt nicht mit der Freigabe überein");
        }
        if (installed == null || !expectedPackage.equals(installed.packageName())
                || installed.signers().isEmpty()
                || !installed.signers().contains(update.signerSha256())
                || !installed.signers().equals(archive.signers())) {
            throw new SecurityException("APK-Signatur stimmt nicht mit der installierten App überein");
        }
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte part : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", part));
        }
        return result.toString();
    }
}
