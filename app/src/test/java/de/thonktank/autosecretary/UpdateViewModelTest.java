package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.AndroidUiTextProvider;
import de.thonktank.autosecretary.update.UpdateInfo;
import de.thonktank.autosecretary.update.UpdateRepository;
import de.thonktank.autosecretary.update.UpdateUiState;
import de.thonktank.autosecretary.update.VerifiedUpdate;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class UpdateViewModelTest {
    @Rule public final TestRule instantExecutors = new InstantTaskExecutorRule();

    private Context context;
    private UiPreferences preferences;
    private FakeRepository repository;
    private AtomicLong now;
    private UpdateViewModel viewModel;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteSharedPreferences("forest_ui");
        preferences = new UiPreferences(context, new NoOpLogger());
        repository = new FakeRepository(context);
        now = new AtomicLong(1_000_000L);
        viewModel = new UpdateViewModel(repository, preferences, new NoOpLogger(),
                new AndroidUiTextProvider(context), now::get);
    }

    @After public void tearDown() {
        viewModel.onCleared();
        context.deleteSharedPreferences("forest_ui");
    }

    @Test public void automaticCheckIsDailyAndManualCheckBypassesThrottle() throws Exception {
        viewModel.automaticCheck();
        await(() -> repository.checks.get() == 1
                && state().status == UpdateUiState.Status.CURRENT);

        viewModel.automaticCheck();
        Thread.sleep(30L);
        assertEquals(1, repository.checks.get());

        viewModel.manualAction();
        await(() -> repository.checks.get() == 2);
    }

    @Test public void availableUpdateDownloadsThenEmitsInstallerEvent() throws Exception {
        UpdateInfo info = new UpdateInfo(1_000_201L, "0.2.2", 1024L);
        repository.available = info;

        viewModel.manualAction();
        await(() -> state().status == UpdateUiState.Status.AVAILABLE
                && viewModel.events().getValue() != null);
        UpdateEvent available = viewModel.events().getValue();
        assertNotNull(available);
        assertEquals(UpdateEvent.Type.AVAILABLE, available.type);

        viewModel.accept(info);
        await(() -> state().status == UpdateUiState.Status.READY
                && viewModel.events().getValue().type == UpdateEvent.Type.INSTALL);
        assertEquals(100, state().progress);
        assertTrue(repository.downloaded);
    }

    @Test public void postponingSuppressesTheSameVersionForTwentyFourHours() {
        UpdateInfo info = new UpdateInfo(44L, "0.2.44", 1L);
        viewModel.postpone(info);

        assertFalse(preferences.shouldPromptForUpdate(44L,
                now.get() + 23L * 60L * 60L * 1000L));
        assertTrue(preferences.shouldPromptForUpdate(44L,
                now.get() + 24L * 60L * 60L * 1000L));
        assertTrue(preferences.shouldPromptForUpdate(45L, now.get()));
    }

    private UpdateUiState state() {
        return viewModel.state().getValue();
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 3_000L;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline)
            Thread.sleep(10L);
        assertTrue("Timed out waiting for updater", condition.getAsBoolean());
    }

    private static final class FakeRepository implements UpdateRepository {
        final AtomicInteger checks = new AtomicInteger();
        final Context context;
        UpdateInfo available;
        boolean downloaded;

        FakeRepository(Context context) { this.context = context; }

        @Override public UpdateInfo check() {
            checks.incrementAndGet();
            return available;
        }

        @Override public VerifiedUpdate download(UpdateInfo update, IntConsumer progress) {
            downloaded = true;
            progress.accept(50);
            return new VerifiedUpdate(update, new File(context.getCacheDir(), "test-update.apk"));
        }
    }

    private static final class NoOpLogger implements AppLogger {
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { }
    }
}
