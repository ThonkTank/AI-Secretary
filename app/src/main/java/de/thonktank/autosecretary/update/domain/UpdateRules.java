package de.thonktank.autosecretary.update.domain;

import java.util.Locale;

/** Pure compatibility rules shared by release discovery and APK verification. */
public final class UpdateRules {
    private UpdateRules() { }

    public static void requireAvailable(String expectedPackage, String expectedApkAsset,
                                        long taggedVersion, String targetCommit,
                                        ReleaseMetadata metadata, PackageEvidence installed)
            throws UpdateFailure {
        if (metadata.versionCode != taggedVersion
                || !expectedPackage.equals(metadata.packageName)
                || !expectedApkAsset.equals(metadata.apkAsset)
                || metadata.versionCode <= installed.versionCode)
            throw new UpdateFailure(UpdateFailure.Kind.INCOMPATIBLE_RELEASE,
                    "Release metadata is incompatible");
        if (!installed.signers.contains(metadata.signerSha256))
            throw new UpdateFailure(UpdateFailure.Kind.SIGNATURE_MISMATCH,
                    "Release signer is incompatible");
        String normalizedTarget = targetCommit == null
                ? "" : targetCommit.toLowerCase(Locale.ROOT);
        if (!metadata.commitSha.equals(normalizedTarget))
            throw new UpdateFailure(UpdateFailure.Kind.INVALID_RELEASE,
                    "Release commit does not match metadata");
    }

    public static void requireDownloaded(String expectedPackage, UpdateInfo update,
                                         PackageEvidence installed, PackageEvidence archive)
            throws UpdateFailure {
        ReleaseMetadata metadata = update.metadata();
        if (!expectedPackage.equals(metadata.packageName)
                || !expectedPackage.equals(archive.packageName)
                || archive.versionCode != update.versionCode
                || update.versionCode <= installed.versionCode)
            throw new UpdateFailure(UpdateFailure.Kind.PACKAGE_MISMATCH,
                    "APK package or version is incompatible");
        if (!archive.signers.contains(metadata.signerSha256)
                || !installed.signers.contains(metadata.signerSha256))
            throw new UpdateFailure(UpdateFailure.Kind.SIGNATURE_MISMATCH,
                    "APK signer is incompatible");
    }
}
