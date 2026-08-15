package de.thonktank.autosecretary.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class UpdateInstallerRobolectricTest {
    @Test public void installerSharesOnlyTheVerifiedCacheFile() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File directory = new File(context.getCacheDir(), "updates");
        assertTrue(directory.exists() || directory.mkdirs());
        File apk = new File(directory, "verified.apk");
        assertTrue(apk.exists() || apk.createNewFile());
        UpdateInfo info = new UpdateInfo(3, "0.2.3", 1);

        Intent intent = new UpdateInstaller().installerIntent(context,
                new VerifiedUpdate(info, apk));

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
}
