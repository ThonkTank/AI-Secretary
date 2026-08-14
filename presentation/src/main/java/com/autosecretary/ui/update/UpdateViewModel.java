package com.autosecretary.ui.update;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.update.UpdateFailure;
import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.UpdateRepository;
import com.autosecretary.application.update.VerifiedUpdate;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public final class UpdateViewModel extends ViewModel {
    private static final String VERSION_CODE = "update.versionCode";
    private static final String VERSION_NAME = "update.versionName";
    private static final String PACKAGE_NAME = "update.packageName";
    private static final String APK_URL = "update.apkUrl";
    private static final String APK_SIZE = "update.apkSize";
    private static final String SHA256 = "update.sha256";
    private static final String SIGNER = "update.signer";
    private static final String VERIFIED_PATH = "update.verifiedPath";
    private static final String INSTALL_PENDING = "update.installPending";
    private static final String SETTINGS_OPENED = "update.settingsOpened";
    private static final String OPENED_VERSION = "update.openedVersion";
    private static final String EFFECT_SEQUENCE = "update.effectSequence";

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
        VerifiedUpdate verified = restoreVerified(pending);
        state = new MutableLiveData<>(verified != null ? new UpdateUiState.Ready(verified)
                : pending == null ? UpdateUiState.initial() : new UpdateUiState.Available(pending));
        if (pending != null && verified == null) download();
    }

    public LiveData<UpdateUiState> state() { return state; }
    public LiveData<UpdateUiEffect> effects() { return effects; }

    public void check() {
        if (current().busy()) return;
        state.setValue(new UpdateUiState.Checking());
        submit(() -> {
            UpdateInfo update = updater.check();
            dispatch(() -> state.setValue(update == null
                    ? new UpdateUiState.Current() : new UpdateUiState.Available(update)));
        }, null);
    }

    public void download() {
        UpdateInfo update = current().available();
        if (current().busy() || update == null) return;
        remember(update);
        state.setValue(new UpdateUiState.Downloading(update));
        submit(() -> {
            var verified = updater.downloadAndVerify(update);
            dispatch(() -> {
                savedState.set(VERIFIED_PATH, verified.apk().getAbsolutePath());
                state.setValue(new UpdateUiState.Ready(verified));
            });
        }, update);
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
        UpdateUiEffect value = effects.getValue();
        if (value != null && value.id() == id) effects.setValue(null);
    }

    private void emit(UpdateUiEffect effect) { effects.setValue(effect); }

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
                    dispatch(() -> {
                        if (!failure.retryable()) clearPending();
                        state.setValue(new UpdateUiState.Error(
                                update, current().verified(), failure));
                    });
                }
            }));
        }
    }

    private static UpdateFailure failure(Throwable error) {
        Throwable source = error;
        while (source.getCause() != null && source.getCause() != source) source = source.getCause();
        String message = source.getMessage() == null
                ? source.getClass().getSimpleName() : source.getMessage();
        String normalized = (source.getClass().getSimpleName() + " " + message)
                .toLowerCase(Locale.ROOT);
        UpdateFailure.Kind kind;
        boolean retryable;
        if (normalized.contains("429") || normalized.contains("rate")) {
            kind = UpdateFailure.Kind.RATE_LIMITED; retryable = true;
        } else if (normalized.contains("network") || normalized.contains("timeout")
                || normalized.contains("timed out") || normalized.contains("unknownhost")) {
            kind = UpdateFailure.Kind.NETWORK; retryable = true;
        } else if (source instanceof SecurityException) {
            kind = UpdateFailure.Kind.SECURITY_REJECTED; retryable = false;
        } else if (normalized.contains("download")) {
            kind = UpdateFailure.Kind.DOWNLOAD_FAILED; retryable = true;
        } else if (normalized.contains("speicher") || normalized.contains("space")) {
            kind = UpdateFailure.Kind.STORAGE; retryable = true;
        } else if (normalized.contains("metadata") || normalized.contains("release")) {
            kind = UpdateFailure.Kind.INVALID_RELEASE; retryable = false;
        } else {
            kind = UpdateFailure.Kind.INTERNAL; retryable = true;
        }
        return new UpdateFailure(kind, message, retryable);
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

    private void clearPending() {
        savedState.remove(VERSION_CODE);
        savedState.remove(VERSION_NAME);
        savedState.remove(PACKAGE_NAME);
        savedState.remove(APK_URL);
        savedState.remove(APK_SIZE);
        savedState.remove(SHA256);
        savedState.remove(SIGNER);
        savedState.remove(VERIFIED_PATH);
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
