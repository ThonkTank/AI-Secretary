package de.thonktank.autosecretary.update.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Package identity extracted from an installed app or downloaded APK. */
public final class PackageEvidence {
    public final String packageName;
    public final long versionCode;
    public final Set<String> signers;

    private PackageEvidence(String packageName, long versionCode, Set<String> signers) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.signers = Collections.unmodifiableSet(new LinkedHashSet<>(signers));
    }

    public static PackageEvidence of(String packageName, long versionCode, Set<String> signers)
            throws UpdateFailure {
        if (packageName == null || packageName.trim().isEmpty() || versionCode <= 0)
            throw new UpdateFailure(UpdateFailure.Kind.PACKAGE_MISMATCH,
                    "Package identity is incomplete");
        if (signers == null || signers.isEmpty() || signers.stream().anyMatch(
                signer -> signer == null || !signer.matches("[0-9a-f]{64}")))
            throw new UpdateFailure(UpdateFailure.Kind.SIGNATURE_MISMATCH,
                    "Package signer evidence is incomplete");
        return new PackageEvidence(packageName, versionCode, signers);
    }
}
