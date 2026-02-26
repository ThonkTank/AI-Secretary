package com.autosecretary.app.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.autosecretary.BuildConfig;
import com.autosecretary.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsController {

    private final Context context;
    private final SettingsDataService settingsDataService;
    private final ExecutorService executorService;
    private final Runnable onDataChanged;

    public SettingsController(@NonNull Context context, @NonNull Runnable onDataChanged) {
        this.context = context;
        this.settingsDataService = new SettingsDataService(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.onDataChanged = onDataChanged;
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
                    if (which == 0) {
                        showBackupRestoreDialog();
                    } else if (which == 1) {
                        createManualBackup();
                    } else if (which == 2) {
                        confirmFactoryReset();
                    } else {
                        showAboutDialog();
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

        String[] backupNames = new String[backups.length];
        for (int i = 0; i < backups.length; i++) {
            backupNames[i] = formatBackupName(backups[i]);
        }

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
                .setPositiveButton(R.string.settings_restore_action, (dialog, which) -> executorService.execute(() -> {
                    boolean success = settingsDataService.restoreBackup(backupFile);
                    runOnUiThread(() -> {
                        int messageId = success
                                ? R.string.settings_restore_success
                                : R.string.settings_restore_failure;
                        Toast.makeText(context, messageId, Toast.LENGTH_LONG).show();
                        if (success) {
                            onDataChanged.run();
                        }
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void createManualBackup() {
        executorService.execute(() -> {
            File backup = settingsDataService.createManualBackup();
            runOnUiThread(() -> {
                int messageId = backup != null
                        ? R.string.settings_backup_success
                        : R.string.settings_backup_failure;
                Toast.makeText(context, messageId, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmFactoryReset() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_reset_confirm_title)
                .setMessage(R.string.settings_reset_confirm_message)
                .setPositiveButton(R.string.settings_reset_action, (dialog, which) -> executorService.execute(() -> {
                    boolean success = settingsDataService.factoryReset();
                    runOnUiThread(() -> {
                        int messageId = success
                                ? R.string.settings_reset_success
                                : R.string.settings_reset_failure;
                        Toast.makeText(context, messageId, Toast.LENGTH_LONG).show();
                        if (success) {
                            onDataChanged.run();
                        }
                    });
                }))
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

    private void runOnUiThread(@NonNull Runnable runnable) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(runnable);
        }
    }
}
