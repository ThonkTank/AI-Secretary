package com.autosecretary.platform.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class GitHubReleaseUpdaterTest {
    private static final String PACKAGE = "com.autosecretary";
    private static final String SIGNER = "a".repeat(64);

    @Test
    public void selectsHighestVersionCodeInsteadOfReleaseOrder() {
        var selected = select(List.of(candidate(11, false), candidate(14, false),
                candidate(12, false)), 10);

        assertEquals(14, selected.versionCode());
    }

    @Test
    public void prereleaseIsEligibleDuringTheTestPhase() {
        var selected = select(List.of(candidate(11, false), candidate(12, true)), 10);

        assertEquals(12, selected.versionCode());
        assertTrue(selected.prerelease());
    }

    @Test
    public void lowerAndEqualVersionsAreIgnored() {
        assertNull(select(List.of(candidate(9, true), candidate(10, false)), 10));
    }

    @Test
    public void foreignPackageIsIgnored() {
        var foreign = candidate(12, true, "example.foreign", SIGNER);

        assertNull(select(List.of(foreign), 10));
    }

    @Test
    public void foreignSignatureIsIgnored() {
        var foreign = candidate(12, true, PACKAGE, "b".repeat(64));

        assertNull(select(List.of(foreign), 10));
    }

    private static GitHubReleaseUpdater.ReleaseMetadata select(
            List<GitHubReleaseUpdater.ReleaseMetadata> releases, int installed) {
        return GitHubReleaseUpdater.selectHighestCompatible(
                releases, installed, PACKAGE, Set.of(SIGNER));
    }

    private static GitHubReleaseUpdater.ReleaseMetadata candidate(int code, boolean prerelease) {
        return candidate(code, prerelease, PACKAGE, SIGNER);
    }

    private static GitHubReleaseUpdater.ReleaseMetadata candidate(
            int code, boolean prerelease, String packageName, String signer) {
        return new GitHubReleaseUpdater.ReleaseMetadata(code, "2.0." + code, packageName,
                "https://github.com/ThonkTank/AI-Secretary/releases/download/android-"
                        + code + "/AutoSecretary.apk",
                "c".repeat(64), signer, prerelease);
    }
}
