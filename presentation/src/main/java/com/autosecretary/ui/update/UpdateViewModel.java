package com.autosecretary.ui.update;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.update.DownloadProgress;
import com.autosecretary.application.update.DownloadTicket;
import com.autosecretary.application.update.UpdateCheckResult;
import com.autosecretary.application.update.UpdateException;
import com.autosecretary.application.update.UpdateFailure;
import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.UpdateRepository;
import com.autosecretary.application.update.VerifiedUpdate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Restorable update state machine. Rendering never launches Android intents. */
public final class UpdateViewModel extends ViewModel {
    private static final String VERSION_CODE = "update.versionCode";
    private static final String VERSION_NAME = "update.versionName";
    private static final String PACKAGE_NAME = "update.packageName";
    private static final String APK_URL = "update.apkUrl";
    private static final String APK_SIZE = "update.apkSize";
    private static final String SHA256 = "update.sha256";
    private static final String SIGNER = "update.signer";
    private static final String DOWNLOAD_ID = "update.downloadId";
    private static final String VERIFIED_PATH = "update.verifiedPath";
    private static final String INSTALL_PENDING = "update.installPending";
    private static final String SETTINGS_OPENED = "update.settingsOpened";
    private static final String OPENED_VERSION = "update.openedVersion";
    private static final String EFFECT_SEQUENCE = "update.effectSequence";
    private static final String LAST_CONSUMED_EFFECT = "update.lastConsumedEffect";

    private final SavedStateHandle savedState;
    private final UpdateRepository updater;
    private final ExecutorService io;
    private final Executor uiExecutor;
    private final MutableLiveData<UpdateUiState> state;
    private final MutableLiveData<UpdateUiEffect> effects = new MutableLiveData<>();
    private final List<Future<?>> running = new ArrayList<>();
    private volatile boolean cleared;

    public UpdateViewModel(
            SavedStateHandle savedState,
            UpdateRepository updater,
            ExecutorService io,
            Executor uiExecutor) {
        this.savedState = savedState;
        this.updater = updater;
        this.io = io;
        this.uiExecutor = uiExecutor;
        UpdateInfo pending = restorePending();
        DownloadTicket ticket = restoreTicket(pending);
        VerifiedUpdate verified = restoreVerified(pending);
        if (verified != null) {
            state = new MutableLiveData<>(new UpdateUiState.Ready(verified));
        } else if (pending == null) {
            state = new MutableLiveData<>(UpdateUiState.initial());
        } else if (ticket == null) {
            state = new MutableLiveData<>(new UpdateUiState.Available(pending));
        } else {
            DownloadProgress progress = updater.query(ticket);
            state = new MutableLiveData<>(stateFor(pending, ticket, progress));
            resume(pending, ticket, progress);
        }
    }

    public LiveData<UpdateUiState> state() { return state; }
    public LiveData<UpdateUiEffect> effects() { return effects; }

    public void check() {
        if (current().busy()) return;
        state.setValue(new UpdateUiState.Checking());
        submit(() -> {
            UpdateCheckResult result = updater.check();
            dispatch(() -> state.setValue(result instanceof UpdateCheckResult.Available available
                    ? new UpdateUiState.Available(available.update())
                    : new UpdateUiState.Current()));
        }, null);
    }

    public void download() {
        UpdateInfo update = current().available();
        if (current().busy() || update == null) return;
        remember(update);
        submit(() -> {
            DownloadTicket ticket = updater.enqueue(update);
            savedState.set(DOWNLOAD_ID, ticket.id());
            dispatch(() -> state.setValue(new UpdateUiState.Downloading(
                    update, ticket, new DownloadProgress.Pending())));
            observe(update, ticket);
        }, update);
    }

    public void cancelDownload() {
        UpdateUiState current = current();
        DownloadTicket ticket = current instanceof UpdateUiState.Downloading value
                ? value.ticket() : current instanceof UpdateUiState.Verifying value
                ? value.ticket() : null;
        if (ticket == null) return;
        updater.cancel(ticket);
        UpdateInfo update = current.available();
        clearTicket();
        state.setValue(update == null ? UpdateUiState.initial()
                : new UpdateUiState.Available(update));
    }

