package de.thonktank.autosecretary.update.presentation;

import de.thonktank.autosecretary.update.domain.UpdateInfo;

/** UI-shell port for update prompts. Implementations own all Android dialog details. */
public interface UpdateDialogs {
    void showAvailable(UpdateInfo update, Runnable postpone, Runnable accept);
    void showInstallPermission(Runnable openSettings);
    void showError(String message, Runnable openReleases);
}
