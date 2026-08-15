package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.AlertDialog;

import de.thonktank.autosecretary.update.domain.UpdateInfo;
import de.thonktank.autosecretary.update.presentation.UpdateDialogs;

import java.util.Locale;

/** Android dialog adapter for the update presentation ports. */
final class AndroidUpdateDialogs implements UpdateDialogs {
    private final Activity activity;

    AndroidUpdateDialogs(Activity activity) {
        this.activity = activity;
    }

    @Override public void showAvailable(UpdateInfo update, Runnable postpone, Runnable accept) {
        if (update == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available_title, update.versionName))
                .setMessage(activity.getString(R.string.update_available_message,
                        readableSize(update.sizeBytes)))
                .setNegativeButton(R.string.update_later, (dialog, which) -> postpone.run())
                .setPositiveButton(R.string.update_now, (dialog, which) -> accept.run())
                .show();
    }

    @Override public void showInstallPermission(Runnable openSettings) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle(R.string.unknown_sources_title)
                .setMessage(R.string.unknown_sources_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_install_settings,
                        (dialog, which) -> openSettings.run())
                .show();
    }

    @Override public void showError(String message, Runnable openReleases) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle(R.string.error_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_github,
                        (dialog, which) -> openReleases.run())
                .show();
    }

    private static String readableSize(long bytes) {
        return String.format(Locale.GERMANY, "%.1f MB", bytes / 1024d / 1024d);
    }
}
