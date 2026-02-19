package controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

import data.Constants;

/**
 * Verwaltet Settings-Menu-Aktionen.
 *
 * Bietet Optionen fuer:
 * - Backup wiederherstellen
 * - Alle Daten loeschen (Factory Reset)
 * - Ueber AutoSecretary (Version anzeigen)
 */
public class SettingsManager {

    private final Context context;
    private final Runnable onDataChanged;

    /**
     * @param context Activity-Context
     * @param onDataChanged Callback das aufgerufen wird wenn Daten geaendert wurden (z.B. Restore/Reset)
     */
    public SettingsManager(Context context, Runnable onDataChanged) {
        this.context = context;
        this.onDataChanged = onDataChanged;
    }

    /**
     * Zeigt das Settings-Popup-Menu an.
     */
    public void showSettingsMenu() {
        String[] options = {
            "Backup wiederherstellen",
            "Manuelles Backup erstellen",
            "Alle Daten loeschen",
            "Ueber AutoSecretary"
        };

        new AlertDialog.Builder(context)
            .setTitle("Einstellungen")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: showBackupRestoreDialog(); break;
                    case 1: createManualBackup(); break;
                    case 2: confirmFactoryReset(); break;
                    case 3: showAboutDialog(); break;
                }
            })
            .show();
    }

    /**
     * Zeigt Dialog mit verfuegbaren Backups zur Wiederherstellung.
     */
    private void showBackupRestoreDialog() {
        if (backups.length == 0) {
            Toast.makeText(context, "Keine Backups vorhanden", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] backupNames = Arrays.stream(backups)
            .map(this::formatBackupName)
            .toArray(String[]::new);

        new AlertDialog.Builder(context)
            .setTitle("Backup wiederherstellen")
            .setItems(backupNames, (dialog, which) -> {
                confirmRestore(backups[which]);
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    /**
     * Formatiert Backup-Dateiname fuer Anzeige.
     * backup_v30_20260205_143022.db -> "Version 30 (05.02.2026 14:30)"
     */
    private String formatBackupName(File file) {
        String name = file.getName();
        try {
            // Extrahiere Version und Timestamp aus "backup_v30_20260205_143022.db"
            String[] parts = name.replace("backup_v", "").replace(".db", "").split("_");
            String version = parts[0];
            String date = parts[1];
            String time = parts[2];

            String formattedDate = date.substring(6, 8) + "." + date.substring(4, 6) + "." + date.substring(0, 4);
            String formattedTime = time.substring(0, 2) + ":" + time.substring(2, 4);

            return "Version " + version + " (" + formattedDate + " " + formattedTime + ")";
        } catch (Exception e) {
            return name;
        }
    }

    /**
     * Bestaetigung vor Wiederherstellung.
     */
    private void confirmRestore(File backupFile) {
        new AlertDialog.Builder(context)
            .setTitle("Backup wiederherstellen?")
            .setMessage("Alle aktuellen Daten werden durch das Backup ersetzt.\n\nDie App wird danach neu gestartet.")
            .setPositiveButton("Wiederherstellen", (d, w) -> {
                // Erst aktuellen Stand sichern

                
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    /**
     * Erstellt manuelles Backup.
     */
    private void createManualBackup() {
        if (backup != null) {
            Toast.makeText(context, "Backup erstellt", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Backup fehlgeschlagen", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Bestaetigung vor Factory Reset.
     */
    private void confirmFactoryReset() {
        new AlertDialog.Builder(context)
            .setTitle("Alle Daten loeschen?")
            .setMessage("ACHTUNG: Alle Daten werden unwiderruflich geloescht!\n\n" +
                        "Ein Backup wird automatisch erstellt.\n\n" +
                        "Die App wird danach neu gestartet.")
            .setPositiveButton("Loeschen", (d, w) -> {

                // Datenbank loeschen
                context.deleteDatabase(Constants.DB_NAME);

                Toast.makeText(context, "Daten geloescht. Bitte App neu starten.",
                    Toast.LENGTH_LONG).show();

                if (onDataChanged != null) {
                    onDataChanged.run();
                }
            })
            .setNegativeButton("Abbrechen", null)
            .show();
    }

    /**
     * Zeigt About-Dialog mit Versionsinformationen.
     */
    private void showAboutDialog() {
        String version = "Unbekannt";
        try {
            PackageInfo pInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            version = pInfo.versionName + " (Build " + pInfo.versionCode + ")";
        } catch (Exception e) {
            // Ignore
        }

        new AlertDialog.Builder(context)
            .setTitle("AutoSecretary")
            .setMessage("Version: " + version + "\n\n" +
                        "Dein persoenlicher Assistent fuer\n" +
                        "Aufgaben, Budget und Ernaehrung.\n\n" +
                        "DB-Schema: v" + Constants.DB_VERSION)
            .setPositiveButton("OK", null)
            .show();
    }
}