    public void requestInstall(boolean canInstallPackages) {
        VerifiedUpdate verified = current().verified();
        if (verified == null) return;
        int version = verified.info().versionCode();
        savedState.set(INSTALL_PENDING, version);
        savedState.set(OPENED_VERSION, 0);
        savedState.set(SETTINGS_OPENED, false);
        continueInstall(canInstallPackages);
    }

    public void continueInstall(boolean canInstallPackages) {
        VerifiedUpdate verified = current().verified();
        Integer pending = savedState.get(INSTALL_PENDING);
        if (verified == null || pending == null
                || pending != verified.info().versionCode()) return;
        if (!canInstallPackages) {
            if (Boolean.TRUE.equals(savedState.get(SETTINGS_OPENED))) return;
            savedState.set(SETTINGS_OPENED, true);
            emit(new UpdateUiEffect.OpenUnknownSourcesSettings(nextEffectId()));
            return;
        }
        Integer opened = savedState.get(OPENED_VERSION);
        if (opened != null && opened == pending) return;
        savedState.remove(INSTALL_PENDING);
        savedState.set(OPENED_VERSION, pending);
        savedState.set(SETTINGS_OPENED, false);
        emit(new UpdateUiEffect.OpenInstaller(nextEffectId(), verified));
    }

    public void consumeEffect(long id) {
        Long consumed = savedState.get(LAST_CONSUMED_EFFECT);
        if (consumed == null || id > consumed) savedState.set(LAST_CONSUMED_EFFECT, id);
        UpdateUiEffect value = effects.getValue();
        if (value != null && value.id() == id) effects.setValue(null);
    }

    private void resume(UpdateInfo update, DownloadTicket ticket, DownloadProgress progress) {
        if (progress instanceof DownloadProgress.Complete) verify(update, ticket);
        else if (progress instanceof DownloadProgress.Pending
                || progress instanceof DownloadProgress.Running) observe(update, ticket);
    }

