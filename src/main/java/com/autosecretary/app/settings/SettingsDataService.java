package com.autosecretary.app.settings;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.autosecretary.database.AppDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class SettingsDataService {

    private static final String BACKUP_PREFIX = "backup_";

    private final Context appContext;

    public SettingsDataService(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public File[] listBackups() {
        File[] files = getBackupDirectory().listFiles((dir, name) ->
                name.startsWith(BACKUP_PREFIX) && name.endsWith(".db")
        );
        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }

    public File createManualBackup() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        File target = new File(getBackupDirectory(), BACKUP_PREFIX + timestamp + ".db");

        try {
            runCheckpoint();
            copyDatabaseFile(getDatabaseFile(), target);
            return target;
        } catch (IOException ex) {
            Log.e("SettingsDataService", "Backup failed", ex);
            return null;
        }
    }

    public boolean restoreBackup(@NonNull File backupFile) {
        try {
            AppDatabase.closeAndReset();
            clearSidecarFiles();
            copyDatabaseFile(backupFile, getDatabaseFile());
            AppDatabase.getInstance(appContext);
            return true;
        } catch (Exception ex) {
            Log.e("SettingsDataService", "Restore failed", ex);
            return false;
        }
    }

    public boolean factoryReset() {
        try {
            File backup = createManualBackup();
            if (backup == null) {
                return false;
            }
            AppDatabase.closeAndReset();
            appContext.deleteDatabase(AppDatabase.DB_NAME);
            clearSidecarFiles();
            AppDatabase.getInstance(appContext);
            return true;
        } catch (Exception ex) {
            Log.e("SettingsDataService", "Factory reset failed", ex);
            return false;
        }
    }

    private void runCheckpoint() {
        AppDatabase database = AppDatabase.getInstance(appContext);
        database.getOpenHelper().getWritableDatabase().execSQL("PRAGMA wal_checkpoint(FULL)");
    }

    private File getBackupDirectory() {
        File backupDir = new File(appContext.getFilesDir(), "backups");
        ensureDirectoryExists(backupDir);
        return backupDir;
    }

    private void ensureDirectoryExists(File dir) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private File getDatabaseFile() {
        return appContext.getDatabasePath(AppDatabase.DB_NAME);
    }

    private void clearSidecarFiles() {
        File database = getDatabaseFile();
        deleteSilently(new File(database.getAbsolutePath() + "-wal"));
        deleteSilently(new File(database.getAbsolutePath() + "-shm"));
    }

    private void deleteSilently(File file) {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void copyDatabaseFile(@NonNull File source, @NonNull File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) {
            ensureDirectoryExists(parent);
        }

        try (FileInputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(destination);
             FileChannel sourceChannel = inputStream.getChannel();
             FileChannel destinationChannel = outputStream.getChannel()) {
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }
}
