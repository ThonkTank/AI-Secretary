package com.autosecretary.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.autosecretary.application.MigrationCandidateResolution;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class FocusDatabaseMigrationTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        deleteDatabase();
    }

    @After
    public void tearDown() {
        deleteDatabase();
    }

    @Test
    public void productionV27MigratesSimpleCoreAndQuarantinesComplexSemantics() {
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(FocusDatabase.NAME), null);
        legacy.execSQL("""
                CREATE TABLE task_core (
                    id TEXT PRIMARY KEY NOT NULL, title TEXT, description TEXT,
                    schedulingType TEXT, minDuration INTEGER NOT NULL,
                    deadline TEXT, adaptive INTEGER NOT NULL, created TEXT,
                    completed INTEGER NOT NULL,
                    repetition_reps INTEGER NOT NULL,
                    repetition_perPeriod INTEGER NOT NULL,
                    repetition_periodUnit TEXT,
                    repetition_periodStart TEXT,
                    history_currentStreak INTEGER NOT NULL,
                    history_completions INTEGER NOT NULL,
                    priority TEXT, startDate TEXT, fixedDate TEXT,
                    progress_target INTEGER, budgetRequiredCents INTEGER
                )
                """);
        legacy.execSQL("ALTER TABLE task_core ADD COLUMN "
                + "repetition_periodCompletions INTEGER NOT NULL DEFAULT 0");
        legacy.execSQL("ALTER TABLE task_core ADD COLUMN fixedStart TEXT");
        legacy.execSQL("ALTER TABLE task_core ADD COLUMN fixedEnd TEXT");
        legacy.execSQL("ALTER TABLE task_core ADD COLUMN fixedDuration INTEGER");
        legacy.execSQL("""
                CREATE TABLE task_pref_slots (
                    id TEXT PRIMARY KEY NOT NULL, taskId TEXT, days TEXT, start TEXT)
                """);
        legacy.execSQL("""
                CREATE TABLE task_slots (
                    id TEXT PRIMARY KEY NOT NULL, taskId TEXT, day TEXT,
                    completed INTEGER NOT NULL, realEnd TEXT)
                """);
        legacy.execSQL("""
                INSERT INTO task_core VALUES
                ('task', 'Steuer', 'Altbeschreibung', 'TASK', 25, '2026-08-20', 0,
                 '2026-08-01', 0, 0, 1, 'DAY', NULL, 0, 0, 'HIGH', NULL, NULL, NULL, NULL,
                 0, NULL, NULL, NULL),
                ('routine', 'Wochenrückblick', NULL, 'TASK', 40, NULL, 1,
                 '2026-08-01', 0, 1, 2, 'WEEK', '2026-08-03', 3, 7,
                 'MEDIUM', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL),
                ('complex', 'Fünfmal lernen', NULL, 'TASK', 30, NULL, 0,
                 '2026-08-01', 0, 5, 2, 'WEEK', '2026-08-03', 0, 0,
                 'MEDIUM', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL),
                ('appointment', 'Arzt', NULL, 'TERMIN', 30, NULL, 0,
                 '2026-08-01', 0, 0, 1, 'DAY', NULL, 0, 0,
                 'MEDIUM', NULL, '2026-08-12', NULL, NULL, 0, '09:00', '10:00', 60),
                ('invalid', '', NULL, 'TASK', 2, 'kein-datum', 0,
                 'keine-zeit', 0, 1, 0, NULL, NULL, 0, 0,
                 'MEDIUM', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL),
                ('missing-start', 'Start fehlt', NULL, 'TASK', 30, NULL, 0,
                 '2026-08-01', 0, 1, 1, 'WEEK', NULL, 0, 0,
                 'MEDIUM', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL)
                """);
        legacy.execSQL("INSERT INTO task_pref_slots VALUES ('pref', 'routine', NULL, '18:00')");
        legacy.execSQL("INSERT INTO task_pref_slots VALUES ('pref-complex', 'complex', NULL, '09:00')");
        legacy.execSQL("INSERT INTO task_pref_slots VALUES ('pref-night', 'task', NULL, '02:00')");
        legacy.execSQL("INSERT INTO task_slots VALUES ('done', 'routine', '2026-08-04', 1, '19:15')");
        legacy.execSQL("INSERT INTO task_slots VALUES ('done-complex', 'complex', '2026-08-05', 1, '09:15')");
        legacy.execSQL("INSERT INTO task_slots VALUES ('corrupt', 'routine', 'kein-tag', 1, '99:99')");
        legacy.execSQL("UPDATE task_core SET repetition_periodCompletions = 1 "
                + "WHERE id = 'routine'");
        legacy.execSQL("UPDATE task_core SET adaptive = 1, history_currentStreak = 2, "
                + "history_completions = 4 WHERE id = 'complex'");
        legacy.execSQL("ALTER TABLE task_core ADD COLUMN "
                + "repetition_completeFirst INTEGER NOT NULL DEFAULT 0");
        legacy.execSQL("ALTER TABLE task_core ADD COLUMN "
                + "repetition_carryoverDebt INTEGER NOT NULL DEFAULT 0");
        legacy.execSQL("""
                INSERT INTO task_core (
                    id, title, schedulingType, minDuration, adaptive, created, completed,
                    repetition_reps, repetition_perPeriod, repetition_periodUnit,
                    repetition_periodStart, history_currentStreak, history_completions,
                    priority, repetition_periodCompletions, repetition_completeFirst,
                    repetition_carryoverDebt)
                VALUES
                    ('monthly', 'Monatsabschluss', 'TASK', 30, 0, '2026-08-01', 0,
                     1, 1, 'MONTH', '2026-08-01', 0, 0, 'MEDIUM', 0, 0, 0),
                    ('carryover', 'Schuldenregel', 'TASK', 30, 0, '2026-08-01', 0,
                     1, 1, 'WEEK', '2026-08-01', 0, 0, 'MEDIUM', 0, 1, 1)
                """);
        legacy.execSQL("CREATE TABLE task_transition_stats (id TEXT PRIMARY KEY NOT NULL)");
        legacy.execSQL("INSERT INTO task_transition_stats VALUES ('transition')");
        legacy.execSQL("CREATE TABLE budget_transaction (id TEXT PRIMARY KEY NOT NULL)");
        legacy.execSQL("INSERT INTO budget_transaction VALUES ('budget')");
        legacy.execSQL("CREATE TABLE meal_plan (id TEXT PRIMARY KEY NOT NULL)");
        legacy.execSQL("INSERT INTO meal_plan VALUES ('meal')");
        legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)");
        legacy.execSQL("INSERT INTO room_master_table VALUES (42, ?)", new Object[]{
                LegacyArchiveImporter.BUILD4_ROOM_IDENTITY});
        legacy.setVersion(27);
        legacy.close();

        LegacyDatabaseBackup.ensure(context);
        assertTrue(hasMigrationBackup(27));

        FocusDatabase migrated = openMigrated();
        assertEquals(2, migrated.focusDao().readWorkItems().size());
        var task = migrated.focusDao().readWorkItem(id("work-item", "task"));
        assertEquals("TASK", task.kind);
        assertEquals("2026-08-20T23:59:00", task.deadlineAt);
        assertNull(task.timePreference);
        var routine = migrated.focusDao().readWorkItem(id("work-item", "routine"));
        assertEquals("ROUTINE", routine.kind);
        assertEquals(14, routine.cadenceDays);
        assertEquals("2026-08-17", routine.nextDueDate);
        assertEquals("EVENING", routine.timePreference);
        assertTrue(routine.flexible);
        assertEquals(1, migrated.focusDao().readCompletions().size());
        assertEquals(6, migrated.focusDao().readMigrationCandidates().size());
        assertEquals(2, migrated.focusDao().readLatestMigrationReport().importedItems);
        assertEquals(6, migrated.focusDao().readLatestMigrationReport().candidateItems);
        assertEquals(2, migrated.focusDao().readLatestMigrationReport().importedCompletions);
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_DESCRIPTIONS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_AMBIGUOUS_TIME_PREFERENCES"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("QUARANTINED_CORRUPT_CORE_ITEMS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_PRIORITIES"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_APPOINTMENTS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_PLANNED_SLOTS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_TRANSITION_STATS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_BUDGET_RECORDS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_MEAL_RECORDS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("CORRUPT_COMPLETIONS_SKIPPED"));
        assertFalse(tableExists(migrated, "task_core"));
        assertFalse(tableExists(migrated, "task_slots"));
        RoomWorkItemRepository repository = new RoomWorkItemRepository(migrated);
        assertEquals(2, repository.loadSnapshot().workItems().size());
        var review = repository.migrationReview();
        assertNotNull(review);
        assertTrue(review.candidates().stream()
                .filter(candidate -> candidate.id().equals(id("work-item", "complex")))
                .findFirst().orElseThrow().legacySummary().contains("5× pro 2 Woche(n)"));
        assertEquals(60, review.candidates().stream()
                .filter(candidate -> candidate.id().equals(id("work-item", "appointment")))
                .findFirst().orElseThrow().durationMinutes());
        assertEquals(java.time.LocalDateTime.parse("2026-08-12T10:00:00"),
                review.candidates().stream()
                        .filter(candidate -> candidate.id().equals(id("work-item", "appointment")))
                        .findFirst().orElseThrow().deadlineAt());
        assertTrue(review.candidates().stream()
                .filter(candidate -> candidate.id().equals(id("work-item", "monthly")))
                .findFirst().orElseThrow().legacySummary().contains("Kalendermonat"));
        assertEquals("CORRUPT_LEGACY_CORE_UNSUPPORTED", review.candidates().stream()
                .filter(candidate -> candidate.id().equals(id("work-item", "invalid")))
                .findFirst().orElseThrow().reason());

        String complexId = id("work-item", "complex");
        String appointmentId = id("work-item", "appointment");
        String invalidId = id("work-item", "invalid");
        String monthlyId = id("work-item", "monthly");
        String carryoverId = id("work-item", "carryover");
        String missingStartId = id("work-item", "missing-start");
        assertThrows(IllegalStateException.class, () -> repository.resolveMigrationCandidates(
                List.of(MigrationCandidateResolution.task(complexId),
                        MigrationCandidateResolution.task(complexId)),
                review.id(), java.time.LocalDateTime.parse("2026-08-12T10:00:00")));
        assertNull(migrated.focusDao().readWorkItem(complexId));
        assertEquals(6, migrated.focusDao().readMigrationCandidates().size());
        assertFalse(migrated.focusDao().readLatestMigrationReport().acknowledged);

        repository.resolveMigrationCandidates(
                List.of(MigrationCandidateResolution.task(complexId),
                        MigrationCandidateResolution.discard(appointmentId),
                        MigrationCandidateResolution.discard(invalidId),
                        MigrationCandidateResolution.routine(monthlyId, 30),
                        MigrationCandidateResolution.discard(carryoverId),
                        MigrationCandidateResolution.discard(missingStartId)),
                review.id(), java.time.LocalDateTime.parse("2026-08-12T10:00:00"));
        var resolvedComplex = repository.find(complexId);
        assertNotNull(resolvedComplex);
        assertEquals(java.time.LocalDateTime.parse("2026-08-01T00:00:00"),
                resolvedComplex.createdAt());
        assertEquals(com.autosecretary.domain.TimePreference.MORNING,
                resolvedComplex.timePreference());
        assertTrue(resolvedComplex.flexible());
        assertEquals(2, resolvedComplex.stats().currentStreak());
        assertEquals(4, resolvedComplex.stats().totalCompletions());
        assertTrue(repository.loadSnapshot().completions().stream()
                .anyMatch(value -> value.workItemId().equals(complexId)
                        && value.completedAt().equals(java.time.LocalDateTime.parse(
                        "2026-08-05T09:15:00"))));
        var resolvedMonthly = (com.autosecretary.domain.Routine) repository.find(monthlyId);
        assertEquals(30, resolvedMonthly.cadenceDays());
        assertEquals(java.time.LocalDate.parse("2026-08-01"), resolvedMonthly.nextDueDate());
        assertTrue(migrated.focusDao().readMigrationCandidates().isEmpty());
        assertTrue(migrated.focusDao().readLatestMigrationReport().acknowledged);
        assertNull(repository.migrationReview());
        migrated.close();
    }

    @Test
    public void fixedBuild4GeneratedV27FixtureMigratesThroughRealRoomOpen() throws Exception {
        File target = context.getDatabasePath(FocusDatabase.NAME);
        target.getParentFile().mkdirs();
        try (var input = getClass().getResourceAsStream("/fixtures/build4-v27.db")) {
            assertNotNull(input);
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        assertBuild4FixtureIdentity(target);
        LegacyDatabaseBackup.ensure(context);
        assertTrue(hasMigrationBackup(27));

        FocusDatabase migrated = openMigrated();
        var snapshot = new RoomWorkItemRepository(migrated).loadSnapshot();
        assertEquals(2, snapshot.workItems().size());
        assertEquals(1, snapshot.completions().size());
        assertEquals(3, migrated.focusDao().readMigrationCandidates().size());
        assertEquals(2, migrated.focusDao().readLatestMigrationReport().importedCompletions);
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_RELATIONSHIPS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_PREREQUISITES"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_SCHEDULE_CONFIG"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_TRANSITION_STATS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_MISS_POLICIES"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_BUDGET_RECORDS"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_MEAL_RECORDS"));
        assertFalse(tableExists(migrated, "task_core"));
        migrated.close();
    }

    @Test
    public void physicalDeviceObservedV30FixtureMigratesThroughRealRoomOpen() throws Exception {
        File target = context.getDatabasePath(FocusDatabase.NAME);
        target.getParentFile().mkdirs();
        try (var input = getClass().getResourceAsStream("/fixtures/build4-v30.db")) {
            assertNotNull(input);
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        assertV30FixtureIdentity(target);
        LegacyDatabaseBackup.ensure(context);
        assertTrue(hasMigrationBackup(30));

        FocusDatabase migrated = openMigrated();
        var snapshot = new RoomWorkItemRepository(migrated).loadSnapshot();
        assertEquals(2, snapshot.workItems().size());
        assertEquals(1, snapshot.completions().size());
        assertEquals(1, migrated.focusDao().readMigrationCandidates().size());
        assertEquals(2, migrated.focusDao().readLatestMigrationReport().importedCompletions);
        assertEquals(30, migrated.focusDao().readLatestMigrationReport().sourceVersion);
        var routine = migrated.focusDao().readWorkItem(id("work-item", "v30-routine"));
        assertEquals("ROUTINE", routine.kind);
        assertEquals(7, routine.cadenceDays);
        assertEquals("2026-08-10", routine.nextDueDate);
        assertEquals("EVENING", routine.timePreference);
        assertFalse(tableExists(migrated, "task_core"));
        migrated.close();
    }

    @Test
    public void legacyV28AndV29HaveExplicitDirectMigrations() {
        assertMinimalLegacyCoreMigration(28);
        deleteDatabase();
        assertMinimalLegacyCoreMigration(29);
    }

    @Test
    public void preMigrationBackupPreservesDatabaseWalShmHashesAndArchive() throws Exception {
        File database = context.getDatabasePath(FocusDatabase.NAME);
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(database, null);
        legacy.enableWriteAheadLogging();
        legacy.setVersion(33);
        legacy.execSQL("CREATE TABLE legacy_fact (id TEXT PRIMARY KEY, value TEXT)");
        try (var checkpoint = legacy.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
            assertTrue(checkpoint.moveToFirst());
            assertEquals(0, checkpoint.getInt(0));
        }
        legacy.execSQL("INSERT INTO legacy_fact VALUES ('one', 'only-in-wal')");
        File wal = new File(database.getPath() + "-wal");
        File shm = new File(database.getPath() + "-shm");
        assertTrue(wal.isFile());
        assertTrue(shm.isFile());

        LegacyDatabaseBackup.ensure(context);
        // Reading user_version may legitimately refresh SQLite's transient SHM metadata. From
        // this point onward the source is idle, so the recovery copy must match it byte-for-byte.
        byte[] expectedDatabase = Files.readAllBytes(database.toPath());
        byte[] expectedWal = Files.readAllBytes(wal.toPath());
        byte[] expectedShm = Files.readAllBytes(shm.toPath());

        File archive = LegacyDatabaseBackup.latestArchive(context);
        assertNotNull(archive);
        String directoryName = archive.getName().substring(
                0, archive.getName().length() - ".zip".length());
        File backup = new File(archive.getParentFile(), directoryName);
        File backupDatabase = new File(backup, FocusDatabase.NAME);
        File backupWal = new File(backup, FocusDatabase.NAME + "-wal");
        File backupShm = new File(backup, FocusDatabase.NAME + "-shm");
        assertArrayEquals(expectedDatabase, Files.readAllBytes(backupDatabase.toPath()));
        assertArrayEquals(expectedWal, Files.readAllBytes(backupWal.toPath()));
        assertArrayEquals(expectedShm, Files.readAllBytes(backupShm.toPath()));
        JSONObject metadata = new JSONObject(new String(Files.readAllBytes(
                new File(backup, "metadata.json").toPath()), StandardCharsets.UTF_8));
        assertEquals(33, metadata.getInt("sourceVersion"));
        assertEquals(sha256(backupDatabase), metadata.getString("databaseSha256"));
        assertEquals(sha256(backupWal), metadata.getString("walSha256"));
        assertEquals(sha256(backupShm), metadata.getString("shmSha256"));
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive.toPath()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.add(entry.getName());
        }
        assertEquals(Set.of(FocusDatabase.NAME, FocusDatabase.NAME + "-wal",
                FocusDatabase.NAME + "-shm", "metadata.json"), entries);
        legacy.close();
    }

    private void assertBuild4FixtureIdentity(File fixture) throws Exception {
        java.util.Properties provenance = new java.util.Properties();
        try (var input = getClass().getResourceAsStream("/fixtures/build4-v27.properties")) {
            assertNotNull(input);
            provenance.load(input);
        }
        assertEquals("f5d9d0bc49b1caf690bb12a8a57f193042428db9",
                provenance.getProperty("sourceCommit"));
        assertEquals(provenance.getProperty("fixtureSha256"), sha256(fixture));
        SQLiteDatabase source = SQLiteDatabase.openDatabase(
                fixture.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (var version = source.rawQuery("PRAGMA user_version", null);
             var identity = source.rawQuery(
                     "SELECT identity_hash FROM room_master_table WHERE id = 42", null);
             var tables = source.rawQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'", null)) {
            assertTrue(version.moveToFirst());
            assertEquals(27, version.getInt(0));
            assertTrue(identity.moveToFirst());
            assertEquals(provenance.getProperty("roomIdentityHash"), identity.getString(0));
            assertTrue(tables.moveToFirst());
            assertEquals(27, tables.getInt(0));
        } finally {
            source.close();
        }
    }

    private void assertV30FixtureIdentity(File fixture) throws Exception {
        java.util.Properties provenance = new java.util.Properties();
        try (var input = getClass().getResourceAsStream("/fixtures/build4-v30.properties")) {
            assertNotNull(input);
            provenance.load(input);
        }
        assertEquals("false", provenance.getProperty("containsUserData"));
        assertEquals(provenance.getProperty("fixtureSha256"), sha256(fixture));
        SQLiteDatabase source = SQLiteDatabase.openDatabase(
                fixture.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try (var version = source.rawQuery("PRAGMA user_version", null);
             var identity = source.rawQuery(
                     "SELECT identity_hash FROM room_master_table WHERE id = 42", null);
             var tables = source.rawQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table'", null)) {
            assertTrue(version.moveToFirst());
            assertEquals(30, version.getInt(0));
            assertTrue(identity.moveToFirst());
            assertEquals(provenance.getProperty("roomIdentityHash"), identity.getString(0));
            assertTrue(tables.moveToFirst());
            assertEquals(29, tables.getInt(0));
        } finally {
            source.close();
        }
    }

    private void assertMinimalLegacyCoreMigration(int version) {
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(FocusDatabase.NAME), null);
        legacy.execSQL("""
                CREATE TABLE task_core (
                    id TEXT PRIMARY KEY NOT NULL, title TEXT, schedulingType TEXT,
                    minDuration INTEGER NOT NULL, created TEXT, adaptive INTEGER NOT NULL,
                    completed INTEGER NOT NULL, repetition_reps INTEGER,
                    repetition_perPeriod INTEGER, repetition_periodUnit TEXT,
                    repetition_periodStart TEXT, history_currentStreak INTEGER,
                    history_completions INTEGER)
                """);
        legacy.execSQL("""
                INSERT INTO task_core VALUES
                ('legacy','Expliziter Pfad','TASK',20,'2026-08-01',1,0,0,0,NULL,NULL,0,0)
                """);
        legacy.setVersion(version);
        legacy.close();

        FocusDatabase migrated = openMigrated();
        assertEquals(1, migrated.focusDao().readWorkItems().size());
        assertEquals(version, migrated.focusDao().readLatestMigrationReport().sourceVersion);
        assertTrue(hasMigrationBackup(version));
        migrated.close();
    }

    private static String sha256(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value));
        return result.toString();
    }

    @Test
    public void previewV33SkipsOnlyCorruptStepsAndConvertsRanksToRelativeDirectives() {
        assertPreviewMigration(33);
    }

    @Test
    public void previewV32MigratesDirectly() {
        assertPreviewMigration(32);
    }

    @Test
    public void previewV31MigratesDirectly() {
        assertPreviewMigration(31);
    }

    @Test
    public void previewRoutineWithDeadlineIsReviewedAndKeepsCompletionEvidence() {
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(FocusDatabase.NAME), null);
        legacy.execSQL("""
                CREATE TABLE obligations (
                    id TEXT PRIMARY KEY NOT NULL, kind TEXT, title TEXT,
                    durationMinutes INTEGER, deadlineAt TEXT, preferredTime TEXT,
                    flexible INTEGER, createdAt TEXT, completed INTEGER,
                    cadenceDays INTEGER, nextDueDate TEXT, currentStreak INTEGER,
                    bestStreak INTEGER, totalCompletions INTEGER, stepsJson TEXT,
                    manualOrderOn TEXT, manualOrderRank INTEGER)
                """);
        legacy.execSQL("""
                CREATE TABLE completions (
                    id TEXT PRIMARY KEY NOT NULL, obligationId TEXT, completedAt TEXT)
                """);
        legacy.execSQL("""
                INSERT INTO obligations VALUES
                ('deadline-routine','ROUTINE','Nicht still verlieren',30,
                 '2026-08-20T12:00:00','EVENING',0,'2026-08-01T09:00:00',0,
                 7,'2026-08-14',2,3,4,
                 '[{"id":"candidate-step","title":"Vorbereiten","days":["THURSDAY"],"completedAt":"2026-08-07T18:00:00","completedFor":"2026-08-07"}]',
                 NULL,0)
                """);
        legacy.execSQL("""
                INSERT INTO completions VALUES
                ('deadline-evidence','deadline-routine','2026-08-07T18:30:00')
                """);
        legacy.setVersion(33);
        legacy.close();

        FocusDatabase migrated = openMigrated();
        assertTrue(migrated.focusDao().readWorkItems().isEmpty());
        assertEquals(1, migrated.focusDao().readMigrationCandidates().size());
        assertEquals(1, migrated.focusDao().readLatestMigrationReport().importedCompletions);
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("QUARANTINED_PROTOTYPE_ITEMS"));

        RoomWorkItemRepository repository = new RoomWorkItemRepository(migrated);
        var review = repository.migrationReview();
        assertNotNull(review);
        String candidateId = id("work-item", "deadline-routine");
        repository.resolveMigrationCandidates(
                List.of(MigrationCandidateResolution.task(candidateId)), review.id(),
                java.time.LocalDateTime.parse("2026-08-12T10:00:00"));

        var restored = repository.find(candidateId);
        assertTrue(restored instanceof com.autosecretary.domain.Task);
        assertEquals(java.time.LocalDateTime.parse("2026-08-20T12:00:00"),
                restored.deadlineAt());
        assertEquals(com.autosecretary.domain.TimePreference.EVENING,
                restored.timePreference());
        assertFalse(restored.flexible());
        assertEquals(4, restored.stats().totalCompletions());
        assertEquals(1, restored.steps().size());
        assertEquals(id("step", "candidate-step"), restored.steps().get(0).id());
        assertEquals(Set.of(java.time.DayOfWeek.THURSDAY), restored.steps().get(0).days());
        assertEquals(1, repository.loadSnapshot().completions().size());
        assertTrue(repository.loadSnapshot().stepCompletions().stream()
                .anyMatch(value -> value.stepId().equals(id("step", "candidate-step"))
                        && value.completedAt().equals(java.time.LocalDateTime.parse(
                        "2026-08-07T18:00:00"))));
        migrated.close();
    }

    @Test
    public void previewInvalidCoreFieldsAreQuarantinedInsteadOfNormalized() {
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(FocusDatabase.NAME), null);
        legacy.execSQL("""
                CREATE TABLE obligations (
                    id TEXT PRIMARY KEY NOT NULL, kind TEXT, title TEXT,
                    durationMinutes INTEGER, deadlineAt TEXT, preferredTime TEXT,
                    flexible INTEGER, createdAt TEXT, completed INTEGER,
                    cadenceDays INTEGER, nextDueDate TEXT, currentStreak INTEGER,
                    bestStreak INTEGER, totalCompletions INTEGER, stepsJson TEXT,
                    manualOrderOn TEXT, manualOrderRank INTEGER)
                """);
        legacy.execSQL("""
                INSERT INTO obligations VALUES
                ('invalid-core','TASK','',2,NULL,NULL,1,'2026-08-01T09:00:00',0,
                 7,'2026-08-14',0,0,0,NULL,NULL,0)
                """);
        legacy.setVersion(33);
        legacy.close();

        FocusDatabase migrated = openMigrated();

        assertTrue(migrated.focusDao().readWorkItems().isEmpty());
        assertEquals(1, migrated.focusDao().readMigrationCandidates().size());
        assertEquals("CORRUPT_PROTOTYPE_UNSUPPORTED",
                migrated.focusDao().readMigrationCandidates().get(0).reason);
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("QUARANTINED_PROTOTYPE_ITEMS"));
        migrated.close();
    }

    private void assertPreviewMigration(int version) {
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(
                context.getDatabasePath(FocusDatabase.NAME), null);
        createHistoricalPreviewSchema(legacy, version);
        legacy.execSQL("""
                CREATE TABLE completions (
                    id TEXT PRIMARY KEY NOT NULL, obligationId TEXT, completedAt TEXT)
                """);
        insertHistoricalPreviewRows(legacy, version);
        legacy.execSQL("INSERT INTO completions VALUES ('c1','one','2026-08-03T08:00')");
        legacy.execSQL("CREATE TABLE budget_transaction (id TEXT PRIMARY KEY NOT NULL)");
        legacy.execSQL("INSERT INTO budget_transaction VALUES ('legacy-preview-budget')");
        legacy.setVersion(version);
        legacy.close();

        FocusDatabase migrated = openMigrated();
        assertEquals(2, migrated.focusDao().readWorkItems().size());
        String firstStepId = version == 31
                ? UUID.nameUUIDFromBytes((id("work-item", "one") + ":0:Lesen")
                        .getBytes(StandardCharsets.UTF_8)).toString()
                : id("step", "step-stable");
        assertEquals(firstStepId,
                migrated.focusDao().readSteps(id("work-item", "one")).get(0).id);
        if (version >= 32) {
            assertEquals(id("step", "step-after"),
                    migrated.focusDao().readSteps(id("work-item", "one")).get(1).id);
        }
        assertTrue(migrated.focusDao().readSteps(id("work-item", "two")).isEmpty());
        assertEquals(version == 31 ? 0 : 1, migrated.focusDao().readStepCompletions().size());
        var directives = migrated.focusDao().readDirectives("2026-08-11");
        assertEquals(2, directives.size());
        if (version == 31) {
            assertEquals("LAST", directives.get(0).relation);
            assertEquals("LAST", directives.get(1).relation);
            assertNull(directives.get(0).anchorWorkItemId);
            assertNull(directives.get(1).anchorWorkItemId);
        } else {
            assertEquals("FIRST", directives.get(0).relation);
            assertEquals("AFTER", directives.get(1).relation);
            assertEquals(id("work-item", "one"), directives.get(1).anchorWorkItemId);
        }
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("CORRUPT_STEPS_SKIPPED"));
        assertEquals(version >= 32,
                migrated.focusDao().readLatestMigrationReport().warningsJson
                        .contains("CORRUPT_STEP_COMPLETIONS_SKIPPED"));
        assertTrue(migrated.focusDao().readLatestMigrationReport().warningsJson
                .contains("DISCARDED_BUDGET_RECORDS"));
        assertEquals(version, migrated.focusDao().readLatestMigrationReport().sourceVersion);
        assertFalse(tableExists(migrated, "obligations"));
        assertFalse(tableExists(migrated, "completions"));
        migrated.close();
    }

    private static void createHistoricalPreviewSchema(SQLiteDatabase database, int version) {
        if (version == 31) {
            database.execSQL("""
                    CREATE TABLE obligations (
                        id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL, deadlineAt TEXT,
                        cadenceDays INTEGER NOT NULL, nextDueDate TEXT, stepsJson TEXT,
                        createdAt TEXT NOT NULL, completed INTEGER NOT NULL,
                        currentStreak INTEGER NOT NULL, bestStreak INTEGER NOT NULL,
                        totalCompletions INTEGER NOT NULL, postponedOn TEXT,
                        postponedRank INTEGER NOT NULL)
                    """);
        } else if (version == 32) {
            database.execSQL("""
                    CREATE TABLE obligations (
                        id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL, deadlineAt TEXT,
                        cadenceDays INTEGER NOT NULL, nextDueDate TEXT, preferredTime TEXT,
                        stepsJson TEXT, createdAt TEXT NOT NULL, completed INTEGER NOT NULL,
                        currentStreak INTEGER NOT NULL, bestStreak INTEGER NOT NULL,
                        totalCompletions INTEGER NOT NULL, manualOrderOn TEXT,
                        manualOrderRank INTEGER NOT NULL)
                    """);
        } else if (version == 33) {
            database.execSQL("""
                    CREATE TABLE obligations (
                        id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL, deadlineAt TEXT,
                        cadenceDays INTEGER NOT NULL, nextDueDate TEXT, preferredTime TEXT,
                        flexible INTEGER NOT NULL DEFAULT 1, stepsJson TEXT,
                        createdAt TEXT NOT NULL, completed INTEGER NOT NULL,
                        currentStreak INTEGER NOT NULL, bestStreak INTEGER NOT NULL,
                        totalCompletions INTEGER NOT NULL, manualOrderOn TEXT,
                        manualOrderRank INTEGER NOT NULL)
                    """);
        } else {
            throw new IllegalArgumentException("Keine historische Preview-Version: " + version);
        }
    }

    private static void insertHistoricalPreviewRows(SQLiteDatabase database, int version) {
        String modernSteps = "[{\"id\":\"step-stable\",\"title\":\"Lesen\","
                + "\"days\":[\"MONDAY\"],\"completedAt\":\"2026-08-03T08:00\","
                + "\"completedFor\":\"TASK\"},{\"id\":\"bad-day\",\"title\":\"Defekt\","
                + "\"days\":[\"FUNDAY\"]},{\"id\":\"step-after\",\"title\":\"Danach\","
                + "\"days\":[],\"completedAt\":\"keine-zeit\",\"completedFor\":\"TASK\"}]";
        String version31Steps = "[{\"title\":\"Lesen\",\"days\":[\"MONDAY\"]},"
                + "{\"title\":\"Defekt\",\"days\":[\"FUNDAY\"]}]";
        if (version == 31) {
            database.execSQL("""
                    INSERT INTO obligations (
                        id, kind, title, durationMinutes, deadlineAt, cadenceDays, nextDueDate,
                        stepsJson, createdAt, completed, currentStreak, bestStreak,
                        totalCompletions, postponedOn, postponedRank)
                    VALUES (?, 'TASK', 'Erste', 20, NULL, 0, NULL, ?,
                            '2026-08-01T10:00', 0, 0, 0, 0, '2026-08-11', 10),
                           ('two', 'TASK', 'Zweite', 30, NULL, 0, NULL, 'not-json',
                            '2026-08-01T11:00', 0, 0, 0, 0, '2026-08-11', 20)
                    """, new Object[]{"one", version31Steps});
        } else if (version == 32) {
            database.execSQL("""
                    INSERT INTO obligations (
                        id, kind, title, durationMinutes, deadlineAt, cadenceDays, nextDueDate,
                        preferredTime, stepsJson, createdAt, completed, currentStreak, bestStreak,
                        totalCompletions, manualOrderOn, manualOrderRank)
                    VALUES (?, 'TASK', 'Erste', 20, NULL, 0, NULL, 'MORNING', ?,
                            '2026-08-01T10:00', 0, 0, 0, 0, '2026-08-11', 10),
                           ('two', 'TASK', 'Zweite', 30, NULL, 0, NULL, NULL, 'not-json',
                            '2026-08-01T11:00', 0, 0, 0, 0, '2026-08-11', 20)
                    """, new Object[]{"one", modernSteps});
        } else {
            database.execSQL("""
                    INSERT INTO obligations (
                        id, kind, title, durationMinutes, deadlineAt, cadenceDays, nextDueDate,
                        preferredTime, flexible, stepsJson, createdAt, completed, currentStreak,
                        bestStreak, totalCompletions, manualOrderOn, manualOrderRank)
                    VALUES (?, 'TASK', 'Erste', 20, NULL, 0, NULL, 'MORNING', 1, ?,
                            '2026-08-01T10:00', 0, 0, 0, 0, '2026-08-11', 10),
                           ('two', 'TASK', 'Zweite', 30, NULL, 0, NULL, NULL, 1, 'not-json',
                            '2026-08-01T11:00', 0, 0, 0, 0, '2026-08-11', 20)
                    """, new Object[]{"one", modernSteps});
        }
    }

    private FocusDatabase openMigrated() {
        LegacyDatabaseBackup.ensure(context);
        FocusDatabase result = Room.databaseBuilder(context, FocusDatabase.class, FocusDatabase.NAME)
                .addMigrations(FocusDatabase.migrations())
                .allowMainThreadQueries()
                .build();
        result.getOpenHelper().getWritableDatabase();
        return result;
    }

    private boolean hasMigrationBackup(int version) {
        File root = new File(context.getFilesDir(), "migration-backups");
        File[] backups = root.listFiles(file -> file.isDirectory()
                && file.getName().startsWith("v" + version + "-"));
        return backups != null && Arrays.stream(backups).anyMatch(file ->
                new File(file, FocusDatabase.NAME).isFile()
                        && new File(file, "metadata.json").isFile()
                        && new File(root, file.getName() + ".zip").isFile());
    }

    private void deleteDatabase() {
        context.deleteDatabase(FocusDatabase.NAME);
        context.getSharedPreferences("migration_backup", Context.MODE_PRIVATE).edit().clear().commit();
        deleteTree(new File(context.getFilesDir(), "migration-backups"));
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private boolean tableExists(FocusDatabase database, String name) {
        try (android.database.Cursor cursor = database.getOpenHelper().getReadableDatabase().query(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new Object[]{name})) {
            return cursor.moveToFirst();
        }
    }

    private static String id(String namespace, String value) {
        return UUID.nameUUIDFromBytes((namespace + ":" + value)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
