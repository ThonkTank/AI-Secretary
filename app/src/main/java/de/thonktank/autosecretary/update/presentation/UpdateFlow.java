package de.thonktank.autosecretary.update.presentation;

import androidx.lifecycle.LiveData;

import de.thonktank.autosecretary.update.domain.UpdateInfo;

/** Observable update workflow consumed by the activity-facing controller. */
public interface UpdateFlow {
    LiveData<UpdateUiState> state();
    LiveData<UpdateEvent> events();
    void automaticCheck();
    void manualAction();
    void accept(UpdateInfo update);
    void postpone(UpdateInfo update);
    void requestInstall();
}
