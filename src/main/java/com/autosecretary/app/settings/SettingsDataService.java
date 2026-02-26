package com.autosecretary.app.settings;

import android.content.Context;

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
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(new Date());
        File target = new File(getBackupDirectory(), BACKUP_PREFIX + timestamp + ".db");

        try {
            runCheckpoint();
            copyDatabaseFile(getDatabaseFile(), target);
            return target;
        } catch (IOException ex) {
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
            return false;
        }
    }

    public boolean factoryReset() {
        try {
            createManualBackup();
            AppDatabase.closeAndReset();
            appContext.deleteDatabase(AppDatabase.DB_NAME);
            clearSidecarFiles();
            AppDatabase.getInstance(appContext);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void runCheckpoint() {
        AppDatabase database = AppDatabase.getInstance(appContext);
        database.getOpenHelper().getWritableDatabase().execSQL("PRAGMA wal_checkpoint(FULL)");
    }

    private File getBackupDirectory() {
        File backupDir = new File(appContext.getFilesDir(), "backups");
        if (!backupDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backupDir.mkdirs();
        }
        return backupDir;
    }

    private File getDatabaseFile() {
        return appContext.getDatabasePath(AppDatabase.DB_NAME);
    }

    private void clearSidecarFiles() {
        File database = getDatabaseFile();
        File walFile = new File(database.getAbsolutePath() + "-wal");
        File shmFile = new File(database.getAbsolutePath() + "-shm");
        //noinspection ResultOfMethodCallIgnored
        walFile.delete();
        //noinspection ResultOfMethodCallIgnored
        shmFile.delete();
    }

    private void copyDatabaseFile(@NonNull File source, @NonNull File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        try (FileInputStream inputStream = new FileInputStream(source);
             FileOutputStream outputStream = new FileOutputStream(destination);
             FileChannel sourceChannel = inputStream.getChannel();
             FileChannel destinationChannel = outputStream.getChannel()) {
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }
}
