package de.thonktank.autosecretary;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.update.application.VerifiedUpdate;
import de.thonktank.autosecretary.update.infrastructure.UpdateInstaller;
import de.thonktank.autosecretary.update.presentation.UpdatePlatform;

/** Activity-bound adapter for package installation and update navigation. */
final class AndroidUpdatePlatform implements UpdatePlatform {
    private final Activity activity;
    private final ActivityResultLauncher<Intent> installPermission;
    private final UpdateInstaller installer;
    private final AppLogger logger;
    private final String repositoryOwner;
    private final String repositoryName;

    AndroidUpdatePlatform(Activity activity, ActivityResultLauncher<Intent> installPermission,
                          UpdateInstaller installer, AppLogger logger,
                          String repositoryOwner, String repositoryName) {
        this.activity = activity;
        this.installPermission = installPermission;
        this.installer = installer;
        this.logger = logger;
        this.repositoryOwner = repositoryOwner;
        this.repositoryName = repositoryName;
    }

    @Override public boolean canInstallPackages() {
        return installer.canInstallPackages(activity);
    }

    @Override public boolean openInstaller(VerifiedUpdate update) {
        try {
            return launch(installer.installerIntent(activity, update),
                    "Could not open Android installer");
        } catch (RuntimeException error) {
            logger.error("Updater", "Could not prepare Android installer", error);
            return false;
        }
    }

    @Override public void openInstallSettings() {
        try {
            installPermission.launch(installer.settingsIntent(activity));
        } catch (RuntimeException error) {
            logger.error("Updater", "Could not open Android install settings", error);
        }
    }

    @Override public void openReleases() {
        try {
            launch(installer.releasesIntent(repositoryOwner, repositoryName),
                    "Could not open GitHub releases");
        } catch (RuntimeException error) {
            logger.error("Updater", "Could not prepare GitHub releases", error);
        }
    }

    private boolean launch(Intent intent, String failureMessage) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            logger.error("Updater", failureMessage, error);
            return false;
        }
    }
}
