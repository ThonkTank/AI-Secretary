package com.autosecretary.app.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.autosecretary.BuildConfig;
import com.autosecretary.R;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Activity-facing self-update workflow.
 *
 * <p>Network and release parsing live in {@link GitHubReleaseUpdateClient}; this class owns
 * lifecycle checks, user-visible dialogs, install-source permission routing, and the installer
 * intent.</p>
 */
public class UpdateChecker {

    private static final String LOG_TAG = "UpdateChecker";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";
    private static final String APK_FILE_NAME = "update.apk";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    private final WeakReference<Activity> activityRef;
    private final ExecutorService backgroundExecutor;
    private final UpdateClient updateClient;

    public UpdateChecker(Activity activity, ExecutorService executor) {
        this(activity, executor, new GitHubReleaseUpdateClient());
    }

    UpdateChecker(Activity activity, ExecutorService executor, UpdateClient updateClient) {
        this.activityRef = new WeakReference<>(activity);
        this.backgroundExecutor = executor;
        this.updateClient = updateClient;
    }

    /**
     * Startup check: silent when no update is available or when GitHub/network checks fail.
     */
    public void checkForUpdate() {
        checkForUpdate(false);
    }

    /**
     * Manual check from settings: reports the up-to-date or failure state to the user.
     */
    public void checkForUpdateManually() {
        checkForUpdate(true);
    }

    private void checkForUpdate(boolean manual) {
        backgroundExecutor.execute(() -> {
            try {
                Activity activity = getAliveActivity();
                if (activity == null) {
                    return;
                }
                AvailableUpdate update = updateClient.fetchLatestUpdate();
                if (update.versionCode() > BuildConfig.VERSION_CODE) {
                    activity.runOnUiThread(() -> showUpdateDialog(update));
                } else if (manual) {
                    activity.runOnUiThread(this::showUpToDateDialog);
                }
            } catch (Exception e) {
                if (manual) {
                    Activity activity = getAliveActivity();
                    if (activity != null) {
                        activity.runOnUiThread(() -> showCheckFailedDialog(e));
                    }
                } else {
                    Log.d(LOG_TAG, "Version check failed", e);
                }
            }
        });
    }

    private Activity getAliveActivity() {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
    }

    private void showUpdateDialog(AvailableUpdate update) {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(activity.getString(R.string.update_available_message, update.versionCode()))
                .setPositiveButton(R.string.update_action_install, (dialog, which) -> downloadAndInstall(update))
                .setNegativeButton(R.string.update_action_later, null)
                .show();
    }

    private void showUpToDateDialog() {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_no_update_title)
                .setMessage(R.string.update_no_update_message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    private void downloadAndInstall(AvailableUpdate update) {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            showInstallPermissionDialog();
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                Activity aliveActivity = getAliveActivity();
                if (aliveActivity == null) {
                    return;
                }
                File apkFile = updateClient.downloadApk(update, new File(aliveActivity.getCacheDir(), APK_FILE_NAME));
                aliveActivity.runOnUiThread(() -> installApk(apkFile));
            } catch (Exception e) {
                Log.e(LOG_TAG, "APK download/install failed", e);
                Activity aliveActivity = getAliveActivity();
                if (aliveActivity != null) {
                    aliveActivity.runOnUiThread(() -> showDownloadErrorDialog(e));
                }
            }
        });
    }

    private void showInstallPermissionDialog() {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setPositiveButton(R.string.update_install_permission_action, (dialog, which) ->
                        openUnknownSourcesSettings(activity))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void openUnknownSourcesSettings(Activity activity) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "Failed to open unknown app source settings", e);
            showDownloadErrorDialog(e);
        }
    }

    private void installApk(File apkFile) {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }

        try {
            activity.startActivity(buildInstallIntent(activity, apkFile));
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "Failed to find app installer", e);
            showDownloadErrorDialog(e);
        }
    }

    Intent buildInstallIntent(Activity activity, File apkFile) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + FILE_PROVIDER_SUFFIX,
                apkFile
        );
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void showCheckFailedDialog(Exception error) {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_check_failed_title)
                .setMessage(activity.getString(R.string.update_check_failed_message, errorDetail(activity, error)))
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    private void showDownloadErrorDialog(Exception error) {
        Activity activity = getAliveActivity();
        if (activity == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_download_failed_title)
                .setMessage(activity.getString(R.string.update_download_failed_message, errorDetail(activity, error)))
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    private String errorDetail(Activity activity, Exception error) {
        return Objects.requireNonNullElse(
                error.getMessage(),
                activity.getString(R.string.update_download_failed_unknown)
        );
    }
}
