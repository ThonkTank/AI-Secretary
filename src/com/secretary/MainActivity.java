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
    private TextView updateStatus;
    private TextView versionText;
    private Button checkUpdateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views initialisieren
        updateStatus = findViewById(R.id.updateStatus);
        versionText = findViewById(R.id.versionText);
        checkUpdateButton = findViewById(R.id.checkUpdateButton);

        // Aktuelle Version anzeigen
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText.setText("Version " + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("Version unknown");
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
        updateStatus.setText("Checking for updates...");
        checkUpdateButton.setEnabled(false);

        UpdateChecker.checkForUpdates(this, new UpdateChecker.UpdateListener() {
            @Override
            public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
                updateStatus.setText("Update available: v" + version);
                checkUpdateButton.setEnabled(true);

                // Dialog anzeigen
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Update Available")
                    .setMessage("Version " + version + " is available!\n\n" + changelog)
                    .setPositiveButton("Download & Install", (dialog, which) -> {
                        updateStatus.setText("Downloading update...");
                        UpdateInstaller.downloadAndInstall(MainActivity.this, downloadUrl, version);
                        Toast.makeText(MainActivity.this, "Download started...", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Later", null)
                    .show();
            }

            @Override
            public void onNoUpdateAvailable() {
                updateStatus.setText("You're up to date!");
                checkUpdateButton.setEnabled(true);
                Toast.makeText(MainActivity.this, "No updates available", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                updateStatus.setText("Error checking for updates");
                checkUpdateButton.setEnabled(true);
                Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
