package com.autosecretary.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a byte-for-byte recovery point before Room opens an older production database. */
public final class LegacyDatabaseBackup {
    private static final String PREFERENCES = "migration_backup";
    private static final String BACKED_UP_VERSION = "backed_up_version";

    private LegacyDatabaseBackup() { }

    public static void ensure(Context context) {
        File database = context.getDatabasePath(FocusDatabase.NAME);
        if (!database.isFile()) return;
        int version = readVersion(database);
        if (version < 27 || version >= 34) return;
        if (version == 27 || version == 30) requireBuild4Identity(database, version);
        int already = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(BACKED_UP_VERSION, -1);
        if (already == version) return;

        String stamp = LocalDateTime.now().toString().replace(':', '-');
        File destination = new File(context.getFilesDir(),
                "migration-backups/v" + version + "-" + stamp);
        if (!destination.mkdirs() && !destination.isDirectory()) {
            throw new IllegalStateException("Migrationsbackup konnte nicht angelegt werden");
        }
        try {
            copyIfPresent(database, new File(destination, FocusDatabase.NAME));
            copyIfPresent(new File(database.getPath() + "-wal"),
                    new File(destination, FocusDatabase.NAME + "-wal"));
            copyIfPresent(new File(database.getPath() + "-shm"),
                    new File(destination, FocusDatabase.NAME + "-shm"));
            File backupDatabase = new File(destination, FocusDatabase.NAME);
            File backupWal = new File(destination, FocusDatabase.NAME + "-wal");
            File backupShm = new File(destination, FocusDatabase.NAME + "-shm");
            JSONObject metadata = new JSONObject()
                    .put("sourceVersion", version)
                    .put("createdAt", LocalDateTime.now().toString())
                    .put("databaseSha256", sha256(backupDatabase));
            if (backupWal.isFile()) metadata.put("walSha256", sha256(backupWal));
            if (backupShm.isFile()) metadata.put("shmSha256", sha256(backupShm));
            try (FileOutputStream output = new FileOutputStream(
                    new File(destination, "metadata.json"))) {
                output.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            createArchive(destination);
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putInt(BACKED_UP_VERSION, version).commit();
        } catch (Exception error) {
            throw new IllegalStateException("Migrationsbackup ist fehlgeschlagen", error);
        }
    }

    public static File latestArchive(Context context) {
        File root = new File(context.getFilesDir(), "migration-backups");
        File[] archives = root.listFiles(file -> file.isFile() && file.getName().endsWith(".zip"));
        if (archives == null || archives.length == 0) return null;
        return java.util.Arrays.stream(archives)
                .max(Comparator.comparingLong(File::lastModified)).orElse(null);
    }

    private static void createArchive(File source) throws Exception {
        File root = source.getParentFile();
        if (root == null) throw new IllegalStateException("Backuppfad ist unvollständig");
        File archive = new File(root, source.getName() + ".zip");
        File temporary = new File(root, source.getName() + ".zip.partial");
        Files.deleteIfExists(temporary.toPath());
        try {
            try (FileOutputStream fileOutput = new FileOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(fileOutput)) {
                for (String name : new String[]{
                        FocusDatabase.NAME, FocusDatabase.NAME + "-wal",
                        FocusDatabase.NAME + "-shm", "metadata.json"}) {
                    File value = new File(source, name);
                    if (!value.isFile()) continue;
                    zip.putNextEntry(new ZipEntry(name));
                    try (FileInputStream input = new FileInputStream(value)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            zip.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                }
                zip.finish();
                zip.flush();
                fileOutput.getFD().sync();
            }
            Files.move(temporary.toPath(), archive.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception error) {
            Files.deleteIfExists(temporary.toPath());
            throw error;
        }
    }

    private static int readVersion(File database) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
                database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor cursor = db.rawQuery("PRAGMA user_version", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            db.close();
        }
    }

    private static void requireBuild4Identity(File file, int version) {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (Cursor cursor = database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42", null)) {
            if (!cursor.moveToFirst()
                    || !LegacyArchiveImporter.matchesBuild4Schema(version, cursor.getString(0))) {
                throw new IllegalStateException(
                        "Datenbank v" + version + " hat nicht das exakte Build-4-Room-Schema");
            }
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "Datenbank v" + version + " hat nicht das exakte Build-4-Room-Schema", error);
        } finally {
            database.close();
        }
    }

    private static void copyIfPresent(File source, File target) throws Exception {
        if (!source.isFile()) return;
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        try (FileOutputStream output = new FileOutputStream(target, true)) {
            output.getFD().sync();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }
}
