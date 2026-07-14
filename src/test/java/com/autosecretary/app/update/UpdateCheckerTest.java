package com.autosecretary.app.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;

import com.autosecretary.BuildConfig;
import com.autosecretary.R;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class UpdateCheckerTest extends AutoSecretaryRobolectricTest {
    private Activity activity;
    private ExecutorService executor;

    @Before
    public void setUp() {
        ShadowDialog.reset();
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        executor = new DirectExecutorService();
    }

    @After
    public void tearDown() {
        activity.finish();
    }

    @Test
    public void manualCheckShowsUpToDateDialogWhenReleaseIsNotNewer() {
        UpdateChecker checker = new UpdateChecker(activity, executor,
                new StaticUpdateClient(new AvailableUpdate(BuildConfig.VERSION_CODE, "apk", "release", "page")));

        checker.checkForUpdateManually();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(
                activity.getString(R.string.update_no_update_message),
                latestDialogMessage());
    }

    @Test
    public void manualCheckShowsFailureDialogWhenReleaseLookupFails() {
        UpdateChecker checker = new UpdateChecker(activity, executor, new FailingUpdateClient());

        checker.checkForUpdateManually();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue(latestDialogMessage().contains("offline"));
    }

    @Test
    public void startupCheckSuppressesLookupFailure() {
        UpdateChecker checker = new UpdateChecker(activity, executor, new FailingUpdateClient());

        checker.checkForUpdate();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertNull(ShadowDialog.getLatestDialog());
    }

    @Test
    public void startupCheckShowsUpdateDialogWhenReleaseIsNewer() {
        UpdateChecker checker = new UpdateChecker(activity, executor,
                new StaticUpdateClient(new AvailableUpdate(BuildConfig.VERSION_CODE + 1, "apk", "release", "page")));

        checker.checkForUpdate();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(
                activity.getString(R.string.update_available_message, BuildConfig.VERSION_CODE + 1),
                latestDialogMessage());
    }

    @Test
    public void buildInstallIntentUsesFileProviderApkMimeTypeAndReadGrant() throws Exception {
        UpdateChecker checker = new UpdateChecker(activity, executor,
                new StaticUpdateClient(new AvailableUpdate(BuildConfig.VERSION_CODE + 1, "apk", "release", "page")));
        File apkFile = new File(activity.getCacheDir(), "update.apk");
        try (FileOutputStream outputStream = new FileOutputStream(apkFile)) {
            outputStream.write("apk".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        Intent intent = checker.buildInstallIntent(activity, apkFile);

        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("application/vnd.android.package-archive", intent.getType());
        assertNotNull(intent.getData());
        assertEquals("content", intent.getData().getScheme());
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }

    private static final class StaticUpdateClient implements UpdateClient {
        private final AvailableUpdate update;

        private StaticUpdateClient(AvailableUpdate update) {
            this.update = update;
        }

        @Override
        public AvailableUpdate fetchLatestUpdate() {
            return update;
        }

        @Override
        public File downloadApk(AvailableUpdate update, File targetFile) {
            return targetFile;
        }
    }

    private String latestDialogMessage() {
        TextView message = ShadowDialog.getLatestDialog().findViewById(android.R.id.message);
        assertNotNull(message);
        return message.getText().toString();
    }

    private static final class FailingUpdateClient implements UpdateClient {
        @Override
        public AvailableUpdate fetchLatestUpdate() throws IOException {
            throw new IOException("offline");
        }

        @Override
        public File downloadApk(AvailableUpdate update, File targetFile) {
            return targetFile;
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return java.util.List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
