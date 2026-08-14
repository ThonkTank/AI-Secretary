package com.autosecretary.platform.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GitHubReleaseUpdaterTest {
    private static final String PACKAGE = "com.autosecretary";
    private static final String SIGNER = "a".repeat(64);

    @Test
    public void acceptsTheOneCanonicalLatestRelease() {
        GitHubReleaseUpdater.ReleaseMetadata latest = candidate(2001001, PACKAGE, SIGNER);

        GitHubReleaseUpdater.validateLatest(
                "android-2001001", latest, PACKAGE, Set.of(SIGNER));
    }

    @Test
    public void rejectsTagVersionMismatch() {
        var latest = candidate(2001001, PACKAGE, SIGNER);

        assertThrows(SecurityException.class, () -> GitHubReleaseUpdater.validateLatest(
                "android-2001000", latest, PACKAGE, Set.of(SIGNER)));
    }

    @Test
    public void rejectsForeignPackage() {
        var latest = candidate(2001001, "example.foreign", SIGNER);

        assertThrows(SecurityException.class, () -> GitHubReleaseUpdater.validateLatest(
                "android-2001001", latest, PACKAGE, Set.of(SIGNER)));
    }

    @Test
    public void rejectsForeignSignature() {
        var latest = candidate(2001001, PACKAGE, "b".repeat(64));

        assertThrows(SecurityException.class, () -> GitHubReleaseUpdater.validateLatest(
                "android-2001001", latest, PACKAGE, Set.of(SIGNER)));
    }

    @Test
    public void readsTheBackwardCompatibleMetadataPublishedForInstalledApps() throws Exception {
        JSONObject metadata = new JSONObject()
                .put("versionCode", 2001001)
                .put("versionName", "2.0.10.1")
                .put("packageName", PACKAGE)
                .put("apkAsset", "AutoSecretary.apk")
                .put("sha256", "c".repeat(64))
                .put("signerSha256", SIGNER);

        var parsed = GitHubReleaseUpdater.ReleaseMetadata.from(metadata,
                "https://github.com/ThonkTank/AI-Secretary/releases/download/"
                        + "android-2001001/AutoSecretary.apk");

        assertEquals(2001001, parsed.versionCode());
        assertEquals(PACKAGE, parsed.packageName());
        assertEquals(SIGNER, parsed.signerSha256());
    }

    private static GitHubReleaseUpdater.ReleaseMetadata candidate(
            int code, String packageName, String signer) {
        return new GitHubReleaseUpdater.ReleaseMetadata(code, "2.0.10.1", packageName,
                "https://github.com/ThonkTank/AI-Secretary/releases/download/android-"
                        + code + "/AutoSecretary.apk",
                "c".repeat(64), signer);
    }
}
