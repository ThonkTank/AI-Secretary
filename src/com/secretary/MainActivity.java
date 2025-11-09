package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private AppLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "=== Application onCreate started ===");

        // Logger initialisieren (keine Permissions mehr nötig!)
        logger = AppLogger.getInstance(this);
        logger.info(TAG, "=== Application started ===");

        // Aktuelle Version loggen
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            logger.info(TAG, "App version: " + pInfo.versionName + " (code: " + pInfo.versionCode + ")");
            Log.i(TAG, "App version: " + pInfo.versionName + " (code: " + pInfo.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            logger.error(TAG, "Could not get package info", e);
        }

        // Settings Button ZUERST initialisieren
        Button settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                logger.info(TAG, "Settings button clicked");
                showSettingsDialog();
            });
            Log.i(TAG, "Settings button initialized successfully");
            logger.info(TAG, "Settings button initialized successfully");
        } else {
            Log.e(TAG, "Settings button NOT FOUND in layout!");
            logger.error(TAG, "Settings button NOT FOUND in layout!");
        }

        // LOGS ALS LETZTES ANZEIGEN (damit alle vorherigen Logs dabei sind)
        TextView mainLogsTextView = findViewById(R.id.mainLogsTextView);
        if (mainLogsTextView != null) {
            List<String> logs = logger.readLogs();
            if (logs.isEmpty()) {
                mainLogsTextView.setText("=== NO LOGS YET ===\n\nLog file path: " + logger.getLogFilePath() + "\n\nIf you see this, the app started successfully but no logs were written.");
            } else {
                StringBuilder logText = new StringBuilder();
                logText.append("=== APP LOGS ===\n\n");
                for (String line : logs) {
                    logText.append(line).append("\n");
                }
                mainLogsTextView.setText(logText.toString());
            }
            Log.i(TAG, "Main logs display initialized with " + logs.size() + " lines");
            logger.info(TAG, "Main logs display initialized with " + logs.size() + " lines");
        } else {
            Log.e(TAG, "Main logs TextView NOT FOUND!");
            logger.error(TAG, "Main logs TextView NOT FOUND!");
        }
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Views im Dialog
        TextView versionText = dialogView.findViewById(R.id.settingsVersionText);
        TextView updateStatus = dialogView.findViewById(R.id.settingsUpdateStatus);
        Button checkUpdateButton = dialogView.findViewById(R.id.settingsCheckUpdateButton);
        Button closeButton = dialogView.findViewById(R.id.settingsCloseButton);

        // Version anzeigen
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText("Version " + pInfo.versionName + " (Build " + pInfo.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Version unknown");
        }

        // Initial: Noch nicht geprüft
        updateStatus.setText("Not checked yet");

        // Update-Check Button
        checkUpdateButton.setOnClickListener(v -> {
            logger.info(TAG, "User initiated update check from settings");
            updateStatus.setText("Checking for updates...");
            checkUpdateButton.setEnabled(false);

            UpdateChecker.checkForUpdates(this, new UpdateChecker.UpdateListener() {
                @Override
                public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
                    logger.info(TAG, "Update dialog shown to user for version " + version);
                    updateStatus.setText("Update available: v" + version);
                    checkUpdateButton.setEnabled(true);

                    // Update-Dialog anzeigen
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update Available")
                        .setMessage("Version " + version + " is available!\n\n" + changelog)
                        .setPositiveButton("Download & Install", (d, which) -> {
                            logger.info(TAG, "User accepted update download");
                            updateStatus.setText("Downloading...");
                            UpdateInstaller.downloadAndInstall(MainActivity.this, downloadUrl, version);
                            Toast.makeText(MainActivity.this, "Download started. Check notifications.", Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("Later", (d, which) -> {
                            logger.info(TAG, "User declined update");
                        })
                        .show();
                }

                @Override
                public void onNoUpdateAvailable() {
                    logger.info(TAG, "No update available");
                    updateStatus.setText("You're up to date!");
                    checkUpdateButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "No updates available", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    logger.error(TAG, "Update check failed: " + error);
                    updateStatus.setText("Error: " + error);
                    checkUpdateButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Error checking updates", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // View Logs Button
        Button viewLogsButton = dialogView.findViewById(R.id.settingsViewLogsButton);
        viewLogsButton.setOnClickListener(v -> {
            logger.info(TAG, "User opened log viewer");
            showLogsDialog();
        });

        // Close Button
        closeButton.setOnClickListener(v -> {
            logger.info(TAG, "Settings dialog closed");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showLogsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logs, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Views im Dialog
        TextView logsTextView = dialogView.findViewById(R.id.logsTextView);
        Button copyButton = dialogView.findViewById(R.id.logsCopyButton);
        Button closeButton = dialogView.findViewById(R.id.logsCloseButton);

        // Logs laden und anzeigen
        List<String> logs = logger.readLogs();
        if (logs.isEmpty()) {
            logsTextView.setText("No logs available yet.\n\nLog file path: " + logger.getLogFilePath());
        } else {
            StringBuilder logText = new StringBuilder();
            for (String line : logs) {
                logText.append(line).append("\n");
            }
            logsTextView.setText(logText.toString());
        }

        // Copy Button
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("App Logs", logsTextView.getText().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
            logger.info(TAG, "User copied logs to clipboard");
        });

        // Close Button
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
