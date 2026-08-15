package com.autosecretary.platform.update;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public final class AndroidUpdateRepositoryCleanupTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void keepsOnlyThePersistedFutureCandidate() throws Exception {
        File directory = temporary.newFolder("updates");
        File installed = file(directory, "AutoSecretary-8.apk");
        File pending = file(directory, "AutoSecretary-10.apk");
        File unrelatedNewer = file(directory, "AutoSecretary-11.apk");
        File partial = file(directory, "AutoSecretary-10.partial");

        assertTrue(AndroidUpdateRepository.cleanupFiles(directory, 8, 10));

        assertFalse(installed.exists());
        assertTrue(pending.exists());
        assertFalse(unrelatedNewer.exists());
        assertFalse(partial.exists());
    }

    @Test public void removesEverythingWhenPendingVersionWasInstalledOrDisappeared()
            throws Exception {
        File directory = temporary.newFolder("updates");
        File installedCandidate = file(directory, "AutoSecretary-10.apk");
        File stale = file(directory, "AutoSecretary-9.apk");

        assertFalse(AndroidUpdateRepository.cleanupFiles(directory, 10, 10));
        assertFalse(installedCandidate.exists());
        assertFalse(stale.exists());

        File orphan = file(directory, "AutoSecretary-12.apk");
        assertFalse(AndroidUpdateRepository.cleanupFiles(directory, 10, 13));
        assertFalse(orphan.exists());
    }

    private static File file(File directory, String name) throws Exception {
        File value = new File(directory, name);
        assertTrue(value.createNewFile());
        return value;
    }
}
