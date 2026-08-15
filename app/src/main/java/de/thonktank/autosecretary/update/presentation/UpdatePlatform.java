package de.thonktank.autosecretary.update.presentation;

import de.thonktank.autosecretary.update.application.VerifiedUpdate;

/** Navigation port for Android package installation and external release pages. */
public interface UpdatePlatform {
    boolean canInstallPackages();
    boolean openInstaller(VerifiedUpdate update);
    void openInstallSettings();
    void openReleases();
}
