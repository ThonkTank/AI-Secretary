package com.aisecretary.taskmaster;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.aisecretary.taskmaster.utils.BackupManager;

import java.io.File;

/**
 * BackupActivity - Backup and Restore functionality
 *
 * Features:
 * - Export database to JSON
 * - Import database from JSON
 * - Backup info display
 * - Phase 8.3
 */
public class BackupActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_EXPORT = 1001;
    private static final int REQUEST_CODE_IMPORT = 1002;

    private TextView backupInfoText;
    private Button exportButton;
    private Button importButton;
    private Button importReplaceButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        // Find views
        backupInfoText = findViewById(R.id.backup_info_text);
        exportButton = findViewById(R.id.export_button);
        importButton = findViewById(R.id.import_button);
        importReplaceButton = findViewById(R.id.import_replace_button);

        // Set up buttons
        exportButton.setOnClickListener(v -> startExport());
        importButton.setOnClickListener(v -> startImport(false));
        importReplaceButton.setOnClickListener(v -> confirmReplace());

        // Update info
        updateBackupInfo();
    }

    /**
     * Update backup information display
     */
    private void updateBackupInfo() {
        StringBuilder info = new StringBuilder();
        info.append("💾 Backup & Restore\n\n");
        info.append("Export: Speichert alle Aufgaben als JSON-Datei\n");
        info.append("Import: Importiert Aufgaben zusätzlich zu vorhandenen\n");
        info.append("Ersetzen: Löscht vorhandene Daten vor Import\n\n");
        info.append("⚠️ Backup enthält:\n");
        info.append("• Alle Aufgaben und Details\n");
        info.append("• Wiederholungen und Streaks\n");
        info.append("• Kategorien und Ketten\n");
        info.append("• Erledigungs-Historie\n");

        backupInfoText.setText(info.toString());
    }

    /**
     * Start export process
     */
    private void startExport() {
        try {
            // Get Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            );

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            // Generate filename
            String filename = BackupManager.generateBackupFilename();
            File outputFile = new File(downloadsDir, filename);

            // Export
            BackupManager.ExportResult result = BackupManager.exportToJson(this, outputFile);

            if (result.success) {
                new AlertDialog.Builder(this)
                    .setTitle("✅ Export erfolgreich")
                    .setMessage(String.format(
                        "Backup erstellt:\n\n" +
                        "Datei: %s\n\n" +
                        "Aufgaben: %d\n" +
                        "Historie: %d Einträge\n\n" +
                        "Speicherort: Downloads-Ordner",
                        filename,
                        result.tasksExported,
                        result.historyExported
                    ))
                    .setPositiveButton("OK", null)
                    .show();
            } else {
                showError("Export fehlgeschlagen", result.error);
            }

        } catch (Exception e) {
            showError("Export Fehler", e.getMessage());
        }
    }

    /**
     * Start import process
     */
    private void startImport(boolean replaceExisting) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");

        try {
            startActivityForResult(intent, replaceExisting ? REQUEST_CODE_IMPORT + 1 : REQUEST_CODE_IMPORT);
        } catch (Exception e) {
            showError("Import Fehler", "Datei-Auswahl fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Confirm replace before import
     */
    private void confirmReplace() {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Warnung")
            .setMessage("Alle vorhandenen Aufgaben werden gelöscht!\n\nMöchten Sie fortfahren?")
            .setPositiveButton("Ja, ersetzen", (dialog, which) -> startImport(true))
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();

            if (fileUri != null) {
                boolean replaceExisting = (requestCode == REQUEST_CODE_IMPORT + 1);
                performImport(fileUri, replaceExisting);
            }
        }
    }

    /**
     * Perform import from file
     */
    private void performImport(Uri fileUri, boolean replaceExisting) {
        try {
            BackupManager.ImportResult result = BackupManager.importFromJson(
                this,
                fileUri,
                replaceExisting
            );

            if (result.success) {
                new AlertDialog.Builder(this)
                    .setTitle("✅ Import erfolgreich")
                    .setMessage(String.format(
                        "Backup wiederhergestellt:\n\n" +
                        "Aufgaben importiert: %d\n" +
                        "Historie: %d Einträge\n" +
                        "Übersprungen: %d\n\n" +
                        "Die App wird neu geladen.",
                        result.tasksImported,
                        result.historyImported,
                        result.tasksSkipped
                    ))
                    .setPositiveButton("OK", (dialog, which) -> {
                        // Return to MainActivity
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .show();
            } else {
                showError("Import fehlgeschlagen", result.error);
            }

        } catch (Exception e) {
            showError("Import Fehler", e.getMessage());
        }
    }

    /**
     * Show error dialog
     */
    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message != null ? message : "Unbekannter Fehler")
            .setPositiveButton("OK", null)
            .show();
    }
}
