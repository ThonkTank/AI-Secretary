package de.thonktank.autosecretary.update.presentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.update.application.UpdateExecutor;
import de.thonktank.autosecretary.update.application.UpdatePreferences;
import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public final class UpdateViewModelTest {
    @Rule public final TestRule instantExecutors = new InstantTaskExecutorRule();

    private Context context;
    private FakePreferences preferences;
    private FakeRepository repository;
    private AtomicLong now;
    private AtomicInteger reportedErrors;
    private UpdateViewModel viewModel;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = new FakePreferences();
        repository = new FakeRepository(context);
        now = new AtomicLong(1_000_000L);
        reportedErrors = new AtomicInteger();
        viewModel = new UpdateViewModel(repository, preferences,
                error -> reportedErrors.incrementAndGet(),
                new AndroidUiTextProvider(context), now::get, new DirectExecutor());
    }

    @After public void tearDown() {
        viewModel.onCleared();
    }

    @Test public void automaticCheckIsDailyAndManualCheckBypassesThrottle() {
        viewModel.automaticCheck();
        assertEquals(1, repository.checks.get());
        assertEquals(UpdateUiState.Status.CURRENT, state().status);

        viewModel.automaticCheck();
        assertEquals(1, repository.checks.get());

        viewModel.manualAction();
        assertEquals(2, repository.checks.get());
    }

    @Test public void availableUpdateDownloadsThenEmitsInstallerEvent() throws Exception {
        UpdateInfo info = updateInfo(1_000_201L, "0.2.2", 1024L);
        repository.available = info;

        viewModel.manualAction();
        assertEquals(UpdateUiState.Status.AVAILABLE, state().status);
        UpdateEvent available = viewModel.events().getValue();
        assertNotNull(available);
        assertEquals(UpdateEvent.Type.AVAILABLE, available.type);

        viewModel.accept(info);
        assertEquals(UpdateUiState.Status.READY, state().status);
        assertEquals(UpdateEvent.Type.INSTALL, viewModel.events().getValue().type);
        assertEquals(100, state().progress);
        assertTrue(repository.downloaded);
    }

    @Test public void postponingSuppressesTheSameVersionForTwentyFourHours() throws Exception {
        UpdateInfo info = updateInfo(44L, "0.2.44", 1L);
        viewModel.postpone(info);

        assertFalse(preferences.shouldPromptForUpdate(44L,
                now.get() + 23L * 60L * 60L * 1000L));
        assertTrue(preferences.shouldPromptForUpdate(44L,
                now.get() + 24L * 60L * 60L * 1000L));
        assertTrue(preferences.shouldPromptForUpdate(45L, now.get()));
    }

    @Test public void typedRepositoryFailureReachesStateEventAndReporter() throws Exception {
        repository.failure = new UpdateFailure(UpdateFailure.Kind.NETWORK, "offline");

        viewModel.manualAction();

        assertEquals(UpdateUiState.Status.ERROR, state().status);
        assertEquals(UpdateFailure.Kind.NETWORK, state().errorKind);
        assertEquals(UpdateFailure.Kind.NETWORK, viewModel.events().getValue().errorKind);
        assertEquals(1, reportedErrors.get());
    }

    private UpdateUiState state() {
        return viewModel.state().getValue();
    }

    private static UpdateInfo updateInfo(long version, String name, long size)
            throws UpdateFailure {
        ReleaseMetadata metadata = ReleaseMetadata.create(version, name,
                "de.thonktank.autosecretary", "AutoSecretary.apk", size,
                repeat('a', 64), repeat('b', 64), repeat('c', 40));
        return UpdateInfo.from(metadata, "https://github.com/AutoSecretary.apk");
    }

    private static String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }

    private static final class FakeRepository implements UpdateRepository {
        final AtomicInteger checks = new AtomicInteger();
        final Context context;
        UpdateInfo available;
        UpdateFailure failure;
        boolean downloaded;

        FakeRepository(Context context) { this.context = context; }

        @Override public UpdateCheckResult check() throws UpdateFailure {
            checks.incrementAndGet();
            if (failure != null) throw failure;
            return available == null ? UpdateCheckResult.current()
                    : UpdateCheckResult.available(available);
        }

        @Override public VerifiedUpdate download(UpdateInfo update, IntConsumer progress)
                throws UpdateFailure {
            downloaded = true;
            progress.accept(50);
            File apk = new File(context.getCacheDir(), "test-update.apk");
            try {
                if (!apk.exists() && !apk.createNewFile()) throw new IOException("create failed");
            } catch (IOException error) {
                throw new UpdateFailure(UpdateFailure.Kind.STORAGE,
                        "Could not create test APK", error);
            }
            return VerifiedUpdate.fromVerifiedFile(update, apk);
        }
    }

    private static final class DirectExecutor implements UpdateExecutor {
        @Override public void execute(Runnable task) { task.run(); }
        @Override public void close() { }
    }

    private static final class FakePreferences implements UpdatePreferences {
        long lastCheck;
        long postponedCode = -1L;
        long postponedAt;

        @Override public boolean shouldCheckUpdates(long nowMillis) {
            return lastCheck <= 0L || nowMillis < lastCheck
                    || nowMillis - lastCheck >= 24L * 60L * 60L * 1000L;
        }

        @Override public void markUpdateCheck(long nowMillis) { lastCheck = nowMillis; }

        @Override public boolean shouldPromptForUpdate(long versionCode, long nowMillis) {
            return postponedCode != versionCode || nowMillis < postponedAt
                    || nowMillis - postponedAt >= 24L * 60L * 60L * 1000L;
        }

        @Override public void postponeUpdate(long versionCode, long nowMillis) {
            postponedCode = versionCode;
            postponedAt = nowMillis;
        }
    }
}
