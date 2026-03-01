package com.autosecretary.app.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.autosecretary.BuildConfig;
import com.autosecretary.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Controller for application settings, including schedule configuration, backup/restore and factory reset operations.
 *
 * This class manages the settings UI menu and delegates data operations to {@link SettingsDataService}.
 * All long-running operations (I/O, database access) are executed on a background thread, with
 * results posted back to the main thread via {@link Handler}.
 *
 * Typical usage:
 * <pre>
 *   SettingsController controller = new SettingsController(context, dataService, onDataChanged, onShowScheduleConfig, executor);
 *   controller.showSettingsMenu();  // Shows menu dialog on UI thread
 * </pre>
 */
public class SettingsController {

    /** Option indices must match the order of strings in {@link #showSettingsMenu()} */
    private static final int OPTION_SCHEDULE_CONFIG  = 0;
    private static final int OPTION_RESTORE_BACKUP   = 1;
    private static final int OPTION_MANUAL_BACKUP    = 2;
    private static final int OPTION_FACTORY_RESET    = 3;
    private static final int OPTION_ABOUT            = 4;

    /** Date format for displaying backup timestamps in the restore dialog */
    private static final SimpleDateFormat BACKUP_DATE_FORMATTER =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);

    /**
     * Functional interface for long-running tasks that return a boolean success flag.
     * Used by {@link #runInBackground(int, int, BackgroundTask)} to execute work off the main thread.
     */
    @FunctionalInterface
    private interface BackgroundTask {
        /**
         * Execute the task. The caller is responsible for exception handling and logging.
         * @return true if the operation succeeded, false otherwise
         */
        boolean execute();
    }

    /**
     * Functional interface for tasks that produce a file result (e.g., backup creation).
     * Used by {@link #executeBackgroundFileTask(int, int, FileProducingTask)} to execute work
     * off the main thread without triggering {@link #onDataChanged}.
     */
    @FunctionalInterface
    private interface FileProducingTask {
        /**
         * Execute the task and produce a File result.
         * @return the resulting file, or null if the operation failed
         */
        File execute();
    }

    private final Context context;
    private final SettingsDataService settingsDataService;
    private final ExecutorService executorService;
    /** Callback invoked on the main thread when data has changed (e.g., after restore or factory reset) */
    private final Runnable onDataChanged;
    /** Callback invoked to open the schedule configuration dialog */
    private final Runnable onShowScheduleConfig;
    /** Handler for posting callbacks from background threads back to the main (UI) thread */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SettingsController(@NonNull Context context, @NonNull SettingsDataService settingsDataService,
                              @NonNull Runnable onDataChanged, @NonNull Runnable onShowScheduleConfig,
                              @NonNull ExecutorService executorService) {
        this.context = context;
        this.settingsDataService = settingsDataService;
        this.executorService = executorService;
        this.onDataChanged = onDataChanged;
        this.onShowScheduleConfig = onShowScheduleConfig;
    }

    /**
     * Execute a task on the background executor, showing a toast result on the main thread.
     *
     * On success, {@link #onDataChanged} is invoked to notify listeners that the application
     * state has changed.
     *
     * @param successMessageId   resource ID for the toast shown on success
     * @param failureMessageId   resource ID for the toast shown on failure
     * @param task               the operation to execute; should return true if successful
     */
    private void runInBackground(
            int successMessageId, int failureMessageId, BackgroundTask task) {
        executorService.execute(() -> {
            boolean success = task.execute();
            mainHandler.post(() -> {
                Toast.makeText(context, success ? successMessageId : failureMessageId,
                        Toast.LENGTH_LONG).show();
                if (success) {
                    onDataChanged.run();
                }
            });
        });
    }

    /**
     * Execute a file-producing task on the background executor.
     *
     * This method does NOT invoke {@link #onDataChanged} on success. Use this for operations
     * that don't require app state refresh (e.g., creating a backup that doesn't modify
     * the active database).
     *
     * @param successMessageId   resource ID for the toast shown when task produces a non-null File
     * @param failureMessageId   resource ID for the toast shown when task produces null
     * @param task               the operation to execute; should return a File on success, null on failure
     */
    private void executeBackgroundFileTask(
            int successMessageId, int failureMessageId, FileProducingTask task) {
        executorService.execute(() -> {
            File result = task.execute();
            mainHandler.post(() ->
                    Toast.makeText(context, result != null ? successMessageId : failureMessageId,
                            Toast.LENGTH_SHORT).show()
            );
        });
    }

    /**
     * Display the main settings menu dialog listing all options defined by the {@code OPTION_*} constants.
     *
     * Call this from a UI context (e.g., when user taps Settings). The menu is modal and blocks
     * interaction with the underlying activity until dismissed.
     */
    public void showSettingsMenu() {
        String[] options = {
                context.getString(R.string.settings_option_schedule_config),
                context.getString(R.string.settings_option_restore_backup),
                context.getString(R.string.settings_option_manual_backup),
                context.getString(R.string.settings_option_factory_reset),
                context.getString(R.string.settings_option_about),
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case OPTION_SCHEDULE_CONFIG: onShowScheduleConfig.run();  break;
                        case OPTION_RESTORE_BACKUP:  showBackupRestoreDialog();   break;
                        case OPTION_MANUAL_BACKUP:   createManualBackup();        break;
                        case OPTION_FACTORY_RESET:   confirmFactoryReset();       break;
                        case OPTION_ABOUT:           showAboutDialog();           break;
                        default:
                            Log.w("SettingsController", "Unknown settings menu option: " + which);
                    }
                })
                .show();
    }

    private void showBackupRestoreDialog() {
        File[] backups = settingsDataService.listBackups();
        if (backups.length == 0) {
            Toast.makeText(context, R.string.settings_no_backups, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] backupNames = Arrays.stream(backups)
                .map(this::formatBackupName)
                .toArray(String[]::new);

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_restore_dialog_title)
                .setItems(backupNames, (dialog, which) -> confirmRestore(backups[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String formatBackupName(@NonNull File file) {
        String lastChanged = BACKUP_DATE_FORMATTER.format(new Date(file.lastModified()));
        return context.getString(R.string.settings_backup_item_format, file.getName(), lastChanged);
    }

    private void confirmRestore(@NonNull File backupFile) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_restore_confirm_title)
                .setMessage(R.string.settings_restore_confirm_message)
                .setPositiveButton(R.string.settings_restore_action, (dialog, which) ->
                        runInBackground(
                                R.string.settings_restore_success,
                                R.string.settings_restore_failure,
                                () -> settingsDataService.restoreBackup(backupFile)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void createManualBackup() {
        executeBackgroundFileTask(
                R.string.settings_backup_success,
                R.string.settings_backup_failure,
                settingsDataService::createManualBackup);
    }

    private void confirmFactoryReset() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_reset_confirm_title)
                .setMessage(R.string.settings_reset_confirm_message)
                .setPositiveButton(R.string.settings_reset_action, (dialog, which) ->
                        runInBackground(
                                R.string.settings_reset_success,
                                R.string.settings_reset_failure,
                                settingsDataService::factoryReset))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAboutDialog() {
        String aboutText = context.getString(
                R.string.settings_about_message,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
        );

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_about_title)
                .setMessage(aboutText)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

}
