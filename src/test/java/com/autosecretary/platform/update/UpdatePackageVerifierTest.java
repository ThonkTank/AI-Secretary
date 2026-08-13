package com.autosecretary.platform.update;

import static org.junit.Assert.assertThrows;

import com.autosecretary.application.update.UpdateInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Set;

public final class UpdatePackageVerifierTest {
    private static final String SIGNER = "a".repeat(64);
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptsOnlyMatchingNewerPackageHashAndSigner() throws Exception {
        File apk = apk();
        UpdateInfo update = update(apk, 6, "com.autosecretary", SIGNER);
        UpdatePackageVerifier.verify(apk, update, 5, "com.autosecretary",
                evidence("com.autosecretary", 5, SIGNER),
                evidence("com.autosecretary", 6, SIGNER));
    }

    @Test
    public void rejectsHashMismatch() throws Exception {
        File apk = apk();
        UpdateInfo update = new UpdateInfo(6, "2.0.1", "com.autosecretary",
                url(), "0".repeat(64), SIGNER, true);
        assertThrows(SecurityException.class, () -> verify(apk, update,
                evidence("com.autosecretary", 6, SIGNER)));
    }

    @Test
    public void rejectsForeignPackage() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk,
                update(apk, 6, "com.autosecretary", SIGNER),
                evidence("example.foreign", 6, SIGNER)));
    }

    @Test
    public void rejectsForeignPackageInReleaseMetadata() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk,
                update(apk, 6, "example.foreign", SIGNER),
                evidence("com.autosecretary", 6, SIGNER)));
    }

    @Test
    public void rejectsSignerMismatch() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk,
                update(apk, 6, "com.autosecretary", "b".repeat(64)),
                evidence("com.autosecretary", 6, "b".repeat(64))));
    }

    @Test
    public void rejectsNonNewerOrMetadataVersionMismatch() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk,
                update(apk, 6, "com.autosecretary", SIGNER),
                evidence("com.autosecretary", 5, SIGNER)));
    }

    private void verify(File apk, UpdateInfo update,
                        UpdatePackageVerifier.PackageEvidence archive) throws Exception {
        UpdatePackageVerifier.verify(apk, update, 5, "com.autosecretary",
                evidence("com.autosecretary", 5, SIGNER), archive);
    }

    private File apk() throws Exception {
        File result = temporary.newFile("update.apk");
        try (var output = new java.io.FileOutputStream(result)) {
            output.write("signed-apk-test-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
    }

    private static UpdateInfo update(
            File apk, int version, String packageName, String signer) throws Exception {
        return new UpdateInfo(version, "2.0.1", packageName, url(),
                UpdatePackageVerifier.sha256(apk), signer, true);
    }

    private static String url() {
        return "https://github.com/ThonkTank/AI-Secretary/releases/download/android-6/AutoSecretary.apk";
    }

    private static UpdatePackageVerifier.PackageEvidence evidence(
            String packageName, long version, String signer) {
        return new UpdatePackageVerifier.PackageEvidence(packageName, version, Set.of(signer));
    }
}
