package de.thonktank.autosecretary.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class AndroidPackageEvidenceReader implements PackageEvidenceReader {
    private final Context context;
    private final PackageManager packages;

    AndroidPackageEvidenceReader(Context context) {
        this.context = context.getApplicationContext();
        this.packages = context.getPackageManager();
    }

    @SuppressWarnings("deprecation")
    @Override public PackageEvidence installed() throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return evidence(packages.getPackageInfo(context.getPackageName(), flags));
    }

    @SuppressWarnings("deprecation")
    @Override public PackageEvidence archive(File apk) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = packages.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null) throw new SecurityException("Downloaded file is not an APK");
        return evidence(info);
    }

    @SuppressWarnings("deprecation")
    private static PackageEvidence evidence(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null) throw new SecurityException("APK has no signing info");
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0)
            throw new SecurityException("APK has no signer");
        Set<String> signers = new LinkedHashSet<>();
        for (Signature signature : signatures) signers.add(sha256(signature.toByteArray()));
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return new PackageEvidence(info.packageName, version, signers);
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder(64);
        for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return result.toString();
    }
}
