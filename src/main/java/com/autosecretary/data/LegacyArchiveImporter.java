package com.autosecretary.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Verified one-time bridge from the Build-4 sandbox into an otherwise empty installation. */
public final class LegacyArchiveImporter {
    public static final String BUILD4_CERT_SHA256 =
            "1e0e90509d79efacebaec1af024f2577d7799cf5534e841db7417184287dbfb2";
    public static final String BUILD4_V27_ROOM_IDENTITY =
            "87fa112d19ca59d751c7933a42b85cd9";
    public static final String BUILD4_V30_ROOM_IDENTITY =
            "51ffa9b42fba4bd0b74c6eb9d8809c00";
    /** Kept for source compatibility with v27 fixture tests. */
    public static final String BUILD4_ROOM_IDENTITY =
            BUILD4_V27_ROOM_IDENTITY;
    private static final String PREFERENCES = "legacy_archive_import";
    private static final String DECIDED = "decided";
    private static final long ARCHIVE_LIMIT = 1024L * 1024L * 1024L;
    private static final Set<String> ENTRIES = Set.of(
            FocusDatabase.NAME, FocusDatabase.NAME + "-wal", FocusDatabase.NAME + "-shm",
            "metadata.properties");

    private LegacyArchiveImporter() { }

    public static boolean requiresUserDecision(Context context) {
        return !context.getDatabasePath(FocusDatabase.NAME).exists()
                && !context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(DECIDED, false);
    }

    public static boolean hasPendingArchive(Context context) {
        return pending(context).isFile();
    }

    public static void chooseEmptyDatabase(Context context) {
        recoverInterrupted(context);
        if (context.getDatabasePath(FocusDatabase.NAME).exists()) {
            throw new IllegalStateException("Die Datenbank wurde bereits angelegt");
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putBoolean(DECIDED, true).commit();
    }

    public static void stage(Context context, Uri source) {
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IllegalStateException("Exportarchiv konnte nicht geöffnet werden");
            stage(context, input);
        } catch (Exception error) {
            throw new IllegalStateException("Exportarchiv konnte nicht übernommen werden", error);
        }
    }

