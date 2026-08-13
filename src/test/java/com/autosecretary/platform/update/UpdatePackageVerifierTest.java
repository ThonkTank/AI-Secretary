package com.autosecretary.platform.update;

import static org.junit.Assert.assertThrows;

import com.autosecretary.application.update.UpdateInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Set;

public final class UpdatePackageVerifierTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void acceptsOnlyMatchingNewerPackageHashAndSigner() throws Exception {
        File apk = apk();
        UpdateInfo update = update(apk, 6);
        UpdatePackageVerifier.verify(apk, update, 5, "com.autosecretary",
                evidence("com.autosecretary", 5, "signer"),
                evidence("com.autosecretary", 6, "signer"));
    }

    @Test
    public void rejectsHashMismatch() throws Exception {
        File apk = apk();
        UpdateInfo update = new UpdateInfo(6, "2.0.1",
                "https://github.com/ThonkTank/AI-Secretary/releases/a.apk", "0".repeat(64));
        assertThrows(SecurityException.class, () -> verify(apk, update,
                evidence("com.autosecretary", 6, "signer")));
    }

    @Test
    public void rejectsForeignPackage() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk, update(apk, 6),
                evidence("example.foreign", 6, "signer")));
    }

    @Test
    public void rejectsForeignInstalledEvidenceEvenWithMatchingSigner() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> UpdatePackageVerifier.verify(
                apk, update(apk, 6), 5, "com.autosecretary",
                evidence("example.foreign", 5, "signer"),
                evidence("com.autosecretary", 6, "signer")));
    }

    @Test
    public void rejectsSignerMismatch() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk, update(apk, 6),
                evidence("com.autosecretary", 6, "other")));
    }

    @Test
    public void rejectsNonNewerOrMetadataVersionMismatch() throws Exception {
        File apk = apk();
        assertThrows(SecurityException.class, () -> verify(apk, update(apk, 6),
                evidence("com.autosecretary", 5, "signer")));
    }

    private void verify(File apk, UpdateInfo update,
                        UpdatePackageVerifier.PackageEvidence archive) throws Exception {
        UpdatePackageVerifier.verify(apk, update, 5, "com.autosecretary",
                evidence("com.autosecretary", 5, "signer"), archive);
    }

    private File apk() throws Exception {
        File result = temporary.newFile("update.apk");
        try (var output = new java.io.FileOutputStream(result)) {
            output.write("signed-apk-test-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
    }

    private static UpdateInfo update(File apk, int version) throws Exception {
        return new UpdateInfo(version, "2.0.1",
                "https://github.com/ThonkTank/AI-Secretary/releases/a.apk",
                UpdatePackageVerifier.sha256(apk));
    }

    private static UpdatePackageVerifier.PackageEvidence evidence(
            String packageName, long version, String signer) {
        return new UpdatePackageVerifier.PackageEvidence(packageName, version, Set.of(signer));
    }
}
