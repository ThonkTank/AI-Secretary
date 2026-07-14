package com.autosecretary.app.settings;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.autosecretary.BuildConfig;
import com.autosecretary.R;
import com.autosecretary.shared.ClaudeApiKeyStore;
import com.autosecretary.shared.ClaudeEndpointStore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Controller for application settings, including schedule configuration, backup/restore and factory reset operations.
 *
 * This class manages the settings UI menu and delegates data operations to {@link SettingsDataService}.
 * All long-running operations (I/O, database access) are executed on a background thread, with
 * results posted back to the main thread via {@link Handler}.
 *
 * Typical usage:
 * <pre>
 *   SettingsController controller = new SettingsController(context, dataService, callbacks..., executor);
 *   controller.showSettingsMenu();  // Shows menu dialog on UI thread
 * </pre>
 */
public class SettingsController {

    /** Date format for displaying backup timestamps in the restore dialog */
    private static final SimpleDateFormat BACKUP_DATE_FORMATTER =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);

    private final Context context;
    private final SettingsDataService settingsDataService;
    private final ExecutorService executorService;
    /** Callback invoked on the main thread when data has changed (e.g., after restore or factory reset) */
    private final Runnable onDataChanged;
    /** Callback invoked to open the schedule configuration dialog */
    private final Runnable onShowScheduleConfig;
    /** Callback invoked to open the category-management dialog */
    private final Runnable onShowCategories;
    /** Callback invoked to open the category time-window dialog */
    private final Runnable onShowCategoryWindows;
    /** Callback invoked to check GitHub Releases for app updates */
    private final Runnable onCheckForUpdate;
    /** Callback invoked to open the cooking preferences dialog */
    private final Runnable onShowCookingPrefs;
    /** Handler for posting callbacks from background threads back to the main (UI) thread */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SettingsController(@NonNull Context context, @NonNull SettingsDataService settingsDataService,
                              @NonNull Runnable onDataChanged, @NonNull Runnable onShowScheduleConfig,
                              @NonNull Runnable onShowCategories,
                              @NonNull Runnable onShowCategoryWindows,
                              @NonNull Runnable onCheckForUpdate,
                              @NonNull Runnable onShowCookingPrefs,
                              @NonNull ExecutorService executorService) {
        this.context = context;
        this.settingsDataService = settingsDataService;
        this.executorService = executorService;
        this.onDataChanged = onDataChanged;
        this.onShowScheduleConfig = onShowScheduleConfig;
        this.onShowCategories = onShowCategories;
        this.onShowCategoryWindows = onShowCategoryWindows;
        this.onCheckForUpdate = onCheckForUpdate;
        this.onShowCookingPrefs = onShowCookingPrefs;
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
            int successMessageId, int failureMessageId, BooleanSupplier task) {
        executorService.execute(() -> {
            boolean success = task.getAsBoolean();
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
            int successMessageId, int failureMessageId, Supplier<File> task) {
        executorService.execute(() -> {
            File result = task.get();
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
        List<SettingsMenuOption> options = List.of(
                new SettingsMenuOption(R.string.settings_option_schedule_config, onShowScheduleConfig),
                new SettingsMenuOption(R.string.settings_option_categories, onShowCategories),
                new SettingsMenuOption(R.string.settings_option_category_windows, onShowCategoryWindows),
                new SettingsMenuOption(R.string.settings_option_api_key, this::showApiKeyDialog),
                new SettingsMenuOption(R.string.settings_option_check_update, onCheckForUpdate),
                new SettingsMenuOption(R.string.settings_option_cooking_prefs, onShowCookingPrefs),
                new SettingsMenuOption(R.string.settings_option_restore_backup, this::showBackupRestoreDialog),
                new SettingsMenuOption(R.string.settings_option_manual_backup, this::createManualBackup),
                new SettingsMenuOption(R.string.settings_option_factory_reset, this::confirmFactoryReset),
                new SettingsMenuOption(R.string.settings_option_about, this::showAboutDialog)
        );
        String[] labels = options.stream()
                .map(option -> context.getString(option.labelResId()))
                .toArray(String[]::new);

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_title)
                .setItems(labels, (dialog, which) -> runMenuAction(options, which))
                .show();
    }

    private void runMenuAction(List<SettingsMenuOption> options, int which) {
        if (which < 0 || which >= options.size()) {
            Log.w("SettingsController", "Unknown settings menu option: " + which);
            return;
        }
        options.get(which).action().run();
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
                .setNegativeButton(R.string.action_cancel, null)
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
                .setNegativeButton(R.string.action_cancel, null)
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
                .setNegativeButton(R.string.action_cancel, null)
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
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    /**
     * Lets the user store the Claude API key (encrypted via {@link ClaudeApiKeyStore}) and an
     * optional custom endpoint base URL. Leaving the key field blank keeps the existing key.
     */
    private void showApiKeyDialog() {
        View view = LayoutInflater.from(context).inflate(R.layout.settings_api_key_dialog, null);
        EditText keyInput = view.findViewById(R.id.SettingsApiKeyInput);
        EditText endpointInput = view.findViewById(R.id.SettingsApiKeyEndpoint);
        ClaudeEndpointStore endpointStore = new ClaudeEndpointStore(context);
        endpointInput.setText(endpointStore.getBaseUrl());

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_api_key_title)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (!key.isEmpty()) {
                        new ClaudeApiKeyStore(context).saveApiKey(key);
                    }
                    endpointStore.setBaseUrl(endpointInput.getText().toString().trim());
                    Toast.makeText(context, R.string.settings_api_key_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private record SettingsMenuOption(int labelResId, Runnable action) {}
}
