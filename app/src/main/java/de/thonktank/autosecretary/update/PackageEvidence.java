package de.thonktank.autosecretary.update;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class PackageEvidence {
    final String packageName;
    final long versionCode;
    final Set<String> signers;

    PackageEvidence(String packageName, long versionCode, Set<String> signers) {
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.signers = Collections.unmodifiableSet(new LinkedHashSet<>(signers));
    }
}
