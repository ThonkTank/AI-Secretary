package com.autosecretary.ui.update;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.update.UpdateGateway;
import com.autosecretary.application.update.UpdateInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public final class UpdateViewModel extends ViewModel {
    private final UpdateGateway gateway;
    private final ExecutorService io;
    private final Executor uiExecutor;
    private final MutableLiveData<UpdateUiState> state = new MutableLiveData<>(UpdateUiState.initial());
    private final List<Future<?>> running = new ArrayList<>();
    private volatile boolean cleared;

    public UpdateViewModel(UpdateGateway gateway, ExecutorService io, Executor uiExecutor) {
        this.gateway = gateway;
        this.io = io;
        this.uiExecutor = uiExecutor;
    }

    public LiveData<UpdateUiState> state() { return state; }

    public void check() {
        UpdateUiState current = current();
        if (current.busy()) return;
        state.setValue(new UpdateUiState(true, current.checked(), current.available(),
                current.verified(), null));
        submit(() -> {
            UpdateInfo update = gateway.check();
            dispatch(() -> state.setValue(new UpdateUiState(false, true, update, null, null)));
        });
    }

    public void download() {
        UpdateUiState current = current();
        if (current.busy() || current.available() == null) return;
        state.setValue(new UpdateUiState(true, true, current.available(), null, null));
        submit(() -> {
            var verified = gateway.downloadAndVerify(current.available());
            dispatch(() -> state.setValue(new UpdateUiState(
                    false, true, current.available(), verified, null)));
        });
    }

    private void submit(Runnable work) {
        synchronized (running) {
            running.removeIf(Future::isDone);
            running.add(io.submit(() -> {
                try {
                    work.run();
                } catch (Throwable error) {
                    String message = error.getCause() != null && error.getCause().getMessage() != null
                            ? error.getCause().getMessage()
                            : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    dispatch(() -> state.setValue(new UpdateUiState(
                            false, true, current().available(), current().verified(), message)));
                }
            }));
        }
    }

    private UpdateUiState current() {
        UpdateUiState value = state.getValue();
        return value == null ? UpdateUiState.initial() : value;
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