    static void stage(Context context, InputStream source) throws Exception {
        recoverInterrupted(context);
        requireEmptyTarget(context);
        File root = root(context);
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Importverzeichnis konnte nicht angelegt werden");
        }
        File temporary = new File(root, "pending.zip.partial");
        File target = pending(context);
        temporary.delete();
        long total = 0;
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = source.read(buffer)) >= 0) {
                total += read;
                if (total > ARCHIVE_LIMIT) throw new IllegalStateException("Exportarchiv ist zu groß");
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
        if (total == 0) throw new IllegalStateException("Exportarchiv ist leer");
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    public static void installPending(Context context) {
        if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(DECIDED, false)) return;
        File archive = pending(context);
        if (!archive.isFile()) return;
        if (completedInstallPresent(context)) {
            // The atomic rename and its count receipt completed. Room may now migrate, or retry
            // its own transaction after a process death, without reinstalling the same archive.
            return;
        }
        recoverInterrupted(context);
        requireEmptyTarget(context);
        File extracted = new File(root(context), "extracted");
        deleteTree(extracted);
        if (!extracted.mkdirs()) throw new IllegalStateException("Import konnte nicht vorbereitet werden");
        try {
            extract(archive, extracted);
            Properties metadata = metadata(extracted);
            validateMetadata(metadata, extracted);
            checkpointArchiveDatabase(extracted);
            ExpectedCounts expected = inspectSource(new File(extracted, FocusDatabase.NAME));
            int metadataVersion = Integer.parseInt(
                    metadata.getProperty("sourceDatabaseVersion"));
            if (metadataVersion != expected.sourceVersion()) {
                throw new SecurityException("Exportmetadaten und Datenbankversion widersprechen sich");
            }
            installFiles(context, extracted);
            Properties receipt = new Properties();
            receipt.setProperty("archiveSha256", sha256(archive));
            receipt.setProperty("installedDatabaseSha256",
                    sha256(context.getDatabasePath(FocusDatabase.NAME)));
            receipt.setProperty("expectedItems", Integer.toString(expected.items()));
            receipt.setProperty("expectedCompletions", Integer.toString(expected.completions()));
            receipt.setProperty("sourceCertificateSha256", BUILD4_CERT_SHA256);
            receipt.setProperty("sourceDatabaseVersion", Integer.toString(expected.sourceVersion()));
            store(receipt, receipt(context));
        } catch (Exception error) {
            recoverInterrupted(context);
            throw new IllegalStateException("Build-4-Archiv wurde nicht importiert", error);
        } finally {
            deleteTree(extracted);
        }
    }

    public static void verifyMigrated(Context context, SupportSQLiteDatabase database) {
        File receipt = receipt(context);
        if (!receipt.isFile()) return;
        try {
            Properties expected = new Properties();
            try (FileInputStream input = new FileInputStream(receipt)) { expected.load(input); }
            int sourceVersion;
            int importedItems;
            int candidateItems;
            int importedCompletions;
            try (Cursor cursor = database.query("""
                    SELECT sourceVersion, importedItems, candidateItems, importedCompletions
                    FROM migration_reports ORDER BY id DESC LIMIT 1
                    """)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalStateException("Migrationsbericht für Build 4 fehlt");
                }
                sourceVersion = cursor.getInt(0);
                importedItems = cursor.getInt(1);
                candidateItems = cursor.getInt(2);
                importedCompletions = cursor.getInt(3);
            }
            int expectedSourceVersion = Integer.parseInt(
                    expected.getProperty("sourceDatabaseVersion"));
            if (sourceVersion != expectedSourceVersion) {
                throw new IllegalStateException("Falsche Quellversion im Bericht");
            }
            int expectedItems = Integer.parseInt(expected.getProperty("expectedItems"));
            int expectedCompletions = Integer.parseInt(expected.getProperty("expectedCompletions"));
            if (importedItems + candidateItems != expectedItems
                    || importedCompletions != expectedCompletions) {
                throw new IllegalStateException("Mengenvergleich nach Migration fehlgeschlagen");
            }
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putBoolean(DECIDED, true).commit();
            marker(context).delete();
            String stamp = LocalDateTime.now().toString().replace(':', '-');
            File verified = new File(root(context), "verified-" + stamp + ".properties");
            Files.move(receipt.toPath(), verified.toPath(), StandardCopyOption.ATOMIC_MOVE);
            Files.move(pending(context).toPath(),
                    new File(root(context), "verified-" + stamp + ".zip").toPath(),
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception error) {
            throw new IllegalStateException("Build-4-Import konnte nicht bestätigt werden", error);
        }
    }

    static void recoverInterrupted(Context context) {
        File marker = marker(context);
        if (!marker.isFile()) return;
        stagingDatabaseFile(context).delete();
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            new File(context.getDatabasePath(FocusDatabase.NAME).getPath() + suffix).delete();
        }
        receipt(context).delete();
        marker.delete();
    }

    private static boolean completedInstallPresent(Context context) {
        File database = context.getDatabasePath(FocusDatabase.NAME);
        File receipt = receipt(context);
        if (!marker(context).isFile() || !receipt.isFile() || !database.isFile()) return false;
        try {
            Properties state = new Properties();
            try (FileInputStream input = new FileInputStream(receipt)) { state.load(input); }
            String expected = state.getProperty("installedDatabaseSha256", "");
            return !expected.isBlank() && expected.equals(sha256(database));
        } catch (Exception invalidCommit) {
            return false;
        }
    }

    private static void installFiles(Context context, File extracted) throws Exception {
        File database = context.getDatabasePath(FocusDatabase.NAME);
        File parent = database.getParentFile();
        if (parent == null) {
            throw new IllegalStateException("Datenbankverzeichnis ist ungültig");
        }
        if (!parent.isDirectory() && !parent.mkdirs()) {
            if (parent.exists()) {
                throw new IllegalStateException("Datenbankpfad ist kein Verzeichnis");
            }
            throw new IllegalStateException("Datenbankverzeichnis konnte nicht angelegt werden");
        } else if (!parent.isDirectory()) {
            throw new IllegalStateException("Datenbankpfad ist kein Verzeichnis");
        }
        File staging = stagingDatabaseFile(context);
        staging.delete();
        Properties state = new Properties();
        state.setProperty("startedAt", LocalDateTime.now().toString());
        store(state, marker(context));
        File source = new File(extracted, FocusDatabase.NAME);
        Files.copy(source.toPath(), staging.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try (FileOutputStream output = new FileOutputStream(staging, true)) {
            output.getFD().sync();
        }
        Files.move(staging.toPath(), database.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }

    /** Folds a captured WAL into the copied main database before the one-file atomic install. */
    private static void checkpointArchiveDatabase(File extracted) {
        File file = new File(extracted, FocusDatabase.NAME);
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try (Cursor cursor = database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
            if (!cursor.moveToFirst() || cursor.getInt(0) != 0) {
                throw new IllegalStateException("Build-4-WAL konnte nicht konsolidiert werden");
            }
        } finally {
            database.close();
        }
        for (String suffix : new String[]{"-wal", "-shm"}) {
            File sidecar = new File(extracted, FocusDatabase.NAME + suffix);
            if (sidecar.exists() && !sidecar.delete()) {
                throw new IllegalStateException("Konsolidierte SQLite-Sidecar konnte nicht entfernt werden");
            }
        }
    }

    private static ExpectedCounts inspectSource(File file) {
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            int sourceVersion = integer(database, "PRAGMA user_version");
            try (Cursor identity = database.rawQuery(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42", null)) {
                if (!identity.moveToFirst()
                        || !matchesBuild4Schema(sourceVersion, identity.getString(0))) {
                    throw new IllegalStateException("Export hat nicht das Build-4-Room-Schema");
                }
            }
            int items = integer(database, "SELECT COUNT(*) FROM task_core");
            int completions = 0;
            try (Cursor cursor = database.rawQuery("""
                    SELECT s.day, s.realEnd FROM task_slots s
                    JOIN task_core t ON t.id = s.taskId
                    WHERE s.completed = 1 AND s.realEnd IS NOT NULL
                    """, null)) {
                while (cursor.moveToNext()) {
                    try {
                        java.time.LocalDate.parse(cursor.getString(0))
                                .atTime(java.time.LocalTime.parse(cursor.getString(1)));
                        completions++;
                    } catch (RuntimeException corruptRecord) {
                        // The migration reports and skips this individual corrupt evidence row.
                    }
                }
            }
            return new ExpectedCounts(sourceVersion, items, completions);
        } finally {
            database.close();
        }
    }

    private static int integer(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("Mengenabfrage ist leer");
            return cursor.getInt(0);
        }
    }

    private static void extract(File archive, File destination) throws Exception {
        Map<String, Boolean> found = new HashMap<>();
        long total = 0;
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || !ENTRIES.contains(name) || found.put(name, true) != null) {
                    throw new SecurityException("Unerlaubter oder doppelter Archiveintrag: " + name);
                }
                File target = new File(destination, name);
                try (FileOutputStream output = new FileOutputStream(target)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        total += read;
                        if (total > ARCHIVE_LIMIT) throw new SecurityException("Entpackte Daten sind zu groß");
                        output.write(buffer, 0, read);
                    }
                    output.getFD().sync();
                }
                input.closeEntry();
            }
        }
        if (!found.containsKey(FocusDatabase.NAME) || !found.containsKey("metadata.properties")) {
            throw new SecurityException("Datenbank oder Metadaten fehlen im Archiv");
        }
    }

    private static Properties metadata(File extracted) throws Exception {
        Properties result = new Properties();
        try (FileInputStream input = new FileInputStream(
                new File(extracted, "metadata.properties"))) {
            result.load(input);
        }
        return result;
    }

    private static void validateMetadata(Properties metadata, File extracted) throws Exception {
        if (!"com.autosecretary".equals(metadata.getProperty("sourcePackage"))) {
            throw new SecurityException("Export stammt nicht von com.autosecretary");
        }
        String certificate = metadata.getProperty("sourceCertificateSha256", "")
                .replace(":", "").toLowerCase(Locale.ROOT);
        if (!BUILD4_CERT_SHA256.equals(certificate)) {
            throw new SecurityException("Export stammt nicht vom Build-4-Zertifikat");
        }
        int sourceVersion;
        try {
            sourceVersion = Integer.parseInt(metadata.getProperty("sourceDatabaseVersion", ""));
        } catch (NumberFormatException error) {
            throw new SecurityException("Exportmetadaten nennen keine unterstützte Datenbankversion", error);
        }
        if (!matchesBuild4Schema(sourceVersion,
                metadata.getProperty("sourceRoomIdentityHash"))) {
            throw new SecurityException("Exportmetadaten nennen nicht das Build-4-Room-Schema");
        }
        verifyFile(metadata, extracted, FocusDatabase.NAME, "databaseSha256");
        verifyOptionalFile(metadata, extracted, FocusDatabase.NAME + "-wal", "walSha256");
        verifyOptionalFile(metadata, extracted, FocusDatabase.NAME + "-shm", "shmSha256");
    }

    private static void verifyFile(
            Properties metadata, File extracted, String name, String property) throws Exception {
        File file = new File(extracted, name);
        String expected = metadata.getProperty(property, "").toLowerCase(Locale.ROOT);
        if (!file.isFile() || expected.isEmpty() || !expected.equals(sha256(file))) {
            throw new SecurityException("Prüfsumme stimmt nicht: " + name);
        }
    }

    private static void verifyOptionalFile(
            Properties metadata, File extracted, String name, String property) throws Exception {
        File file = new File(extracted, name);
        String expected = metadata.getProperty(property, "");
        if (file.isFile() != !expected.isEmpty()) {
            throw new SecurityException("Archivmetadaten sind unvollständig: " + name);
        }
        if (file.isFile() && !expected.toLowerCase(Locale.ROOT).equals(sha256(file))) {
            throw new SecurityException("Prüfsumme stimmt nicht: " + name);
        }
    }

    private static void requireEmptyTarget(Context context) {
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            if (new File(context.getDatabasePath(FocusDatabase.NAME).getPath() + suffix).exists()) {
                throw new IllegalStateException("Import ist nur vor Anlage einer Datenbank erlaubt");
            }
        }
    }

    private static void store(Properties properties, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Importstatus konnte nicht angelegt werden");
        }
        File temporary = new File(target.getPath() + ".partial");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            properties.store(output, "Auto Secretary verified legacy import");
            output.getFD().sync();
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value));
        }
        return result.toString();
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static File root(Context context) {
        return new File(context.getNoBackupFilesDir(), "legacy-import");
    }

    private static File pending(Context context) { return new File(root(context), "pending.zip"); }
    private static File receipt(Context context) { return new File(root(context), "receipt.properties"); }
    private static File marker(Context context) { return new File(root(context), "installing.properties"); }

    private static File stagingDatabaseFile(Context context) {
        return new File(context.getDatabasePath(FocusDatabase.NAME).getPath() + ".bridge");
    }

    static boolean matchesBuild4Schema(int version, String identity) {
        if (identity == null) return false;
        return switch (version) {
            case 27 -> BUILD4_V27_ROOM_IDENTITY.equals(identity);
            case 30 -> BUILD4_V30_ROOM_IDENTITY.equals(identity);
            default -> false;
        };
    }

    private record ExpectedCounts(int sourceVersion, int items, int completions) { }
}
