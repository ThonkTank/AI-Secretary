package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private AppLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Logger initialisieren
        logger = AppLogger.getInstance(this);
        logger.info(TAG, "=== Application started ===");

        // Aktuelle Version loggen
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            logger.info(TAG, "App version: " + pInfo.versionName + " (code: " + pInfo.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            logger.error(TAG, "Could not get package info", e);
        }

        // Settings Button
        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> {
            logger.info(TAG, "Settings button clicked");
            showSettingsDialog();
        });
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

        // Close Button
        closeButton.setOnClickListener(v -> {
            logger.info(TAG, "Settings dialog closed");
            dialog.dismiss();
        });

        dialog.show();
    }
}
