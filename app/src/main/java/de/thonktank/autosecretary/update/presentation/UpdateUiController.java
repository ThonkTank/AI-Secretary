package de.thonktank.autosecretary.update.presentation;

import androidx.lifecycle.LiveData;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.presentation.UiTextProvider;

/** Coordinates update state and one-shot effects without depending on Android UI APIs. */
public final class UpdateUiController {
    private final UpdateFlow flow;
    private final UpdateDialogs dialogs;
    private final UpdatePlatform platform;
    private final UiTextProvider texts;
    private final boolean automaticChecks;

    public UpdateUiController(UpdateFlow flow, UpdateDialogs dialogs, UpdatePlatform platform,
                              UiTextProvider texts, boolean automaticChecks) {
        this.flow = flow;
        this.dialogs = dialogs;
        this.platform = platform;
        this.texts = texts;
        this.automaticChecks = automaticChecks;
    }

    public LiveData<UpdateUiState> state() { return flow.state(); }
    public LiveData<UpdateEvent> effects() { return flow.events(); }

    public void onResume() {
        if (automaticChecks) flow.automaticCheck();
    }

    public void onManualAction() {
        flow.manualAction();
    }

    public void onInstallPermissionResult() {
        if (platform.canInstallPackages()) flow.requestInstall();
    }

    public void handleEffect(UpdateEvent effect) {
        if (effect == null || !effect.consume()) return;
        if (effect.type == UpdateEvent.Type.AVAILABLE) {
            dialogs.showAvailable(effect.update,
                    () -> flow.postpone(effect.update),
                    () -> flow.accept(effect.update));
        } else if (effect.type == UpdateEvent.Type.INSTALL) {
            openInstaller(effect);
        } else {
            showError(effect.message);
        }
    }

    private void openInstaller(UpdateEvent effect) {
        if (!platform.canInstallPackages()) {
            dialogs.showInstallPermission(platform::openInstallSettings);
            return;
        }
        if (!platform.openInstaller(effect.verified))
            showError(texts.text(R.string.error_update_download));
    }

    private void showError(String message) {
        String visibleMessage = message == null
                ? texts.text(R.string.error_update_check) : message;
        dialogs.showError(visibleMessage, platform::openReleases);
    }
}
