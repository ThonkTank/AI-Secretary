package de.thonktank.autosecretary.presentation.options;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.AppDatabase;
import de.thonktank.autosecretary.CalendarPermissionStatus;
import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.local.RoomInvalidationSource;
import de.thonktank.autosecretary.data.observable.CalendarInvalidationSource;
import de.thonktank.autosecretary.data.observable.ClockInvalidationSource;
import de.thonktank.autosecretary.data.observable.PreferenceInvalidationSource;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.update.application.UpdateExecutor;
import de.thonktank.autosecretary.update.application.UpdatePreferences;
import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.ReleaseMetadata;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class OptionsViewModelTest {
    private Context context;
    private AppDatabase database;
    private UiPreferences uiPreferences;
    private CalendarInvalidationSource calendarInvalidations;
    private PresentationInvalidationSource invalidations;
    private FakeRepository repository;
    private FakeUpdatePreferences updatePreferences;
    private AtomicLong now;
    private AtomicInteger reported;
    private AtomicInteger calendarLoads;
    private SavedStateHandle savedState;
    private OptionsViewModel viewModel;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteSharedPreferences("forest_ui");
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().setQueryExecutor(Runnable::run)
                .setTransactionExecutor(Runnable::run).build();
        uiPreferences = new UiPreferences(context, new NoOpLogger());
        repository = new FakeRepository(context);
        updatePreferences = new FakeUpdatePreferences();
        now = new AtomicLong(1_000_000L);
        reported = new AtomicInteger();
        calendarLoads = new AtomicInteger();
        savedState = new SavedStateHandle();
        viewModel = create(savedState);
    }

    @After public void tearDown() {
        if (viewModel != null) viewModel.onCleared();
        if (invalidations != null) invalidations.close();
        database.close();
        context.deleteSharedPreferences("forest_ui");
    }

    @Test public void preferencesAndPermissionHaveOneAtomicOptionsState() {
        int loadsBefore = calendarLoads.get();
        uiPreferences.setThemeMode(UiThemeMode.DARK);
        uiPreferences.setFocusStepLimit(FocusStepLimit.THREE);
        viewModel.dispatch(OptionsAction.restTimerDefaultChanged(75));

        assertEquals(UiThemeMode.DARK, state().themeMode);
        assertEquals(FocusStepLimit.THREE, state().focusStepLimit);
        assertEquals(75, state().restTimerDefaultSeconds);
        assertEquals(75, uiPreferences.restTimerDefaultSeconds());
        assertEquals(DayPalette.at(LocalTime.NOON, DayPalette.Mode.DARK).background,
                state().palette.background);
        assertEquals(loadsBefore, calendarLoads.get());

        viewModel.dispatch(OptionsAction.permissionObserved(false, false));
        assertEquals(CalendarPermissionStatus.REQUESTABLE, state().calendarPermission);
        viewModel.dispatch(OptionsAction.calendarPermissionSelected());
        assertEquals(OptionsRequest.Kind.REQUEST_CALENDAR_PERMISSION,
                state().firstRequest().kind);
    }

    @Test public void automaticCheckIsRateLimitedAndManualActionBypassesIt() {
        viewModel.dispatch(OptionsAction.resumed());
        viewModel.dispatch(OptionsAction.resumed());
        assertEquals(1, repository.checks.get());
        assertEquals(UpdateUiState.Status.CURRENT, state().update.status);

        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        assertEquals(2, repository.checks.get());
    }

    @Test public void flowNavigationUsesTheRestorableOptionsRequestBoundary() {
        viewModel.dispatch(OptionsAction.openFlowSetupSelected());
        OptionsRequest request = state().firstRequest();
        assertNotNull(request);
        assertEquals(OptionsRequest.Kind.OPEN_FLOW_SETUP, request.kind);

        viewModel.onCleared();
        viewModel = create(savedState);
        assertEquals(OptionsRequest.Kind.OPEN_FLOW_SETUP, state().firstRequest().kind);

        viewModel.dispatch(OptionsAction.acknowledgeRequest(state().firstRequest().id));
        viewModel.dispatch(OptionsAction.openFlowRunsSelected());
        assertEquals(OptionsRequest.Kind.OPEN_FLOW_RUNS, state().firstRequest().kind);
    }

    @Test public void acceptedOfferBecomesStableRestorableInstallRequest() throws Exception {
        repository.available = updateInfo(1_000_201L, "0.2.2", 1024L);
        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        OptionsRequest offer = state().firstRequest();
        assertNotNull(offer);
        assertEquals(OptionsRequest.Kind.UPDATE_AVAILABLE, offer.kind);

        viewModel.dispatch(OptionsAction.updateAccepted(offer.id, offer.update));
        OptionsRequest install = state().firstRequest();
        assertEquals(OptionsRequest.Kind.INSTALL_UPDATE, install.kind);
        assertEquals(UpdateUiState.Status.READY, state().update.status);
        assertTrue(repository.downloaded);

        String stableId = install.id;
        viewModel.onCleared();
        viewModel = create(savedState);
        assertEquals(stableId, state().firstRequest().id);
        assertEquals(OptionsRequest.Kind.INSTALL_UPDATE, state().firstRequest().kind);
        assertEquals(UpdateUiState.Status.READY, state().update.status);
    }

    @Test public void installPermissionResultAcknowledgesAndRekeysPendingWork() throws Exception {
        repository.available = updateInfo(5L, "0.2.5", 1L);
        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        OptionsRequest offer = state().firstRequest();
        viewModel.dispatch(OptionsAction.updateAccepted(offer.id, offer.update));
        String firstInstallId = state().firstRequest().id;

        viewModel.dispatch(OptionsAction.installPermissionResult(true));

        assertEquals(OptionsRequest.Kind.INSTALL_UPDATE, state().firstRequest().kind);
        assertNotEquals(firstInstallId, state().firstRequest().id);
        assertSame(state().firstRequest().verified, state().requests.get(0).verified);
    }

    @Test public void manualFailureIsTypedReportedAndConfirmable() {
        repository.failure = new UpdateFailure(UpdateFailure.Kind.NETWORK, "offline");

        viewModel.dispatch(OptionsAction.manualUpdateSelected());

        assertEquals(UpdateUiState.Status.ERROR, state().update.status);
        assertEquals(UpdateFailure.Kind.NETWORK, state().update.errorKind);
        assertEquals(1, reported.get());
        assertEquals(OptionsRequest.Kind.UPDATE_ERROR, state().firstRequest().kind);
        assertEquals(UpdateFailure.Kind.NETWORK, state().firstRequest().errorKind);
    }

    @Test public void duplicateManualActionCannotStartASecondDownload() throws Exception {
        repository.available = updateInfo(7L, "0.2.7", 1L);
        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        OptionsRequest offer = state().firstRequest();
        viewModel.dispatch(OptionsAction.updateAccepted(offer.id, offer.update));

        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        viewModel.dispatch(OptionsAction.manualUpdateSelected());

        assertEquals(1, repository.downloads.get());
        assertEquals(1, state().requests.size());
    }

    @Test public void staleHostCallbacksCannotRestartOrReplaceWorkflow() throws Exception {
        repository.available = updateInfo(8L, "0.2.8", 1L);
        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        OptionsRequest offer = state().firstRequest();
        viewModel.dispatch(OptionsAction.updateAccepted(offer.id, offer.update));
        OptionsRequest install = state().firstRequest();

        viewModel.dispatch(OptionsAction.updateAccepted(offer.id, offer.update));
        viewModel.dispatch(OptionsAction.installerFailed("options:missing"));

        assertEquals(1, repository.downloads.get());
        assertSame(install, state().firstRequest());
        assertEquals(UpdateUiState.Status.READY, state().update.status);
    }

    @Test public void clearedOwnerRejectsLateUpdateResultWithoutAbortingStartedWork() {
        viewModel.onCleared();
        QueuedUpdateExecutor queued = new QueuedUpdateExecutor();
        viewModel = create(new SavedStateHandle(), queued);
        viewModel.dispatch(OptionsAction.manualUpdateSelected());
        assertEquals(UpdateUiState.Status.CHECKING, state().update.status);

        viewModel.onCleared();
        queued.runPending();

        assertTrue(queued.closed);
        assertTrue(queued.ran);
        assertEquals(UpdateUiState.Status.CHECKING, state().update.status);
        assertNull(state().firstRequest());
    }

    private OptionsViewModel create(SavedStateHandle handle) {
        return create(handle, new DirectUpdateExecutor());
    }

    private OptionsViewModel create(SavedStateHandle handle, UpdateExecutor updateExecutor) {
        CalendarDataSource calendar = new CalendarDataSource() {
            @Override public CalendarResult loadToday() {
                calendarLoads.incrementAndGet();
                return new CalendarResult.Success(Collections.emptyList());
            }
            @Override public Subscription observeChanges(Runnable observer) {
                return () -> { };
            }
        };
        if (invalidations != null) invalidations.close();
        calendarInvalidations = new CalendarInvalidationSource(calendar);
        invalidations = new PresentationInvalidationSource(new RoomInvalidationSource(database),
                calendarInvalidations, new PreferenceInvalidationSource(uiPreferences),
                new ClockInvalidationSource(new FixedClock(), observer -> () -> { }),
                Runnable::run);
        return new OptionsViewModel(uiPreferences, calendar, new FixedClock(), new NoOpLogger(),
                (resource, arguments) -> resource == R.string.error_update_download
                        ? "download failed" : "check failed",
                repository, updatePreferences, failure -> reported.incrementAndGet(), now::get,
                true, calendarInvalidations::materializeExternalChange, handle,
                new DirectExecutorService(), updateExecutor, invalidations,
                Runnable::run);
    }

    private OptionsScreenState state() { return viewModel.state().getValue(); }

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
        final Context context;
        final AtomicInteger checks = new AtomicInteger();
        final AtomicInteger downloads = new AtomicInteger();
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
            downloads.incrementAndGet();
            downloaded = true;
            progress.accept(50);
            File apk = new File(context.getCacheDir(), "options-test-update.apk");
            try {
                if (!apk.exists() && !apk.createNewFile()) throw new IOException("create failed");
            } catch (IOException error) {
                throw new UpdateFailure(UpdateFailure.Kind.STORAGE, "create failed", error);
            }
            return VerifiedUpdate.fromVerifiedFile(update, apk);
        }
    }

    private static final class FakeUpdatePreferences implements UpdatePreferences {
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

    private static final class DirectUpdateExecutor implements UpdateExecutor {
        @Override public void execute(Runnable task) { task.run(); }
        @Override public void close() { }
    }

    private static final class QueuedUpdateExecutor implements UpdateExecutor {
        private final AtomicReference<Runnable> pending = new AtomicReference<>();
        boolean closed;
        boolean ran;
        @Override public void execute(Runnable task) {
            if (!pending.compareAndSet(null, task)) throw new IllegalStateException("already queued");
        }
        @Override public void close() { closed = true; }
        void runPending() {
            Runnable task = pending.getAndSet(null);
            if (task != null) {
                ran = true;
                task.run();
            }
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
        @Override public void execute(Runnable command) {
            if (shutdown) throw new IllegalStateException("shut down");
            command.run();
        }
    }

    private static final class FixedClock implements Clock {
        @Override public LocalDate today() { return LocalDate.of(2026, 8, 25); }
        @Override public LocalTime time() { return LocalTime.NOON; }
    }

    private static final class NoOpLogger implements AppLogger {
        @Override public void info(String tag, String message) { }
        @Override public void error(String tag, String message, Throwable error) { }
    }
}
