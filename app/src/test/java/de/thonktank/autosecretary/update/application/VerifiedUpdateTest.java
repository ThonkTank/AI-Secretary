package de.thonktank.autosecretary.update.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;

public final class VerifiedUpdateTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void onlyAnExistingVerifiedFileBecomesAnInstallationCandidate()
            throws Exception {
        UpdateInfo update = updateInfo();
        File apk = temporary.newFile("update.apk");

        VerifiedUpdate verified = VerifiedUpdate.fromVerifiedFile(update, apk);

        assertSame(update, verified.info);
        assertEquals(apk, verified.apk);
        try {
            VerifiedUpdate.fromVerifiedFile(update, new File(temporary.getRoot(), "missing.apk"));
            fail("missing installation candidate must be rejected");
        } catch (UpdateFailure error) {
            assertEquals(UpdateFailure.Kind.STORAGE, error.kind());
        }
    }

    private static UpdateInfo updateInfo() throws UpdateFailure {
        ReleaseMetadata metadata = ReleaseMetadata.create(3, "0.2.3",
                "de.thonktank.autosecretary", "AutoSecretary.apk", 1,
                repeat('a', 64), repeat('b', 64), repeat('c', 40));
        return UpdateInfo.from(metadata, "https://github.com/AutoSecretary.apk");
    }

    private static String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }
}
