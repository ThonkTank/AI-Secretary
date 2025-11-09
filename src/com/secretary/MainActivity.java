package com.secretary.helloworld;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private AppLogger logger;
    private boolean permissionsGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "=== Application onCreate started ===");

        // Storage Permissions anfragen (für Android 6.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                Log.i(TAG, "Requesting storage permissions...");
                requestPermissions(
                    new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    },
                    PERMISSION_REQUEST_CODE
                );
            } else {
                permissionsGranted = true;
                initializeApp();
            }
        } else {
            permissionsGranted = true;
            initializeApp();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Log.i(TAG, "Storage permissions granted!");
                permissionsGranted = true;
                initializeApp();
            } else {
                Log.w(TAG, "Storage permissions denied!");
                Toast.makeText(this, "Storage permissions required for logging", Toast.LENGTH_LONG).show();
                // App trotzdem initialisieren, aber ohne Logging
                permissionsGranted = false;
                initializeApp();
            }
        }
    }

    private void initializeApp() {
        // Logger initialisieren (nur wenn Permissions vorhanden)
        if (permissionsGranted) {
            logger = AppLogger.getInstance(this);
            logger.info(TAG, "=== Application started with permissions ===");

            // Aktuelle Version loggen
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                logger.info(TAG, "App version: " + pInfo.versionName + " (code: " + pInfo.versionCode + ")");
            } catch (PackageManager.NameNotFoundException e) {
                logger.error(TAG, "Could not get package info", e);
            }
        } else {
            Log.w(TAG, "App started WITHOUT storage permissions - logging disabled");
        }

        // Settings Button
        Button settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                if (logger != null) {
                    logger.info(TAG, "Settings button clicked");
                }
                showSettingsDialog();
            });
            Log.i(TAG, "Settings button initialized successfully");
        } else {
            Log.e(TAG, "Settings button NOT FOUND in layout!");
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
            if (logger != null) {
                logger.info(TAG, "User initiated update check from settings");
            }
            updateStatus.setText("Checking for updates...");
            checkUpdateButton.setEnabled(false);

            UpdateChecker.checkForUpdates(this, new UpdateChecker.UpdateListener() {
                @Override
                public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
                    if (logger != null) {
                        logger.info(TAG, "Update dialog shown to user for version " + version);
                    }
                    updateStatus.setText("Update available: v" + version);
                    checkUpdateButton.setEnabled(true);

                    // Update-Dialog anzeigen
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update Available")
                        .setMessage("Version " + version + " is available!\n\n" + changelog)
                        .setPositiveButton("Download & Install", (d, which) -> {
                            if (logger != null) {
                                logger.info(TAG, "User accepted update download");
                            }
                            updateStatus.setText("Downloading...");
                            UpdateInstaller.downloadAndInstall(MainActivity.this, downloadUrl, version);
                            Toast.makeText(MainActivity.this, "Download started. Check notifications.", Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("Later", (d, which) -> {
                            if (logger != null) {
                                logger.info(TAG, "User declined update");
                            }
                        })
                        .show();
                }

                @Override
                public void onNoUpdateAvailable() {
                    if (logger != null) {
                        logger.info(TAG, "No update available");
                    }
                    updateStatus.setText("You're up to date!");
                    checkUpdateButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "No updates available", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    if (logger != null) {
                        logger.error(TAG, "Update check failed: " + error);
                    }
                    updateStatus.setText("Error: " + error);
                    checkUpdateButton.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Error checking updates", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Close Button
        closeButton.setOnClickListener(v -> {
            if (logger != null) {
                logger.info(TAG, "Settings dialog closed");
            }
            dialog.dismiss();
        });

        dialog.show();
    }
}
