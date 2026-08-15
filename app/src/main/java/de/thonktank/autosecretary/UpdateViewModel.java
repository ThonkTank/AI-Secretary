package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.update.UpdateInfo;
import de.thonktank.autosecretary.update.UpdateRepository;
import de.thonktank.autosecretary.update.UpdateUiState;
import de.thonktank.autosecretary.update.VerifiedUpdate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

public final class UpdateViewModel extends ViewModel {
    private final UpdateRepository repository;
    private final UiPreferences preferences;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final LongSupplier now;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UpdateUiState> state =
            new MutableLiveData<>(UpdateUiState.idle());
    private final MutableLiveData<UpdateEvent> events = new MutableLiveData<>();
    private VerifiedUpdate verified;

    UpdateViewModel(UpdateRepository repository, UiPreferences preferences, AppLogger logger,
                    UiTextProvider texts, LongSupplier now) {
        this.repository = repository;
        this.preferences = preferences;
        this.logger = logger;
        this.texts = texts;
        this.now = now;
    }

    LiveData<UpdateUiState> state() { return state; }
    LiveData<UpdateEvent> events() { return events; }

    void automaticCheck() {
        long currentTime = now.getAsLong();
        if (!preferences.shouldCheckUpdates(currentTime)) return;
        preferences.markUpdateCheck(currentTime);
        check(true);
    }

    void manualAction() {
        UpdateUiState current = state.getValue();
        if (current == null) return;
        if (current.status == UpdateUiState.Status.AVAILABLE) download(current.update);
        else if (current.status == UpdateUiState.Status.READY) requestInstall();
        else if (current.status != UpdateUiState.Status.CHECKING
                && current.status != UpdateUiState.Status.DOWNLOADING) check(false);
    }

    void accept(UpdateInfo update) {
        if (update != null) download(update);
    }

    void postpone(UpdateInfo update) {
        if (update != null) preferences.postponeUpdate(update.versionCode, now.getAsLong());
    }

    void requestInstall() {
        if (verified != null) events.setValue(UpdateEvent.install(verified));
    }

    private void check(boolean automatic) {
        UpdateUiState current = state.getValue();
        if (current != null && (current.status == UpdateUiState.Status.CHECKING
                || current.status == UpdateUiState.Status.DOWNLOADING)) return;
        state.setValue(UpdateUiState.checking());
        worker.execute(() -> {
            try {
                UpdateInfo update = repository.check();
                if (update == null) {
                    state.postValue(UpdateUiState.current());
                    return;
                }
                state.postValue(UpdateUiState.available(update));
                if (!automatic || preferences.shouldPromptForUpdate(
                        update.versionCode, now.getAsLong()))
                    events.postValue(UpdateEvent.available(update));
            } catch (Exception error) {
                String message = texts.text(R.string.error_update_check);
                logger.error("Updater", "Update check failed", error);
                state.postValue(UpdateUiState.error(message));
                if (!automatic) events.postValue(UpdateEvent.error(message));
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
            } catch (Exception error) {
                String message = texts.text(R.string.error_update_download);
                logger.error("Updater", "Update download or verification failed", error);
                state.postValue(UpdateUiState.error(message));
                events.postValue(UpdateEvent.error(message));
            }
        });
    }

    @Override protected void onCleared() {
        worker.shutdownNow();
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;

        public Factory(AppContainer container) {
            this.container = container;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!modelClass.isAssignableFrom(UpdateViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new UpdateViewModel(container.updates, container.uiPreferences,
                    container.logger, container.texts, System::currentTimeMillis);
        }
    }
}
