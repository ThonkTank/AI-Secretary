package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class AndroidUpdateDialogsRobolectricTest {
    @Test public void availableDialogFormatsSizeAndDelegatesChoice() throws Exception {
        try (ActivityController<TestActivity> activity =
                     Robolectric.buildActivity(TestActivity.class).setup()) {
            AtomicInteger accepted = new AtomicInteger();
            new AndroidUpdateDialogs(activity.get()).showAvailable(updateInfo(),
                    () -> { }, accepted::incrementAndGet);

            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            ShadowAlertDialog shadowDialog = Shadow.extract(dialog);
            assertTrue(shadowDialog.getMessage().toString().contains("1,0 MB"));
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(1, accepted.get());
        }
    }

    @Test public void errorDialogDelegatesReleaseNavigation() {
        try (ActivityController<TestActivity> activity =
                     Robolectric.buildActivity(TestActivity.class).setup()) {
            AtomicInteger releases = new AtomicInteger();
            new AndroidUpdateDialogs(activity.get()).showError("Fehler", releases::incrementAndGet);

            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            ShadowAlertDialog shadowDialog = Shadow.extract(dialog);
            assertEquals("Fehler", shadowDialog.getMessage());
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(1, releases.get());
        }
    }

    private static UpdateInfo updateInfo() throws Exception {
        ReleaseMetadata metadata = ReleaseMetadata.create(3, "0.2.3",
                "de.thonktank.autosecretary", "AutoSecretary.apk", 1024 * 1024,
                String.join("", Collections.nCopies(64, "a")),
                String.join("", Collections.nCopies(64, "b")),
                String.join("", Collections.nCopies(40, "c")));
        return UpdateInfo.from(metadata, "https://github.com/AutoSecretary.apk");
    }

    public static final class TestActivity extends Activity { }
}
