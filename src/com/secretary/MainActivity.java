package com.secretary.helloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private TextView updateStatus;
    private TextView versionText;
    private Button checkUpdateButton;
    private AppLogger logger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Logger initialisieren
        logger = AppLogger.getInstance(this);
        logger.info(TAG, "=== Application started ===");

        // Views initialisieren
        updateStatus = findViewById(R.id.updateStatus);
        versionText = findViewById(R.id.versionText);
        checkUpdateButton = findViewById(R.id.checkUpdateButton);

        // Aktuelle Version anzeigen
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText("Version " + pInfo.versionName);
            logger.info(TAG, "App version: " + pInfo.versionName + " (code: " + pInfo.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Version unknown");
            logger.error(TAG, "Could not get package info", e);
        }

        // Update-Button Click Listener
        checkUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForUpdates();
            }
        });

        // Automatisch bei Start prüfen
        checkForUpdates();
    }

    private void checkForUpdates() {
        logger.info(TAG, "User initiated update check");
        updateStatus.setText("Checking for updates...");
        checkUpdateButton.setEnabled(false);

        UpdateChecker.checkForUpdates(this, new UpdateChecker.UpdateListener() {
            @Override
            public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
                logger.info(TAG, "Update dialog shown to user for version " + version);
                updateStatus.setText("Update available: v" + version);
                checkUpdateButton.setEnabled(true);

                // Dialog anzeigen
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Update Available")
                    .setMessage("Version " + version + " is available!\n\n" + changelog)
                    .setPositiveButton("Download & Install", (dialog, which) -> {
                        logger.info(TAG, "User accepted update download");
                        updateStatus.setText("Downloading update...");
                        UpdateInstaller.downloadAndInstall(MainActivity.this, downloadUrl, version);
                        Toast.makeText(MainActivity.this, "Download started...", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Later", (dialog, which) -> {
                        logger.info(TAG, "User declined update");
                    })
                    .show();
            }

            @Override
            public void onNoUpdateAvailable() {
                logger.info(TAG, "No update available - user informed");
                updateStatus.setText("You're up to date!");
                checkUpdateButton.setEnabled(true);
                Toast.makeText(MainActivity.this, "No updates available", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                logger.error(TAG, "Update check failed: " + error);
                updateStatus.setText("Error checking for updates");
                checkUpdateButton.setEnabled(true);
                Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
