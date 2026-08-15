package de.thonktank.autosecretary.update.presentation;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.update.application.UpdateErrorReporter;
import de.thonktank.autosecretary.update.application.UpdateClock;
import de.thonktank.autosecretary.update.application.UpdateExecutor;
import de.thonktank.autosecretary.update.application.UpdateExecutorFactory;
import de.thonktank.autosecretary.update.application.UpdatePreferences;
import de.thonktank.autosecretary.update.application.UpdateRepository;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.domain.UpdateCheckResult;
import de.thonktank.autosecretary.update.domain.UpdateFailure;
import de.thonktank.autosecretary.update.domain.UpdateInfo;

public final class UpdateViewModel extends ViewModel implements UpdateFlow {
    private final UpdateRepository repository;
    private final UpdatePreferences preferences;
    private final UpdateErrorReporter errors;
    private final UiTextProvider texts;
    private final UpdateClock clock;
    private final UpdateExecutor worker;
    private final MutableLiveData<UpdateUiState> state =
            new MutableLiveData<>(UpdateUiState.idle());
    private final MutableLiveData<UpdateEvent> events = new MutableLiveData<>();
    private VerifiedUpdate verified;

    UpdateViewModel(UpdateRepository repository, UpdatePreferences preferences,
                    UpdateErrorReporter errors, UiTextProvider texts, UpdateClock clock,
                    UpdateExecutor worker) {
        this.repository = repository;
        this.preferences = preferences;
        this.errors = errors;
        this.texts = texts;
        this.clock = clock;
        this.worker = worker;
    }

    public LiveData<UpdateUiState> state() { return state; }
    public LiveData<UpdateEvent> events() { return events; }

    public void automaticCheck() {
        long currentTime = clock.nowMillis();
        if (!preferences.shouldCheckUpdates(currentTime)) return;
        preferences.markUpdateCheck(currentTime);
        check(true);
    }

    public void manualAction() {
        UpdateUiState current = state.getValue();
        if (current == null) return;
        if (current.status == UpdateUiState.Status.AVAILABLE) download(current.update);
        else if (current.status == UpdateUiState.Status.READY) requestInstall();
        else if (current.status != UpdateUiState.Status.CHECKING
                && current.status != UpdateUiState.Status.DOWNLOADING) check(false);
    }

    public void accept(UpdateInfo update) {
        if (update != null) download(update);
    }

    public void postpone(UpdateInfo update) {
        if (update != null) preferences.postponeUpdate(update.versionCode, clock.nowMillis());
    }

    public void requestInstall() {
        if (verified != null) events.setValue(UpdateEvent.install(verified));
    }

    private void check(boolean automatic) {
        UpdateUiState current = state.getValue();
        if (current != null && (current.status == UpdateUiState.Status.CHECKING
                || current.status == UpdateUiState.Status.DOWNLOADING)) return;
        state.setValue(UpdateUiState.checking());
        worker.execute(() -> {
            try {
                UpdateCheckResult result = repository.check();
                if (!result.isAvailable()) {
                    state.postValue(UpdateUiState.current());
                    return;
                }
                UpdateInfo update = result.availableUpdate();
                state.postValue(UpdateUiState.available(update));
                if (!automatic || preferences.shouldPromptForUpdate(
                        update.versionCode, clock.nowMillis()))
                    events.postValue(UpdateEvent.available(update));
            } catch (UpdateFailure error) {
                handleFailure(error, R.string.error_update_check, automatic);
            }
        });
    }

    private void download(UpdateInfo update) {
        if (update == null) return;
        state.setValue(UpdateUiState.downloading(update, 0));
        worker.execute(() -> {
            try {
                verified = repository.download(update,
                        progress -> state.postValue(UpdateUiState.downloading(update, progress)));
                state.postValue(UpdateUiState.ready(update));
                events.postValue(UpdateEvent.install(verified));
            } catch (UpdateFailure error) {
                handleFailure(error, R.string.error_update_download, false);
            }
        });
    }

    private void handleFailure(UpdateFailure error, int messageResource, boolean silent) {
        String message = texts.text(messageResource);
        errors.report(error);
        state.postValue(UpdateUiState.error(error.kind(), message));
        if (!silent) events.postValue(UpdateEvent.error(error.kind(), message));
    }

    @Override protected void onCleared() {
        worker.close();
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final UpdateRepository repository;
        private final UpdatePreferences preferences;
        private final UpdateErrorReporter errors;
        private final UiTextProvider texts;
        private final UpdateClock clock;
        private final UpdateExecutorFactory workers;

        public Factory(UpdateRepository repository, UpdatePreferences preferences,
                       UpdateErrorReporter errors, UiTextProvider texts, UpdateClock clock,
                       UpdateExecutorFactory workers) {
            this.repository = repository;
            this.preferences = preferences;
            this.errors = errors;
            this.texts = texts;
            this.clock = clock;
            this.workers = workers;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!modelClass.isAssignableFrom(UpdateViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new UpdateViewModel(repository, preferences, errors, texts, clock,
                    workers.create());
        }
    }
}
