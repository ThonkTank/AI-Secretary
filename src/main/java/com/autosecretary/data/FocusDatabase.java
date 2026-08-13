package com.autosecretary.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.autosecretary.data.entity.CompletionEntity;
import com.autosecretary.data.entity.DayPlanDirectiveEntity;
import com.autosecretary.data.entity.MigrationCandidateEntity;
import com.autosecretary.data.entity.MigrationReportEntity;
import com.autosecretary.data.entity.PlannedSlotEntity;
import com.autosecretary.data.entity.PlanningConflictEntity;
import com.autosecretary.data.entity.StepCompletionEntity;
import com.autosecretary.data.entity.StepDayEntity;
import com.autosecretary.data.entity.StepEntity;
import com.autosecretary.data.entity.UndoJournalEntity;
import com.autosecretary.data.entity.WorkItemEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Database(
        entities = {
                WorkItemEntity.class,
                StepEntity.class,
                StepDayEntity.class,
                StepCompletionEntity.class,
                CompletionEntity.class,
                DayPlanDirectiveEntity.class,
                PlannedSlotEntity.class,
                PlanningConflictEntity.class,
                UndoJournalEntity.class,
                MigrationReportEntity.class,
                MigrationCandidateEntity.class
        },
        version = FocusDatabase.VERSION,
        exportSchema = true)
public abstract class FocusDatabase extends RoomDatabase {
    public static final String NAME = "autosecretary.db";
    public static final int VERSION = 34;

    public static final Migration MIGRATION_27_34 = new CoreMigration(27);
    public static final Migration MIGRATION_28_34 = new CoreMigration(28);
    public static final Migration MIGRATION_29_34 = new CoreMigration(29);
    public static final Migration MIGRATION_30_34 = new CoreMigration(30);
    public static final Migration MIGRATION_31_34 = new CoreMigration(31);
    public static final Migration MIGRATION_32_34 = new CoreMigration(32);
    public static final Migration MIGRATION_33_34 = new CoreMigration(33);

    public abstract FocusDao focusDao();

    public static Migration[] migrations() {
        return new Migration[]{
                MIGRATION_27_34, MIGRATION_28_34, MIGRATION_29_34, MIGRATION_30_34,
                MIGRATION_31_34, MIGRATION_32_34, MIGRATION_33_34
        };
    }

    private static final class CoreMigration extends Migration {
        private final int sourceVersion;

