package de.thonktank.autosecretary.update.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Collections;

public final class UpdateDomainTest {
    private static final String PACKAGE = "de.thonktank.autosecretary";
    private static final String SIGNER = repeat('a', 64);
    private static final String SHA = repeat('b', 64);
    private static final String COMMIT = repeat('c', 40);

    @Test public void updateInfoCanOnlyBeCreatedFromACompleteValidatedContract()
            throws Exception {
        ReleaseMetadata metadata = metadata(7, SIGNER);
        UpdateInfo update = UpdateInfo.from(metadata, "https://github.com/update.apk");

        assertEquals(7, update.versionCode);
        assertSame(metadata, update.metadata());
        assertEquals("github.com", update.apkUri().getHost());

        assertInvalid(() -> UpdateInfo.from(null, "https://github.com/update.apk"));
        assertInvalid(() -> UpdateInfo.from(metadata, "http://github.com/update.apk"));
        assertInvalid(() -> ReleaseMetadata.create(0, "0.2.7", PACKAGE,
                "AutoSecretary.apk", 1, SHA, SIGNER, COMMIT));
    }

    @Test public void checkResultMakesCurrentAndAvailableOutcomesExplicit() throws Exception {
        UpdateCheckResult current = UpdateCheckResult.current();
        UpdateInfo update = UpdateInfo.from(metadata(8, SIGNER),
                "https://github.com/update.apk");

        assertFalse(current.isAvailable());
        assertSame(update, UpdateCheckResult.available(update).availableUpdate());
        try {
            current.availableUpdate();
            fail("current result must not expose an update");
        } catch (IllegalStateException expected) { }
    }

    @Test public void compatibilityRulesReturnSpecificFailureKinds() throws Exception {
        PackageEvidence installed = PackageEvidence.of(PACKAGE, 2,
                Collections.singleton(SIGNER));
        ReleaseMetadata wrongSigner = metadata(7, repeat('d', 64));
        assertFailure(UpdateFailure.Kind.SIGNATURE_MISMATCH, () ->
                UpdateRules.requireAvailable(PACKAGE, "AutoSecretary.apk", 7, COMMIT,
                        wrongSigner, installed));

        ReleaseMetadata valid = metadata(7, SIGNER);
        PackageEvidence wrongArchive = PackageEvidence.of("wrong.package", 7,
                Collections.singleton(SIGNER));
        UpdateInfo update = UpdateInfo.from(valid, "https://github.com/update.apk");
        assertFailure(UpdateFailure.Kind.PACKAGE_MISMATCH, () ->
                UpdateRules.requireDownloaded(PACKAGE, update, installed, wrongArchive));
    }

    private static ReleaseMetadata metadata(long version, String signer) throws UpdateFailure {
        return ReleaseMetadata.create(version, "0.2.7", PACKAGE,
                "AutoSecretary.apk", 1, SHA, signer, COMMIT);
    }

    private static void assertInvalid(FailingAction action) throws Exception {
        assertFailure(UpdateFailure.Kind.INVALID_RELEASE, action);
    }

    private static void assertFailure(UpdateFailure.Kind kind, FailingAction action)
            throws Exception {
        try {
            action.run();
            fail("expected typed update failure");
        } catch (UpdateFailure error) {
            assertEquals(kind, error.kind());
        }
    }

    private static String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }

    @FunctionalInterface
    private interface FailingAction {
        void run() throws Exception;
    }
}
