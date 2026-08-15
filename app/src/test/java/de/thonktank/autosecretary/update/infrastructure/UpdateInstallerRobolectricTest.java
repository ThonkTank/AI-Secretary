package de.thonktank.autosecretary.update.infrastructure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class UpdateInstallerRobolectricTest {
    @Test public void installerSharesOnlyTheVerifiedCacheFile() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File directory = new File(context.getCacheDir(), "updates");
        assertTrue(directory.exists() || directory.mkdirs());
        File apk = new File(directory, "verified.apk");
        assertTrue(apk.exists() || apk.createNewFile());
        UpdateInfo info = updateInfo(3, 1);

        Intent intent = new UpdateInstaller().installerIntent(context,
                VerifiedUpdate.fromVerifiedFile(info, apk));

        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("content", intent.getData().getScheme());
        assertEquals("application/vnd.android.package-archive", intent.getType());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        apk.delete();
    }

    @Test public void settingsIntentTargetsThisApplication() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new UpdateInstaller().settingsIntent(context);

        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.getAction());
        assertEquals("package:" + context.getPackageName(), intent.getDataString());
    }

    private static UpdateInfo updateInfo(long version, long size) throws Exception {
        ReleaseMetadata metadata = ReleaseMetadata.create(version, "0.2.3",
                "de.thonktank.autosecretary", "AutoSecretary.apk", size,
                String.join("", Collections.nCopies(64, "a")),
                String.join("", Collections.nCopies(64, "b")),
                String.join("", Collections.nCopies(40, "c")));
        return UpdateInfo.from(metadata, "https://github.com/AutoSecretary.apk");
    }
}