    private void observe(UpdateInfo update, DownloadTicket ticket) {
        submit(() -> {
            while (!cleared && !Thread.currentThread().isInterrupted()) {
                DownloadProgress progress = updater.query(ticket);
                dispatch(() -> state.setValue(stateFor(update, ticket, progress)));
                if (progress instanceof DownloadProgress.Complete) {
                    verify(update, ticket);
                    return;
                }
                if (progress instanceof DownloadProgress.Failed) return;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, update);
    }

    private void verify(UpdateInfo update, DownloadTicket ticket) {
        dispatch(() -> state.setValue(new UpdateUiState.Verifying(update, ticket)));
        submit(() -> {
            VerifiedUpdate verified = updater.verify(ticket);
            savedState.set(VERIFIED_PATH, verified.apk().getAbsolutePath());
            clearTicket();
            dispatch(() -> state.setValue(new UpdateUiState.Ready(verified)));
        }, update);
    }

    private static UpdateUiState stateFor(
            UpdateInfo update, DownloadTicket ticket, DownloadProgress progress) {
        if (progress instanceof DownloadProgress.Complete) {
            return new UpdateUiState.Verifying(update, ticket);
        }
        if (progress instanceof DownloadProgress.Failed failed) {
            return new UpdateUiState.Error(update, null, failed.failure());
        }
        return new UpdateUiState.Downloading(update, ticket, progress);
    }

    private void emit(UpdateUiEffect effect) {
        Long consumed = savedState.get(LAST_CONSUMED_EFFECT);
        if (consumed == null || effect.id() > consumed) effects.setValue(effect);
    }

    private long nextEffectId() {
        Long current = savedState.get(EFFECT_SEQUENCE);
        long next = (current == null ? 0 : current) + 1;
        savedState.set(EFFECT_SEQUENCE, next);
        return next;
    }

    private void submit(Runnable work, UpdateInfo update) {
        synchronized (running) {
            running.removeIf(Future::isDone);
            running.add(io.submit(() -> {
                try {
                    work.run();
                } catch (Throwable error) {
                    UpdateFailure failure = failure(error);
                    dispatch(() -> state.setValue(new UpdateUiState.Error(
                            update, current().verified(), failure)));
                }
            }));
        }
    }

    private static UpdateFailure failure(Throwable error) {
        if (error instanceof UpdateException typed) return typed.failure();
        Throwable source = error;
        while (source.getCause() != null && source.getCause() != source) source = source.getCause();
        String message = source.getMessage() == null
                ? source.getClass().getSimpleName() : source.getMessage();
        String normalized = (source.getClass().getSimpleName() + " " + message)
                .toLowerCase(Locale.ROOT);
        if (source instanceof SecurityException) {
            return new UpdateFailure(UpdateFailure.Kind.SECURITY_REJECTED, message, false);
        }
        if (normalized.contains("network") || normalized.contains("timeout")) {
            return new UpdateFailure(UpdateFailure.Kind.NETWORK, message, true);
        }
        return new UpdateFailure(UpdateFailure.Kind.INTERNAL, message, true);
    }

    private void remember(UpdateInfo update) {
        savedState.set(VERSION_CODE, update.versionCode());
        savedState.set(VERSION_NAME, update.versionName());
        savedState.set(PACKAGE_NAME, update.packageName());
        savedState.set(APK_URL, update.apkUrl());
        savedState.set(APK_SIZE, update.apkSizeBytes());
        savedState.set(SHA256, update.sha256());
        savedState.set(SIGNER, update.signerSha256());
    }

    private UpdateInfo restorePending() {
        Integer code = savedState.get(VERSION_CODE);
        if (code == null || code < 1) return null;
        try {
            Long size = savedState.get(APK_SIZE);
            return new UpdateInfo(code, savedState.get(VERSION_NAME), savedState.get(PACKAGE_NAME),
                    savedState.get(APK_URL), size == null ? 0 : size,
                    savedState.get(SHA256), savedState.get(SIGNER));
        } catch (RuntimeException invalid) {
            clearPending();
            return null;
        }
    }

    private DownloadTicket restoreTicket(UpdateInfo pending) {
        Long id = savedState.get(DOWNLOAD_ID);
        if (pending == null || id == null || id < 1) return null;
        try { return new DownloadTicket(id, pending.versionCode()); }
        catch (RuntimeException invalid) { clearTicket(); return null; }
    }

    private VerifiedUpdate restoreVerified(UpdateInfo pending) {
        String path = savedState.get(VERIFIED_PATH);
        if (pending == null || path == null) return null;
        File apk = new File(path);
        if (!apk.isFile()) {
            savedState.remove(VERIFIED_PATH);
            return null;
        }
        try { return new VerifiedUpdate(pending, apk); }
        catch (RuntimeException invalid) { return null; }
    }

    private void clearTicket() { savedState.remove(DOWNLOAD_ID); }

    private void clearPending() {
        savedState.remove(VERSION_CODE);
        savedState.remove(VERSION_NAME);
        savedState.remove(PACKAGE_NAME);
        savedState.remove(APK_URL);
        savedState.remove(APK_SIZE);
        savedState.remove(SHA256);
        savedState.remove(SIGNER);
        savedState.remove(VERIFIED_PATH);
        clearTicket();
    }

    private UpdateUiState current() {
        UpdateUiState value = state.getValue();
        return value == null ? UpdateUiState.initial() : value;
    }

    private void dispatch(Runnable action) {
        uiExecutor.execute(() -> { if (!cleared) action.run(); });
    }

    @Override protected void onCleared() {
        cleared = true;
        synchronized (running) {
            for (Future<?> future : running) future.cancel(true);
            running.clear();
        }
    }
}
