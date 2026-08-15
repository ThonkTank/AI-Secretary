package de.thonktank.autosecretary.update.infrastructure;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import de.thonktank.autosecretary.update.domain.PackageEvidence;
import de.thonktank.autosecretary.update.domain.UpdateFailure;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    @Override public PackageEvidence installed() throws UpdateFailure {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        try {
            return evidence(packages.getPackageInfo(context.getPackageName(), flags));
        } catch (PackageManager.NameNotFoundException error) {
            throw new UpdateFailure(UpdateFailure.Kind.PACKAGE_MISMATCH,
                    "Installed package could not be inspected", error);
        }
    }

    @SuppressWarnings("deprecation")
    @Override public PackageEvidence archive(File apk) throws UpdateFailure {
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo info = packages.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (info == null)
            throw new UpdateFailure(UpdateFailure.Kind.PACKAGE_MISMATCH,
                    "Downloaded file is not an APK");
        return evidence(info);
    }

    @SuppressWarnings("deprecation")
    private static PackageEvidence evidence(PackageInfo info) throws UpdateFailure {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28) {
            if (info.signingInfo == null)
                throw new UpdateFailure(UpdateFailure.Kind.SIGNATURE_MISMATCH,
                        "APK has no signing info");
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0)
            throw new UpdateFailure(UpdateFailure.Kind.SIGNATURE_MISMATCH,
                    "APK has no signer");
        Set<String> signers = new LinkedHashSet<>();
        for (Signature signature : signatures) signers.add(sha256(signature.toByteArray()));
        long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return PackageEvidence.of(info.packageName, version, signers);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest)
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Android runtime does not provide SHA-256", error);
        }
    }
}
