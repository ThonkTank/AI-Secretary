package com.autosecretary.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class LegacyArchiveImporterTest {
    private Context context;
    private ExecutorService databaseExecutor;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clean();
        databaseExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        databaseExecutor.shutdownNow();
        clean();
    }

    @Test
    public void verifiedArchiveImportsOnlyIntoEmptyDatabaseAndComparesCounts() throws Exception {
        File unrelated = context.getDatabasePath("androidx.work.workdb");
        assertTrue(unrelated.getParentFile().mkdirs() || unrelated.getParentFile().isDirectory());
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(unrelated)) {
            output.write("unrelated-work-manager-state".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        byte[] archive = buildArchive(false, true);
        LegacyArchiveImporter.stage(context, new ByteArrayInputStream(archive));
        LegacyArchiveImporter.installPending(context);
        String installedHash = sha256(bytes(context.getDatabasePath(FocusDatabase.NAME)));
        LegacyArchiveImporter.installPending(context);
        assertEquals(installedHash, sha256(bytes(context.getDatabasePath(FocusDatabase.NAME))));
        FocusDatabaseFactory.prepare(context);

        FocusDatabase database = FocusDatabaseFactory.open(context, databaseExecutor);
        int[] counts = databaseExecutor.submit(() -> new int[]{
                database.focusDao().readWorkItems().size(),
                database.focusDao().readMigrationCandidates().size(),
                database.focusDao().readCompletions().size()}).get();
        assertEquals(1, counts[0]);
        assertEquals(1, counts[1]);
        assertEquals(1, counts[2]);
        assertEquals(2, databaseExecutor.submit(() ->
                database.focusDao().readLatestMigrationReport().importedCompletions).get().intValue());
        assertFalse(LegacyArchiveImporter.hasPendingArchive(context));
        try (FileInputStream input = new FileInputStream(unrelated)) {
            assertEquals("unrelated-work-manager-state", new String(input.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
        }
        assertFalse(new File(context.getDatabasePath(FocusDatabase.NAME).getPath()
                + ".bridge").exists());
        assertTrue(new File(context.getNoBackupFilesDir(), "legacy-import")
                .listFiles(file -> file.getName().startsWith("verified-")).length >= 2);
        database.close();
    }

    @Test
    public void verifiedPhysicalDeviceV30ArchiveImportsAndComparesCounts() throws Exception {
        byte[] archive = buildArchive(false, false, false, 30);
        LegacyArchiveImporter.stage(context, new ByteArrayInputStream(archive));
        LegacyArchiveImporter.installPending(context);
        FocusDatabaseFactory.prepare(context);

        FocusDatabase database = FocusDatabaseFactory.open(context, databaseExecutor);
        int[] result = databaseExecutor.submit(() -> new int[]{
                database.focusDao().readWorkItems().size(),
                database.focusDao().readMigrationCandidates().size(),
                database.focusDao().readCompletions().size(),
                database.focusDao().readLatestMigrationReport().sourceVersion}).get();
        assertEquals(1, result[0]);
        assertEquals(1, result[1]);
        assertEquals(1, result[2]);
        assertEquals(30, result[3]);
        assertFalse(LegacyArchiveImporter.hasPendingArchive(context));
        database.close();
    }

    @Test
    public void badDatabaseHashIsRejectedWithoutCreatingTarget() throws Exception {
        LegacyArchiveImporter.stage(context, new ByteArrayInputStream(buildArchive(true)));

        assertThrows(IllegalStateException.class, () -> LegacyArchiveImporter.installPending(context));
        assertFalse(context.getDatabasePath(FocusDatabase.NAME).exists());
    }

    @Test
    public void foreignV27RoomIdentityIsRejectedWithoutCreatingTarget() throws Exception {
        LegacyArchiveImporter.stage(context, new ByteArrayInputStream(
                buildArchive(false, false, true)));

        assertThrows(IllegalStateException.class, () -> LegacyArchiveImporter.installPending(context));
        assertFalse(context.getDatabasePath(FocusDatabase.NAME).exists());
    }

    @Test
    public void existingDatabaseCannotBeReplaced() throws Exception {
        assertTrue(context.getDatabasePath(FocusDatabase.NAME).getParentFile().mkdirs()
                || context.getDatabasePath(FocusDatabase.NAME).getParentFile().isDirectory());
        assertTrue(context.getDatabasePath(FocusDatabase.NAME).createNewFile());

        assertThrows(IllegalStateException.class, () -> LegacyArchiveImporter.stage(
                context, new ByteArrayInputStream(buildArchive(false))));
    }

    @Test
    public void explicitEmptyDatabaseDecisionNeverImportsPreviouslyStagedArchive() throws Exception {
        LegacyArchiveImporter.stage(context, new ByteArrayInputStream(buildArchive(false)));
        LegacyArchiveImporter.chooseEmptyDatabase(context);

        FocusDatabase database = FocusDatabaseFactory.open(context, databaseExecutor);
        int[] counts = databaseExecutor.submit(() -> new int[]{
                database.focusDao().readWorkItems().size(),
                database.focusDao().readMigrationCandidates().size()}).get();

        assertEquals(0, counts[0]);
        assertEquals(0, counts[1]);
        database.close();
    }

    private byte[] buildArchive(boolean wrongHash) throws Exception {
        return buildArchive(wrongHash, false);
    }

    private byte[] buildArchive(boolean wrongHash, boolean retainWal) throws Exception {
        return buildArchive(wrongHash, retainWal, false);
    }

    private byte[] buildArchive(
            boolean wrongHash, boolean retainWal, boolean wrongIdentity) throws Exception {
        return buildArchive(wrongHash, retainWal, wrongIdentity, 27);
    }

    private byte[] buildArchive(
            boolean wrongHash,
            boolean retainWal,
            boolean wrongIdentity,
            int sourceVersion) throws Exception {
        File source = new File(context.getCacheDir(),
                "build4-fixture-v" + sourceVersion + ".db");
        source.delete();
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(source, null);
        if (retainWal) {
            legacy.enableWriteAheadLogging();
            try (android.database.Cursor ignored = legacy.rawQuery(
                    "PRAGMA wal_autocheckpoint=0", null)) {
                assertTrue(ignored.moveToFirst());
            }
        }
        legacy.execSQL("""
                CREATE TABLE task_core (
                    id TEXT PRIMARY KEY NOT NULL, title TEXT, schedulingType TEXT,
                    minDuration INTEGER NOT NULL, deadline TEXT, adaptive INTEGER NOT NULL,
                    created TEXT, completed INTEGER NOT NULL,
                    repetition_reps INTEGER NOT NULL, repetition_perPeriod INTEGER NOT NULL,
                    repetition_periodUnit TEXT, repetition_periodStart TEXT,
                    history_currentStreak INTEGER NOT NULL, history_completions INTEGER NOT NULL)
                """);
        legacy.execSQL("CREATE TABLE task_pref_slots (id TEXT PRIMARY KEY, taskId TEXT, start TEXT)");
        legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)");
        String expectedIdentity = sourceVersion == 30
                ? LegacyArchiveImporter.BUILD4_V30_ROOM_IDENTITY
                : LegacyArchiveImporter.BUILD4_V27_ROOM_IDENTITY;
        legacy.execSQL("INSERT INTO room_master_table VALUES (42, ?)", new Object[]{
                wrongIdentity ? "foreign-legacy-schema" : expectedIdentity});
        legacy.execSQL("""
                CREATE TABLE task_slots (
                    id TEXT PRIMARY KEY, taskId TEXT, day TEXT,
                    completed INTEGER NOT NULL, realEnd TEXT)
                """);
        String task = UUID.nameUUIDFromBytes("archive-task".getBytes()).toString();
        String complex = UUID.nameUUIDFromBytes("archive-complex".getBytes()).toString();
        legacy.setVersion(sourceVersion);
        if (retainWal) {
            try (android.database.Cursor ignored = legacy.rawQuery(
                    "PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                assertTrue(ignored.moveToFirst());
            }
        }
        legacy.execSQL("INSERT INTO task_core VALUES (?, 'Übernehmen', 'TASK', 30, NULL, 1, "
                        + "'2026-08-01', 0, 0, 1, 'DAY', NULL, 0, 1)", new Object[]{task});
        legacy.execSQL("INSERT INTO task_core VALUES (?, 'Entscheiden', 'TASK', 45, NULL, 0, "
                        + "'2026-08-01', 0, 5, 2, 'WEEK', NULL, 0, 0)", new Object[]{complex});
        legacy.execSQL("INSERT INTO task_slots VALUES ('slot', ?, '2026-08-03', 1, '08:30')",
                new Object[]{task});
        legacy.execSQL("INSERT INTO task_slots VALUES ('slot-complex', ?, '2026-08-04', 1, '09:30')",
                new Object[]{complex});
        byte[] database;
        try (FileInputStream input = new FileInputStream(source)) { database = input.readAllBytes(); }
        File walFile = new File(source.getPath() + "-wal");
        File shmFile = new File(source.getPath() + "-shm");
        byte[] wal = retainWal && walFile.isFile() ? bytes(walFile) : null;
        byte[] shm = retainWal && shmFile.isFile() ? bytes(shmFile) : null;
        legacy.close();
        Properties metadata = new Properties();
        metadata.setProperty("sourcePackage", "com.autosecretary");
        metadata.setProperty("sourceCertificateSha256", LegacyArchiveImporter.BUILD4_CERT_SHA256);
        metadata.setProperty("sourceDatabaseVersion", Integer.toString(sourceVersion));
        metadata.setProperty("sourceRoomIdentityHash", expectedIdentity);
        metadata.setProperty("databaseSha256", wrongHash ? "00" : sha256(database));
        if (wal != null) metadata.setProperty("walSha256", sha256(wal));
        if (shm != null) metadata.setProperty("shmSha256", sha256(shm));
        ByteArrayOutputStream encodedMetadata = new ByteArrayOutputStream();
        metadata.store(encodedMetadata, "fixture");

        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(archive)) {
            zip.putNextEntry(new ZipEntry(FocusDatabase.NAME));
            zip.write(database);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("metadata.properties"));
            zip.write(encodedMetadata.toByteArray());
            zip.closeEntry();
            if (wal != null) {
                zip.putNextEntry(new ZipEntry(FocusDatabase.NAME + "-wal"));
                zip.write(wal);
                zip.closeEntry();
            }
            if (shm != null) {
                zip.putNextEntry(new ZipEntry(FocusDatabase.NAME + "-shm"));
                zip.write(shm);
                zip.closeEntry();
            }
        }
        source.delete();
        walFile.delete();
        shmFile.delete();
        return archive.toByteArray();
    }

    private static String sha256(byte[] value) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte part : MessageDigest.getInstance("SHA-256").digest(value)) {
            result.append(String.format(Locale.ROOT, "%02x", part));
        }
        return result.toString();
    }

    private static byte[] bytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.readAllBytes();
        }
    }

    private void clean() {
        context.deleteDatabase(FocusDatabase.NAME);
        context.deleteDatabase("androidx.work.workdb");
        new File(context.getDatabasePath(FocusDatabase.NAME).getPath() + ".bridge").delete();
        context.getSharedPreferences("migration_backup", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("legacy_archive_import", Context.MODE_PRIVATE).edit().clear().commit();
        deleteTree(new File(context.getNoBackupFilesDir(), "legacy-import"));
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
