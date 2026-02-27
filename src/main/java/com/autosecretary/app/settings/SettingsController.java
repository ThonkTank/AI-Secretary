package com.autosecretary.app.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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

public class SettingsController {

    private static final int OPTION_RESTORE_BACKUP = 0;
    private static final int OPTION_MANUAL_BACKUP = 1;
    private static final int OPTION_FACTORY_RESET = 2;
    private static final int OPTION_ABOUT = 3;

    @FunctionalInterface
    private interface BackgroundTask {
        boolean execute();
    }

    @FunctionalInterface
    private interface FileProducingTask {
        File execute();
    }

    private final Context context;
    private final SettingsDataService settingsDataService;
    private final ExecutorService executorService;
    private final Runnable onDataChanged;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SettingsController(@NonNull Context context, @NonNull SettingsDataService settingsDataService,
                              @NonNull Runnable onDataChanged, @NonNull ExecutorService executorService) {
        this.context = context;
        this.settingsDataService = settingsDataService;
        this.executorService = executorService;
        this.onDataChanged = onDataChanged;
    }

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

    public void showSettingsMenu() {
        String[] options = {
                context.getString(R.string.settings_option_restore_backup),
                context.getString(R.string.settings_option_manual_backup),
                context.getString(R.string.settings_option_factory_reset),
                context.getString(R.string.settings_option_about)
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case OPTION_RESTORE_BACKUP: showBackupRestoreDialog(); break;
                        case OPTION_MANUAL_BACKUP:  createManualBackup();       break;
                        case OPTION_FACTORY_RESET:  confirmFactoryReset();      break;
                        case OPTION_ABOUT:          showAboutDialog();          break;
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
        String lastChanged = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                .format(new Date(file.lastModified()));
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
