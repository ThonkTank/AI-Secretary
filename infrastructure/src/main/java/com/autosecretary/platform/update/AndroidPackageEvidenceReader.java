package com.autosecretary.platform.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class AndroidPackageEvidenceReader {
    private final Context context;
    private final String packageName;

    AndroidPackageEvidenceReader(Context context, String packageName) {
        this.context = context.getApplicationContext();
        this.packageName = packageName;
    }

    UpdatePackageVerifier.PackageEvidence installed() throws Exception {
        PackageInfo info = packageInfo(packageName, null);
        var evidence = evidence(info);
        if (evidence == null || !packageName.equals(evidence.packageName())
                || evidence.signers().isEmpty()) {
            throw new SecurityException("Installiertes App-Paket ist nicht lesbar");
        }
        return evidence;
    }

    UpdatePackageVerifier.PackageEvidence archive(File apk) throws Exception {
        return evidence(packageInfo(null, apk));
    }

    private PackageInfo packageInfo(String installedPackage, File archive) throws Exception {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return archive == null ? manager.getPackageInfo(installedPackage, flags)
                : manager.getPackageArchiveInfo(archive.getAbsolutePath(), flags);
    }

    private static UpdatePackageVerifier.PackageEvidence evidence(PackageInfo info)
            throws Exception {
        if (info == null) return null;
        long version = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
        return new UpdatePackageVerifier.PackageEvidence(
                info.packageName, version, certificateDigests(info));
    }

    private static Set<String> certificateDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return Set.of();
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures == null) return result;
        for (Signature signature : signatures) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            result.add(hex(digest.digest(signature.toByteArray())));
        }
        return result;
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte part : value) result.append(String.format(Locale.ROOT, "%02x", part));
        return result.toString();
    }
}
