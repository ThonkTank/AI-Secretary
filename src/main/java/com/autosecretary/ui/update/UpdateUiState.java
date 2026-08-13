package com.autosecretary.ui.update;

import com.autosecretary.application.update.UpdateInfo;
import com.autosecretary.application.update.VerifiedUpdate;

public record UpdateUiState(
        boolean busy,
        boolean checked,
        UpdateInfo available,
        VerifiedUpdate verified,
        String error) {
    public static UpdateUiState initial() {
        return new UpdateUiState(false, false, null, null, null);
    }
}