        CoreMigration(int sourceVersion) {
            super(sourceVersion, 34);
            this.sourceVersion = sourceVersion;
        }

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            createSchema(database);
            MigrationSummary summary = sourceVersion <= 30
                    ? migrateLegacyCore(database)
                    : migratePrototype(database, sourceVersion);
            insertReport(database, sourceVersion, summary);
            dropLegacyTables(database);
        }
    }

    private static void createSchema(SupportSQLiteDatabase db) {
        db.execSQL("PRAGMA foreign_keys=ON");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS work_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    deadlineAt TEXT,
                    timePreference TEXT,
                    flexible INTEGER NOT NULL,
                    createdAt TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    cadenceDays INTEGER NOT NULL,
                    nextDueDate TEXT,
                    currentStreak INTEGER NOT NULL,
                    bestStreak INTEGER NOT NULL,
                    totalCompletions INTEGER NOT NULL,
                    revision INTEGER NOT NULL
                )
                """);
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS steps (
                    id TEXT NOT NULL PRIMARY KEY,
                    workItemId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    FOREIGN KEY(workItemId) REFERENCES work_items(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_steps_workItemId_position ON steps(workItemId, position)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS step_days (
                    stepId TEXT NOT NULL,
                    dayOfWeek TEXT NOT NULL,
                    PRIMARY KEY(stepId, dayOfWeek),
                    FOREIGN KEY(stepId) REFERENCES steps(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS index_step_days_stepId ON step_days(stepId)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS step_completions (
                    id TEXT NOT NULL PRIMARY KEY,
                    stepId TEXT NOT NULL,
                    occurrenceKey TEXT NOT NULL,
                    completedAt TEXT NOT NULL,
                    FOREIGN KEY(stepId) REFERENCES steps(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS index_step_completions_stepId ON step_completions(stepId)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_step_completions_stepId_occurrenceKey ON step_completions(stepId, occurrenceKey)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS work_item_completions (
                    id TEXT NOT NULL PRIMARY KEY,
                    workItemId TEXT NOT NULL,
                    occurrenceKey TEXT NOT NULL,
                    completedAt TEXT NOT NULL,
                    FOREIGN KEY(workItemId) REFERENCES work_items(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_item_completions_workItemId ON work_item_completions(workItemId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_work_item_completions_completedAt ON work_item_completions(completedAt)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS day_plan_directives (
                    id TEXT NOT NULL PRIMARY KEY,
                    day TEXT NOT NULL,
                    workItemId TEXT NOT NULL,
                    relation TEXT NOT NULL,
                    anchorWorkItemId TEXT,
                    updatedAt TEXT NOT NULL,
                    FOREIGN KEY(workItemId) REFERENCES work_items(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_day_plan_directives_day_workItemId ON day_plan_directives(day, workItemId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_day_plan_directives_workItemId ON day_plan_directives(workItemId)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS planned_slots (
                    id TEXT NOT NULL PRIMARY KEY,
                    workItemId TEXT NOT NULL,
                    occurrenceKey TEXT NOT NULL,
                    day TEXT NOT NULL,
                    startAt TEXT NOT NULL,
                    endAt TEXT NOT NULL,
                    computedAt TEXT NOT NULL,
                    FOREIGN KEY(workItemId) REFERENCES work_items(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_slots_workItemId ON planned_slots(workItemId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_slots_day ON planned_slots(day)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS planning_conflicts (
                    id TEXT NOT NULL PRIMARY KEY,
                    workItemId TEXT NOT NULL,
                    occurrenceKey TEXT NOT NULL,
                    reason TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    computedAt TEXT NOT NULL,
                    FOREIGN KEY(workItemId) REFERENCES work_items(id) ON DELETE CASCADE
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planning_conflicts_workItemId ON planning_conflicts(workItemId)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS undo_journal (
                    id TEXT NOT NULL PRIMARY KEY,
                    kind TEXT NOT NULL,
                    label TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    undoneAt TEXT
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS index_undo_journal_createdAt ON undo_journal(createdAt)");
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS migration_reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceVersion INTEGER NOT NULL,
                    completedAt TEXT NOT NULL,
                    importedItems INTEGER NOT NULL,
                    importedCompletions INTEGER NOT NULL,
                    candidateItems INTEGER NOT NULL,
                    warningsJson TEXT NOT NULL,
                    acknowledged INTEGER NOT NULL
                )
                """);
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS migration_candidates (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    deadlineAt TEXT,
                    reason TEXT NOT NULL,
                    legacyPayloadJson TEXT NOT NULL
                )
                """);
    }

    private static MigrationSummary migratePrototype(
            SupportSQLiteDatabase db,
            int sourceVersion) {
        MigrationSummary summary = new MigrationSummary();
        Map<String, List<ManualRank>> ranks = new HashMap<>();
        Map<String, List<ManualRank>> postponed = new HashMap<>();
        try (Cursor cursor = db.query("SELECT * FROM obligations")) {
            while (cursor.moveToNext()) {
                ContentValues item = new ContentValues();
                String legacyId = text(cursor, "id", UUID.randomUUID().toString());
                String id = stableUuid(legacyId, "work-item");
                String rawKind = nullableText(cursor, "kind");
                String kind = nonBlank(rawKind, "TASK");
                String rawTitle = nullableText(cursor, "title");
                String title = nonBlank(rawTitle, "Aufgabe");
                int rawDuration = integer(cursor, "durationMinutes", 30);
                int duration = boundedDuration(rawDuration);
                String deadlineAt = nullableText(cursor, "deadlineAt");
                String createdAt = text(cursor, "createdAt", LocalDateTime.now().toString());
                String nextDueDate = nullableText(cursor, "nextDueDate");
                String preferredTime = nullableText(cursor, "preferredTime");
                if (!validPreference(preferredTime)) {
                    preferredTime = null;
                    incrementWarning(summary, "DISCARDED_AMBIGUOUS_TIME_PREFERENCES");
                }
                int rawCurrentStreak = integer(cursor, "currentStreak", 0);
                int rawBestStreak = integer(cursor, "bestStreak", 0);
                int rawTotalCompletions = integer(cursor, "totalCompletions", 0);
                int rawCadence = integer(cursor, "cadenceDays", 0);
                int rawFlexible = integer(cursor, "flexible", 1);
                int rawCompletedValue = integer(cursor, "completed", 0);
                boolean rawCompleted = rawCompletedValue != 0;
                int currentStreak = Math.max(0, rawCurrentStreak);
                int bestStreak = Math.max(currentStreak, rawBestStreak);
                int totalCompletions = Math.max(bestStreak,
                        rawTotalCompletions);
                String candidateReason = prototypeCandidateReason(
                        kind, rawTitle, rawDuration, deadlineAt, createdAt, nextDueDate,
                        rawCadence, rawCompleted, rawCurrentStreak, rawBestStreak,
                        rawTotalCompletions, rawKind, rawFlexible, rawCompletedValue);
                if (candidateReason != null) {
                    insertPrototypeCandidate(db, cursor, id, title, duration, kind,
                            deadlineAt, createdAt, nextDueDate, preferredTime,
                            currentStreak, bestStreak, totalCompletions, candidateReason,
                            summary);
                    summary.candidateItems++;
                    incrementWarning(summary, "QUARANTINED_PROTOTYPE_ITEMS");
                    continue;
                }
                item.put("id", id);
                item.put("kind", kind);
                item.put("title", title);
                item.put("durationMinutes", duration);
                putNullable(item, "deadlineAt", deadlineAt);
                putNullable(item, "timePreference", preferredTime);
                item.put("flexible", rawFlexible != 0);
                item.put("createdAt", createdAt);
                item.put("completed", "TASK".equals(kind) && rawCompleted);
                item.put("cadenceDays", "ROUTINE".equals(kind)
                        ? rawCadence : 0);
                putNullable(item, "nextDueDate", "ROUTINE".equals(kind)
                        ? nextDueDate : null);
                item.put("currentStreak", currentStreak);
                item.put("bestStreak", bestStreak);
                item.put("totalCompletions", totalCompletions);
                item.put("revision", 0);
                db.insert("work_items", SQLiteDatabase.CONFLICT_REPLACE, item);
                summary.importedItems++;

                migratePrototypeSteps(db, id, nullableText(cursor, "stepsJson"), summary);
                String orderDay = nullableText(cursor, "manualOrderOn");
                long rank = longValue(cursor, "manualOrderRank", 0);
                if (orderDay != null && rank > 0) {
                    ranks.computeIfAbsent(orderDay, ignored -> new ArrayList<>())
                            .add(new ManualRank(id, rank));
                }
                String postponedDay = nullableText(cursor, "postponedOn");
                long postponedRank = longValue(cursor, "postponedRank", 0);
                if (sourceVersion == 31 && postponedDay != null && postponedRank > 0) {
                    postponed.computeIfAbsent(postponedDay, ignored -> new ArrayList<>())
                            .add(new ManualRank(id, postponedRank));
                }
            }
        }
        if (tableExists(db, "completions")) {
            try (Cursor cursor = db.query("SELECT * FROM completions")) {
                while (cursor.moveToNext()) {
                    String workItemId = stableUuid(text(cursor, "obligationId", ""),
                            "work-item");
                    ContentValues completion = new ContentValues();
                    completion.put("id", stableUuid(
                            text(cursor, "id", UUID.randomUUID().toString()), "completion"));
                    completion.put("workItemId", workItemId);
                    completion.put("occurrenceKey", "legacy");
                    String completedAt = text(cursor, "completedAt", LocalDateTime.now().toString());
                    try { LocalDateTime.parse(completedAt); }
                    catch (RuntimeException corruptRecord) {
                        incrementWarning(summary, "CORRUPT_COMPLETIONS_SKIPPED");
                        continue;
                    }
                    completion.put("completedAt", completedAt);
                    if (workItemExists(db, workItemId)) {
                        db.insert("work_item_completions", SQLiteDatabase.CONFLICT_REPLACE, completion);
                        summary.importedCompletions++;
                    } else if (migrationCandidateExists(db, workItemId)) {
                        appendCandidateCompletion(db, workItemId, completion);
                        summary.importedCompletions++;
                    }
                }
            }
        }
        migrateManualRanks(db, ranks);
        migratePostponedRanks(db, postponed);
        countDiscardedLegacyFacts(db, summary);
        return summary;
    }

    private static String prototypeCandidateReason(
            String kind,
            String title,
            int duration,
            String deadlineAt,
            String createdAt,
            String nextDueDate,
            int cadenceDays,
            boolean completed,
            int currentStreak,
            int bestStreak,
            int totalCompletions,
            String rawKind,
            int rawFlexible,
            int rawCompleted) {
        if (rawKind == null || rawKind.isBlank()
                || !"TASK".equals(kind) && !"ROUTINE".equals(kind)) {
            return "CORRUPT_PROTOTYPE_UNSUPPORTED";
        }
        if (title == null || title.isBlank() || duration < 5 || duration > 480
                || !validDateTime(createdAt)
                || deadlineAt != null && !validDateTime(deadlineAt)
                || currentStreak < 0 || bestStreak < currentStreak
                || totalCompletions < bestStreak
                || rawFlexible < 0 || rawFlexible > 1
                || rawCompleted < 0 || rawCompleted > 1) {
            return "CORRUPT_PROTOTYPE_UNSUPPORTED";
        }
        if ("ROUTINE".equals(kind)) {
            if (deadlineAt != null) return "ROUTINE_DEADLINE_UNSUPPORTED";
            if (cadenceDays < 1 || nextDueDate == null || !validDate(nextDueDate) || completed) {
                return "CORRUPT_PROTOTYPE_UNSUPPORTED";
            }
        } else if (cadenceDays != 0 || nextDueDate != null) {
            return "CORRUPT_PROTOTYPE_UNSUPPORTED";
        }
        return null;
    }

    private static void insertPrototypeCandidate(
            SupportSQLiteDatabase db,
            Cursor cursor,
            String id,
            String title,
            int duration,
            String kind,
            String deadlineAt,
            String createdAt,
            String nextDueDate,
            String preferredTime,
            int currentStreak,
            int bestStreak,
            int totalCompletions,
            String reason,
            MigrationSummary summary) {
        ContentValues candidate = new ContentValues();
        candidate.put("id", id);
        candidate.put("title", title);
        candidate.put("durationMinutes", duration);
        putNullable(candidate, "deadlineAt", validDateTime(deadlineAt) ? deadlineAt : null);
        candidate.put("reason", reason);
        JSONObject payload = new JSONObject();
        try {
            payload.put("legacyKind", kind);
            payload.put("legacyDeadlineAt", deadlineAt == null ? JSONObject.NULL : deadlineAt);
            payload.put("legacyNextDueDate", nextDueDate == null ? JSONObject.NULL : nextDueDate);
            payload.put("timePreference", preferredTime == null ? JSONObject.NULL : preferredTime);
            payload.put("flexible", integer(cursor, "flexible", 1) != 0);
            payload.put("createdAt", validDateTime(createdAt)
                    ? createdAt : LocalDateTime.now().toString());
            payload.put("completed", integer(cursor, "completed", 0) != 0);
            payload.put("cadenceDays", Math.max(1, integer(cursor, "cadenceDays", 1)));
            payload.put("suggestedDueDate", validDate(nextDueDate)
                    ? nextDueDate : LocalDate.now().toString());
            payload.put("currentStreak", currentStreak);
            payload.put("bestStreak", bestStreak);
            payload.put("totalCompletions", totalCompletions);
            payload.put("steps", normalizedPrototypeSteps(
                    id, nullableText(cursor, "stepsJson"), summary));
        } catch (Exception error) {
            throw new IllegalStateException("Prototypeintrag konnte nicht quarantänisiert werden", error);
        }
        candidate.put("legacyPayloadJson", payload.toString());
        db.insert("migration_candidates", SQLiteDatabase.CONFLICT_ABORT, candidate);
    }

    private static void migratePrototypeSteps(
            SupportSQLiteDatabase db,
            String workItemId,
            String encoded,
            MigrationSummary summary) {
        JSONArray steps = normalizedPrototypeSteps(workItemId, encoded, summary);
        for (int position = 0; position < steps.length(); position++) {
            JSONObject source = steps.optJSONObject(position);
            if (source == null) continue;
            String stepId = source.optString("id");
            ContentValues step = new ContentValues();
            step.put("id", stepId);
            step.put("workItemId", workItemId);
            step.put("title", source.optString("title"));
            step.put("position", position);
            db.insert("steps", SQLiteDatabase.CONFLICT_REPLACE, step);
            JSONArray days = source.optJSONArray("days");
            if (days == null) days = new JSONArray();
            for (int index = 0; index < days.length(); index++) {
                ContentValues day = new ContentValues();
                day.put("stepId", stepId);
                day.put("dayOfWeek", days.optString(index));
                db.insert("step_days", SQLiteDatabase.CONFLICT_IGNORE, day);
            }
            if (source.has("completedAt")) {
                ContentValues completion = new ContentValues();
                completion.put("id", source.optString("completionId"));
                completion.put("stepId", stepId);
                completion.put("occurrenceKey", source.optString("occurrenceKey"));
                completion.put("completedAt", source.optString("completedAt"));
                db.insert("step_completions", SQLiteDatabase.CONFLICT_REPLACE, completion);
            }
        }
    }

    private static JSONArray normalizedPrototypeSteps(
            String workItemId,
            String encoded,
            MigrationSummary summary) {
        JSONArray result = new JSONArray();
        if (encoded == null || encoded.isBlank()) return result;
        JSONArray steps;
        try { steps = new JSONArray(encoded); }
        catch (Exception corruptList) {
            incrementWarning(summary, "CORRUPT_STEPS_SKIPPED");
            return result;
        }
        Set<String> seenIds = new java.util.HashSet<>();
        for (int sourcePosition = 0; sourcePosition < steps.length(); sourcePosition++) {
            try {
                JSONObject source = steps.getJSONObject(sourcePosition);
                String stepId = source.optString("id");
                if (stepId.isBlank()) {
                    stepId = UUID.nameUUIDFromBytes(
                            (workItemId + ":" + sourcePosition + ":" + source.optString("title"))
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                } else stepId = stableUuid(stepId, "step");
                if (!seenIds.add(stepId)) throw new IllegalArgumentException("Doppelte Schritt-ID");
                String title = source.optString("title", "").trim();
                if (title.isEmpty()) throw new IllegalArgumentException("Leerer Schritttitel");
                JSONArray validDays = new JSONArray();
                JSONArray days = source.optJSONArray("days");
                if (days != null) {
                    for (int index = 0; index < days.length(); index++) {
                        validDays.put(java.time.DayOfWeek.valueOf(days.getString(index)).name());
                    }
                }
                JSONObject normalized = new JSONObject()
                        .put("id", stepId).put("title", title).put("days", validDays);
                String completedAt = source.isNull("completedAt")
                        ? null : source.optString("completedAt", null);
                if (completedAt != null && !completedAt.isBlank()) {
                    String completedFor = source.isNull("completedFor")
                            ? null : source.optString("completedFor", null);
                    try {
                        LocalDateTime.parse(completedAt);
                        if (completedFor != null && !"TASK".equals(completedFor)) {
                            LocalDate.parse(completedFor);
                        }
                        String occurrenceKey = completedFor == null ? "TASK" : completedFor;
                        normalized.put("completionId", stableUuid(
                                stepId + ":" + occurrenceKey + ":" + completedAt,
                                "step-completion"));
                        normalized.put("occurrenceKey", occurrenceKey);
                        normalized.put("completedAt", completedAt);
                    } catch (RuntimeException corruptCompletion) {
                        incrementWarning(summary, "CORRUPT_STEP_COMPLETIONS_SKIPPED");
                    }
                }
                result.put(normalized);
            } catch (Exception corruptStep) {
                incrementWarning(summary, "CORRUPT_STEPS_SKIPPED");
            }
        }
        return result;
    }

    private static MigrationSummary migrateLegacyCore(SupportSQLiteDatabase db) {
        MigrationSummary summary = new MigrationSummary();
        if (!tableExists(db, "task_core")) return summary;
        try (Cursor cursor = db.query("SELECT * FROM task_core")) {
            while (cursor.moveToNext()) {
                String legacyId = text(cursor, "id", UUID.randomUUID().toString());
                String id = stableUuid(legacyId, "work-item");
                String rawTitle = nullableText(cursor, "title");
                String title = nonBlank(rawTitle, "Aufgabe");
                String schedulingType = text(cursor, "schedulingType", "TASK");
                int rawDuration = integer(cursor, "minDuration", 30);
                int duration = legacyDuration(cursor, schedulingType);
                String deadline = nullableText(cursor, "deadline");
                boolean validDeadline = deadline == null || validDate(deadline);
                String deadlineAt = deadline == null || !validDeadline
                        ? null : deadline + "T23:59:00";
                int rawRepetitions = integer(cursor, "repetition_reps", 0);
                int repetitions = Math.max(0, rawRepetitions);
                int perPeriod = integer(cursor, "repetition_perPeriod", 0);
                String unit = nullableText(cursor, "repetition_periodUnit");
                int rawPeriodCompletions = integer(
                        cursor, "repetition_periodCompletions", 0);
                int periodCompletions = Math.max(0, rawPeriodCompletions);
                int rawCompleteFirst = integer(cursor, "repetition_completeFirst", 0);
                boolean completeFirst = rawCompleteFirst != 0;
                int rawCarryoverDebt = integer(cursor, "repetition_carryoverDebt", 0);
                int carryoverDebt = Math.max(0, rawCarryoverDebt);
                int rawCooldown = integer(cursor, "cooldown", 1);
                int cooldown = Math.max(0, rawCooldown);
                int cadenceDays = exactCadenceDays(unit, perPeriod);
                String periodStart = nullableText(cursor, "repetition_periodStart");
                boolean validPeriodStart = validDate(periodStart);
                boolean simpleRecurrence = repetitions == 1 && perPeriod > 0
                        && cadenceDays > 0 && cadenceDays <= 365
                        && periodCompletions <= repetitions
                        && !completeFirst && carryoverDebt == 0 && cooldown <= 1
                        && validPeriodStart;
                boolean routineDeadline = simpleRecurrence && deadlineAt != null;
                LegacyPreference preference = legacyTimePreference(db, legacyId);
                if (preference.unmapped()) {
                    incrementWarning(summary, "DISCARDED_AMBIGUOUS_TIME_PREFERENCES");
                }
                String rawCreatedAt = nullableText(cursor, "created");
                String createdAt = legacyCreatedAt(cursor);
                int rawAdaptive = integer(cursor, "adaptive", 0);
                int rawCompleted = integer(cursor, "completed", 0);
                int rawCurrentStreak = integer(cursor, "history_currentStreak", 0);
                int rawTotalCompletions = integer(cursor, "history_completions", 0);
                boolean corruptCore = rawTitle == null || rawTitle.isBlank()
                        || !"TASK".equals(schedulingType) && !"TERMIN".equals(schedulingType)
                        || !"TERMIN".equals(schedulingType)
                        && (rawDuration < 5 || rawDuration > 480)
                        || !validDeadline || !validLegacyCreated(rawCreatedAt)
                        || rawRepetitions < 0 || perPeriod < 0
                        || rawPeriodCompletions < 0 || rawCarryoverDebt < 0
                        || rawCooldown < 0 || rawCurrentStreak < 0
                        || rawTotalCompletions < rawCurrentStreak
                        || rawCompleteFirst < 0 || rawCompleteFirst > 1
                        || rawAdaptive < 0 || rawAdaptive > 1
                        || rawCompleted < 0 || rawCompleted > 1;
                int streak = Math.max(0, rawCurrentStreak);
                int totalCompletions = Math.max(streak, rawTotalCompletions);
                if (corruptCore || repetitions > 1 || repetitions == 1 && !simpleRecurrence
                        || routineDeadline || "TERMIN".equals(schedulingType)) {
                    ContentValues candidate = new ContentValues();
                    candidate.put("id", id);
                    candidate.put("title", title);
                    candidate.put("durationMinutes", duration);
                    String candidateDeadline = "TERMIN".equals(schedulingType)
                            ? appointmentDeadlineAt(cursor) : deadlineAt;
                    putNullable(candidate, "deadlineAt", candidateDeadline);
                    candidate.put("reason", "TERMIN".equals(schedulingType)
                            ? "FIXED_APPOINTMENT_UNSUPPORTED"
                            : corruptCore ? "CORRUPT_LEGACY_CORE_UNSUPPORTED"
                            : routineDeadline ? "ROUTINE_DEADLINE_UNSUPPORTED"
                            : "COMPLEX_RECURRENCE_UNSUPPORTED");
                    JSONObject payload = new JSONObject();
                    try {
                        payload.put("repetitions", repetitions);
                        payload.put("perPeriod", perPeriod);
                        payload.put("periodUnit", unit == null ? JSONObject.NULL : unit);
                        payload.put("periodCompletions", periodCompletions);
                        payload.put("periodStart", periodStart);
                        payload.put("completeFirst", completeFirst);
                        payload.put("carryoverDebt", carryoverDebt);
                        payload.put("cooldown", cooldown);
                        payload.put("fixedDate", nullableText(cursor, "fixedDate"));
                        payload.put("fixedStart", nullableText(cursor, "fixedStart"));
                        payload.put("fixedEnd", nullableText(cursor, "fixedEnd"));
                        payload.put("fixedDuration", nullableText(cursor, "fixedDuration"));
                        payload.put("timePreference", preference.value() == null
                                ? JSONObject.NULL : preference.value());
                        payload.put("flexible", rawAdaptive != 0);
                        payload.put("createdAt", createdAt);
                        payload.put("completed", rawCompleted != 0);
                        payload.put("currentStreak", streak);
                        payload.put("bestStreak", streak);
                        payload.put("totalCompletions", totalCompletions);
                        payload.put("suggestedDueDate", suggestedLegacyDueDate(
                                periodStart, createdAt, fixedDate(cursor)));
                        payload.put("completionEvidence", new JSONArray());
                        payload.put("rawTitle", rawTitle == null ? JSONObject.NULL : rawTitle);
                        payload.put("rawDuration", rawDuration);
                        payload.put("rawDeadline", deadline == null ? JSONObject.NULL : deadline);
                        payload.put("rawCreatedAt", rawCreatedAt == null
                                ? JSONObject.NULL : rawCreatedAt);
                        payload.put("rawRepetitions", rawRepetitions);
                        payload.put("rawPeriodCompletions", rawPeriodCompletions);
                        payload.put("rawCarryoverDebt", rawCarryoverDebt);
                        payload.put("rawCooldown", rawCooldown);
                        payload.put("rawCompleteFirst", rawCompleteFirst);
                        payload.put("rawAdaptive", rawAdaptive);
                        payload.put("rawCompleted", rawCompleted);
                        payload.put("rawCurrentStreak", rawCurrentStreak);
                        payload.put("rawTotalCompletions", rawTotalCompletions);
                    } catch (Exception ignored) { }
                    candidate.put("legacyPayloadJson", payload.toString());
                    db.insert("migration_candidates", SQLiteDatabase.CONFLICT_REPLACE, candidate);
                    summary.candidateItems++;
                    if (corruptCore) incrementWarning(
                            summary, "QUARANTINED_CORRUPT_CORE_ITEMS");
                    continue;
                }

                boolean routine = simpleRecurrence;
                ContentValues item = new ContentValues();
                item.put("id", id);
                item.put("kind", routine ? "ROUTINE" : "TASK");
                item.put("title", title);
                item.put("durationMinutes", duration);
                putNullable(item, "deadlineAt", deadlineAt);
                putNullable(item, "timePreference", preference.value());
                item.put("flexible", rawAdaptive != 0);
                item.put("createdAt", createdAt);
                item.put("completed", !routine && rawCompleted != 0);
                item.put("cadenceDays", routine ? cadenceDays : 0);
                String due = periodStart;
                if (due == null) due = LocalDateTime.parse(createdAt).toLocalDate().toString();
                if (routine && due != null
                        && periodCompletions >= repetitions) {
                    try { due = LocalDate.parse(due).plusDays(cadenceDays).toString(); }
                    catch (RuntimeException ignored) { }
                }
                putNullable(item, "nextDueDate", routine ? due : null);
                item.put("currentStreak", streak);
                item.put("bestStreak", streak);
                item.put("totalCompletions", totalCompletions);
                item.put("revision", 0);
                db.insert("work_items", SQLiteDatabase.CONFLICT_REPLACE, item);
                summary.importedItems++;
            }
        }

        if (tableExists(db, "task_slots")) {
            try (Cursor cursor = db.query("""
                    SELECT id, taskId, day, realEnd FROM task_slots
                    WHERE completed = 1 AND realEnd IS NOT NULL
                    """)) {
                while (cursor.moveToNext()) {
                    String workItemId = stableUuid(cursor.getString(1), "work-item");
                    String day = cursor.getString(2);
                    String realEnd = cursor.getString(3);
                    String completedAt;
                    try {
                        completedAt = LocalDate.parse(day)
                                .atTime(java.time.LocalTime.parse(realEnd)).toString();
                    } catch (RuntimeException corruptRecord) {
                        incrementWarning(summary, "CORRUPT_COMPLETIONS_SKIPPED");
                        continue;
                    }
                    ContentValues completion = new ContentValues();
                    completion.put("id", stableUuid(cursor.getString(0), "completion"));
                    completion.put("workItemId", workItemId);
                    completion.put("occurrenceKey", day);
                    completion.put("completedAt", completedAt);
                    if (workItemExists(db, workItemId)) {
                        db.insert("work_item_completions", SQLiteDatabase.CONFLICT_REPLACE, completion);
                        summary.importedCompletions++;
                    } else if (migrationCandidateExists(db, workItemId)) {
                        appendCandidateCompletion(db, workItemId, completion);
                        summary.importedCompletions++;
                    }
                }
            }
        }

        countDiscardedLegacyFacts(db, summary);
        return summary;
    }

    private static LegacyPreference legacyTimePreference(
            SupportSQLiteDatabase db, String taskId) {
        if (!tableExists(db, "task_pref_slots")) {
            return new LegacyPreference(null, false, false);
        }
        String preference = null;
        boolean hadRows = false;
        boolean invalid = false;
        try (Cursor cursor = db.query(
                "SELECT start FROM task_pref_slots WHERE taskId = ?", new Object[]{taskId})) {
            while (cursor.moveToNext()) {
                hadRows = true;
                String start = cursor.getString(0);
                if (start == null || start.length() < 2) {
                    invalid = true;
                    continue;
                }
                int hour;
                try { hour = java.time.LocalTime.parse(start).getHour(); }
                catch (RuntimeException ignored) {
                    invalid = true;
                    continue;
                }
                String current;
                if (hour >= 7 && hour < 11) current = "MORNING";
                else if (hour >= 11 && hour < 15) current = "MIDDAY";
                else if (hour >= 17 && hour < 22) current = "EVENING";
                else {
                    invalid = true;
                    continue;
                }
                if (preference != null && !preference.equals(current)) invalid = true;
                preference = current;
            }
        }
        return new LegacyPreference(invalid ? null : preference, hadRows, invalid);
    }

    private static void countDiscardedLegacyFacts(SupportSQLiteDatabase db, MigrationSummary summary) {
        countRowsMatching(db, summary, "DISCARDED_DESCRIPTIONS", "task_core",
                "description IS NOT NULL AND TRIM(description) <> ''");
        countRowsMatching(db, summary, "DISCARDED_PRIORITIES", "task_core",
                "priority IS NOT NULL");
        countRowsMatching(db, summary, "DISCARDED_START_BOUNDARIES", "task_core",
                "startDate IS NOT NULL");
        countRowsMatching(db, summary, "DISCARDED_APPOINTMENTS", "task_core",
                "schedulingType = 'TERMIN'");
        countRowsMatching(db, summary, "DISCARDED_PROGRESS_MODELS", "task_core",
                progressModelPredicate(db));
        countRowsMatching(db, summary, "DISCARDED_DURATION_RANGES", "task_core",
                columnExists(db, "task_core", "maxDuration")
                        ? "maxDuration > 0 AND maxDuration <> minDuration" : null);
        countRowsMatching(db, summary, "DISCARDED_COOLDOWNS", "task_core",
                columnExists(db, "task_core", "cooldown") ? "cooldown <> 1" : null);
        countRowsMatching(db, summary, "DISCARDED_MISS_POLICIES", "task_core",
                columnExists(db, "task_core", "closeOnMiss") ? "closeOnMiss = 1" : null);
        countRowsMatching(db, summary, "DISCARDED_COMPLETION_AGGREGATES", "task_core",
                completionAggregatePredicate(db));
        countRowsMatching(db, summary, "DISCARDED_GOAL_APPEARANCE", "task_core",
                nullableFactPredicate(db, "task_core", new String[]{"goalIcon", "goalColorHex"}));
        countRowsMatching(db, summary, "DISCARDED_BUDGET_LINKS", "task_core",
                nullableFactPredicate(db, "task_core", new String[]{
                        "budgetRequiredCents", "budgetAccountId", "budgetCategoryId"}));
        countRowsMatching(db, summary, "DISCARDED_MEAL_LINKS", "task_core",
                nullableFactPredicate(db, "task_core", new String[]{"mealType"}));
        countRowsMatching(db, summary, "DISCARDED_RELATIONSHIPS", "task_relation", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_PREREQUISITES", "task_prerequisites", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_PLANNED_SLOTS", "task_slots", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_PLANNED_MEALS", "task_planned_meals", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_SCHEDULE_CONFIG", "task_schedule_config", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_TRANSITION_STATS", "task_transition_stats", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_TASK_CATEGORIES", "task_category", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_CATEGORY_WINDOWS", "task_category_window", "1 = 1");
        countRowsMatching(db, summary, "DISCARDED_PREFERENCE_DAYS", "task_pref_slots",
                columnExists(db, "task_pref_slots", "days")
                        ? "days IS NOT NULL AND TRIM(days) <> ''" : null);
        countRowsAcross(db, summary, "DISCARDED_BUDGET_RECORDS", new String[]{
                "budget_account", "budget_category", "budget_transaction", "budget_limit",
                "budget_import", "budget_recurring_template"});
        countRowsAcross(db, summary, "DISCARDED_MEAL_RECORDS", new String[]{
                "consumption_log", "cooking_preferences", "household_member", "ingredient",
                "member_rating", "pantry_item", "meal_plan", "recipe", "recipe_ingredient",
                "shopping_list_item", "store_package", "weekly_food_target"});
    }

    private static String progressModelPredicate(SupportSQLiteDatabase db) {
        List<String> facts = new ArrayList<>();
        String[] numeric = {"progress_target", "progress_current", "progress_minPerRep",
                "progress_maxPerRep", "progress_totalProgress"};
        for (String column : numeric) {
            if (columnExists(db, "task_core", column)) facts.add(column + " <> 0");
        }
        if (columnExists(db, "task_core", "progress_unit")) {
            facts.add("(progress_unit IS NOT NULL AND TRIM(progress_unit) <> '')");
        }
        if (columnExists(db, "task_core", "progress_resetPerRep")) {
            facts.add("progress_resetPerRep = 1");
        }
        return facts.isEmpty() ? null : String.join(" OR ", facts);
    }

    private static String completionAggregatePredicate(SupportSQLiteDatabase db) {
        List<String> facts = new ArrayList<>();
        if (columnExists(db, "task_core", "history_trackedCompletions")) {
            facts.add("history_trackedCompletions <> 0");
        }
        if (columnExists(db, "task_core", "history_nrStreaks")) {
            facts.add("history_nrStreaks <> 1");
        }
        if (columnExists(db, "task_core", "history_totalDuration")) {
            facts.add("history_totalDuration <> 0");
        }
        return facts.isEmpty() ? null : String.join(" OR ", facts);
    }

    private static String nullableFactPredicate(
            SupportSQLiteDatabase db,
            String table,
            String[] columns) {
        List<String> facts = new ArrayList<>();
        for (String column : columns) {
            if (columnExists(db, table, column)) facts.add(column + " IS NOT NULL");
        }
        return facts.isEmpty() ? null : String.join(" OR ", facts);
    }

    private static void countRowsMatching(
            SupportSQLiteDatabase db,
            MigrationSummary summary,
            String warning,
            String table,
            String predicate) {
        if (predicate == null || !tableExists(db, table)) return;
        try (Cursor cursor = db.query("SELECT COUNT(*) FROM `"
                + table.replace("`", "``") + "` WHERE " + predicate)) {
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                summary.warnings.put(warning, cursor.getInt(0));
            }
        } catch (Exception ignored) { }
    }

    private static void countRowsAcross(
            SupportSQLiteDatabase db,
            MigrationSummary summary,
            String warning,
            String[] tables) {
        int total = 0;
        for (String table : tables) {
            if (!tableExists(db, table)) continue;
            try (Cursor cursor = db.query("SELECT COUNT(*) FROM `"
                    + table.replace("`", "``") + "`")) {
                if (cursor.moveToFirst()) total += cursor.getInt(0);
            } catch (Exception ignored) { }
        }
        if (total > 0) {
            try { summary.warnings.put(warning, total); }
            catch (Exception ignored) { }
        }
    }

    private static void incrementWarning(MigrationSummary summary, String warning) {
        try { summary.warnings.put(warning, summary.warnings.optInt(warning) + 1); }
        catch (Exception ignored) { }
    }

    private static void migrateManualRanks(
            SupportSQLiteDatabase db,
            Map<String, List<ManualRank>> ranks) {
        for (Map.Entry<String, List<ManualRank>> entry : ranks.entrySet()) {
            entry.getValue().sort(java.util.Comparator.comparingLong(ManualRank::rank));
            String previous = null;
            int index = 0;
            for (ManualRank rank : entry.getValue()) {
                ContentValues directive = new ContentValues();
                directive.put("id", UUID.randomUUID().toString());
                directive.put("day", entry.getKey());
                directive.put("workItemId", rank.id());
                directive.put("relation", index == 0 ? "FIRST" : "AFTER");
                putNullable(directive, "anchorWorkItemId", previous);
                directive.put("updatedAt", LocalDateTime.now().plusNanos(index).toString());
                db.insert("day_plan_directives", SQLiteDatabase.CONFLICT_REPLACE, directive);
                previous = rank.id();
                index++;
            }
        }
    }

    /**
     * Version 31's "Später" action appended selected items behind automatic work. A LAST
     * directive is the closest safe equivalent: it keeps the postponement inside the item's
     * urgency band, so a migrated preference can never hide a newly urgent task.
     */
    private static void migratePostponedRanks(
            SupportSQLiteDatabase db,
            Map<String, List<ManualRank>> ranks) {
        for (Map.Entry<String, List<ManualRank>> entry : ranks.entrySet()) {
            entry.getValue().sort(java.util.Comparator.comparingLong(ManualRank::rank));
            int index = 0;
            for (ManualRank rank : entry.getValue()) {
                ContentValues directive = new ContentValues();
                directive.put("id", UUID.randomUUID().toString());
                directive.put("day", entry.getKey());
                directive.put("workItemId", rank.id());
                directive.put("relation", "LAST");
                directive.putNull("anchorWorkItemId");
                directive.put("updatedAt", LocalDateTime.now().plusNanos(index).toString());
                db.insert("day_plan_directives", SQLiteDatabase.CONFLICT_REPLACE, directive);
                index++;
            }
        }
    }

    private static void insertReport(
            SupportSQLiteDatabase db,
            int sourceVersion,
            MigrationSummary summary) {
        ContentValues report = new ContentValues();
        report.put("sourceVersion", sourceVersion);
        report.put("completedAt", LocalDateTime.now().toString());
        report.put("importedItems", summary.importedItems);
        report.put("importedCompletions", summary.importedCompletions);
        report.put("candidateItems", summary.candidateItems);
        report.put("warningsJson", summary.warnings.toString());
        report.put("acknowledged", false);
        db.insert("migration_reports", SQLiteDatabase.CONFLICT_ABORT, report);
    }

    private static void dropLegacyTables(SupportSQLiteDatabase db) {
        Set<String> current = Set.of(
                "work_items", "steps", "step_days", "step_completions",
                "work_item_completions", "day_plan_directives", "planned_slots",
                "planning_conflicts", "undo_journal", "migration_reports",
                "migration_candidates", "room_master_table", "android_metadata");
        List<String> legacy = new ArrayList<>();
        try (Cursor cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (cursor.moveToNext()) {
                String table = cursor.getString(0);
                if (!table.startsWith("sqlite_") && !current.contains(table)) legacy.add(table);
            }
        }
        db.execSQL("PRAGMA defer_foreign_keys=ON");
        for (String table : legacy) {
            db.execSQL("DROP TABLE IF EXISTS `" + table.replace("`", "``") + "`");
        }
    }

    private static boolean workItemExists(SupportSQLiteDatabase db, String id) {
        try (Cursor cursor = db.query("SELECT 1 FROM work_items WHERE id = ?", new Object[]{id})) {
            return cursor.moveToFirst();
        }
    }

    private static boolean migrationCandidateExists(SupportSQLiteDatabase db, String id) {
        try (Cursor cursor = db.query(
                "SELECT 1 FROM migration_candidates WHERE id = ?", new Object[]{id})) {
            return cursor.moveToFirst();
        }
    }

    private static void appendCandidateCompletion(
            SupportSQLiteDatabase db, String candidateId, ContentValues completion) {
        try (Cursor cursor = db.query(
                "SELECT legacyPayloadJson FROM migration_candidates WHERE id = ?",
                new Object[]{candidateId})) {
            if (!cursor.moveToFirst()) return;
            JSONObject payload = new JSONObject(cursor.getString(0));
            JSONArray evidence = payload.optJSONArray("completionEvidence");
            if (evidence == null) evidence = new JSONArray();
            evidence.put(new JSONObject()
                    .put("id", completion.getAsString("id"))
                    .put("occurrenceKey", completion.getAsString("occurrenceKey"))
                    .put("completedAt", completion.getAsString("completedAt")));
            payload.put("completionEvidence", evidence);
            db.execSQL("UPDATE migration_candidates SET legacyPayloadJson = ? WHERE id = ?",
                    new Object[]{payload.toString(), candidateId});
        } catch (Exception error) {
            throw new IllegalStateException("Abschlussbeleg konnte nicht quarantänisiert werden", error);
        }
    }

    private static boolean tableExists(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", new Object[]{table})) {
            return cursor.moveToFirst();
        }
    }

    private static boolean columnExists(SupportSQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.query("PRAGMA table_info(" + table + ")")) {
            int index = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) if (column.equals(cursor.getString(index))) return true;
            return false;
        }
    }

    private static String text(Cursor cursor, String column, String fallback) {
        String value = nullableText(cursor, column);
        return value == null ? fallback : value;
    }

    private static String nullableText(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static int integer(Cursor cursor, String column, int fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getInt(index);
    }

    private static long longValue(Cursor cursor, String column, long fallback) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? fallback : cursor.getLong(index);
    }

    private static String stableUuid(String value, String namespace) {
        String candidate = value == null ? "" : value.trim();
        try { return UUID.fromString(candidate).toString(); }
        catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes((namespace + ":" + candidate)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        }
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int boundedDuration(int value) {
        return Math.max(5, Math.min(480, value));
    }

    private static int legacyDuration(Cursor cursor, String schedulingType) {
        int duration = integer(cursor, "minDuration", 30);
        if (!"TERMIN".equals(schedulingType)) return boundedDuration(duration);
        int fixed = integer(cursor, "fixedDuration", 0);
        if (fixed > 0) return boundedDuration(fixed);
        String start = nullableText(cursor, "fixedStart");
        String end = nullableText(cursor, "fixedEnd");
        if (start != null && end != null) {
            try {
                long minutes = java.time.Duration.between(
                        java.time.LocalTime.parse(start), java.time.LocalTime.parse(end)).toMinutes();
                if (minutes > 0 && minutes <= Integer.MAX_VALUE) duration = (int) minutes;
            } catch (RuntimeException ignored) { }
        }
        return boundedDuration(duration);
    }

    private static int exactCadenceDays(String unit, int perPeriod) {
        if (perPeriod < 1) return 0;
        long days = switch (unit == null ? "" : unit) {
            case "DAY" -> perPeriod;
            case "WEEK" -> (long) perPeriod * 7;
            default -> 0;
        };
        return days > Integer.MAX_VALUE ? 0 : (int) days;
    }

    private static boolean validDate(String value) {
        if (value == null) return false;
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean validDateTime(String value) {
        if (value == null) return false;
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean validPreference(String value) {
        return value == null || "MORNING".equals(value)
                || "MIDDAY".equals(value) || "EVENING".equals(value);
    }

    private static String legacyCreatedAt(Cursor cursor) {
        String value = text(cursor, "created", LocalDate.now().toString());
        try {
            if (value.contains("T")) return LocalDateTime.parse(value).toString();
            return LocalDate.parse(value).atStartOfDay().toString();
        } catch (RuntimeException error) {
            return LocalDate.now().atStartOfDay().toString();
        }
    }

    private static boolean validLegacyCreated(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            if (value.contains("T")) LocalDateTime.parse(value);
            else LocalDate.parse(value);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static String fixedDate(Cursor cursor) {
        String value = nullableText(cursor, "fixedDate");
        return validDate(value) ? value : null;
    }

    private static String suggestedLegacyDueDate(
            String periodStart, String createdAt, String fixedDate) {
        if (periodStart != null && validDate(periodStart)) return periodStart;
        if (fixedDate != null && validDate(fixedDate)) return fixedDate;
        try { return LocalDateTime.parse(createdAt).toLocalDate().toString(); }
        catch (RuntimeException error) { return LocalDate.now().toString(); }
    }

    private static String appointmentDeadlineAt(Cursor cursor) {
        String day = fixedDate(cursor);
        if (day == null) {
            String deadline = nullableText(cursor, "deadline");
            return deadline == null || !validDate(deadline)
                    ? null : deadline + "T23:59:00";
        }
        String end = nullableText(cursor, "fixedEnd");
        if (end != null) {
            try { return LocalDate.parse(day).atTime(java.time.LocalTime.parse(end)).toString(); }
            catch (RuntimeException ignored) { }
        }
        String start = nullableText(cursor, "fixedStart");
        if (start != null) {
            try {
                return LocalDate.parse(day).atTime(java.time.LocalTime.parse(start))
                        .plusMinutes(legacyDuration(cursor, "TERMIN")).toString();
            } catch (RuntimeException ignored) { }
        }
        return LocalDate.parse(day).atTime(23, 59).toString();
    }

    private record ManualRank(String id, long rank) { }

    private record LegacyPreference(String value, boolean hadRows, boolean invalid) {
        boolean unmapped() { return hadRows && (invalid || value == null); }
    }

    private static final class MigrationSummary {
        int importedItems;
        int importedCompletions;
        int candidateItems;
        final JSONObject warnings = new JSONObject();
    }
}
