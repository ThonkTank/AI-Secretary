package com.autosecretary.ui.migration;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.LegacyImportPort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public final class LegacyImportViewModel extends ViewModel {
    private final LegacyImportPort imports;
    private final ExecutorService io;
    private final Executor uiExecutor;
    private final Runnable initializeCore;
    private final MutableLiveData<LegacyImportUiState> state =
            new MutableLiveData<>(LegacyImportUiState.initial());
    private final List<Future<?>> running = new ArrayList<>();
    private volatile boolean cleared;

    public LegacyImportViewModel(
            LegacyImportPort imports,
            ExecutorService io,
            Executor uiExecutor,
            Runnable initializeCore) {
        this.imports = imports;
        this.io = io;
        this.uiExecutor = uiExecutor;
        this.initializeCore = initializeCore;
    }

    public LiveData<LegacyImportUiState> state() { return state; }

    public void importArchive(LegacyImportPort.ArchiveSource source) {
        submit(() -> imports.importArchive(source));
    }

    public void chooseEmptyDatabase() {
        submit(imports::chooseEmptyDatabase);
    }

    private void submit(Runnable action) {
        LegacyImportUiState current = state.getValue();
        if (current != null && current.busy()) return;
        state.setValue(new LegacyImportUiState(true, false, null));
        synchronized (running) {
            running.removeIf(Future::isDone);
            running.add(io.submit(() -> {
                try {
                    action.run();
                    initializeCore.run();
                    dispatch(() -> state.setValue(new LegacyImportUiState(false, true, null)));
                } catch (Throwable error) {
                    String message = error.getCause() != null && error.getCause().getMessage() != null
                            ? error.getCause().getMessage()
                            : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    dispatch(() -> state.setValue(new LegacyImportUiState(false, false, message)));
                }
            }));
        }
    }

    private void dispatch(Runnable action) {
        uiExecutor.execute(() -> {
            if (!cleared) action.run();
        });
    }

    @Override
    protected void onCleared() {
        cleared = true;
        synchronized (running) {
            for (Future<?> future : running) future.cancel(true);
            running.clear();
        }
    }
}
