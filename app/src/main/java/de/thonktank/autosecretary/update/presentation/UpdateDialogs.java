package de.thonktank.autosecretary.update.presentation;

import de.thonktank.autosecretary.update.domain.UpdateInfo;

/** UI-shell port for update prompts. Implementations own all Android dialog details. */
public interface UpdateDialogs {
    void showAvailable(UpdateInfo update, Runnable postpone, Runnable accept, Runnable dismiss);
    void showInstallPermission(Runnable openSettings, Runnable dismiss);
    void showError(String message, Runnable openReleases, Runnable dismiss);
}
