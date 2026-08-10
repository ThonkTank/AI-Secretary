package com.autosecretary.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.autosecretary.core.Completion;
import com.autosecretary.core.Obligation;
import com.autosecretary.core.RoutineCadence;
import com.autosecretary.core.RoutineStep;
import com.autosecretary.core.TimePreference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The app's complete persistence surface: obligations, completion evidence and one legacy import. */
public final class TaskStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "autosecretary.db";
    private static final int DATABASE_VERSION = 32;

    public TaskStore(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCoreTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createCoreTables(db);
        if (oldVersion < 32) {
            addColumnIfMissing(db, "obligations", "preferredTime", "TEXT");
            addColumnIfMissing(db, "obligations", "manualOrderOn", "TEXT");
            addColumnIfMissing(db, "obligations", "manualOrderRank", "INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < DATABASE_VERSION) {
            migrateLegacyTasksOnce(db);
        }
    }

    private void createCoreTables(SQLiteDatabase db) {
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS obligations (
                    id TEXT PRIMARY KEY NOT NULL,
                    kind TEXT NOT NULL,
                    title TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    deadlineAt TEXT,
                    cadenceDays INTEGER NOT NULL,
                    nextDueDate TEXT,
                    preferredTime TEXT,
                    stepsJson TEXT,
                    createdAt TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    currentStreak INTEGER NOT NULL,
                    bestStreak INTEGER NOT NULL,
                    totalCompletions INTEGER NOT NULL,
                    manualOrderOn TEXT,
                    manualOrderRank INTEGER NOT NULL
                )
                """);
        db.execSQL("""
                CREATE TABLE IF NOT EXISTS completions (
                    id TEXT PRIMARY KEY NOT NULL,
                    obligationId TEXT NOT NULL,
                    completedAt TEXT NOT NULL
                )
                """);
        db.execSQL("CREATE INDEX IF NOT EXISTS completions_obligation ON completions(obligationId)");
        db.execSQL("CREATE INDEX IF NOT EXISTS completions_time ON completions(completedAt)");
    }

    /** Imports only facts that belong to the new core; old feature tables remain untouched. */
    private void migrateLegacyTasksOnce(SQLiteDatabase db) {
        if (!tableExists(db, "task_core") || count(db, "obligations") > 0) {
            return;
        }
        String id = legacyColumn(db, "id", "lower(hex(randomblob(16)))");
        String title = legacyColumn(db, "title", "'Aufgabe'");
        String duration = legacyColumn(db, "minDuration", "30");
        String deadline = columnExists(db, "task_core", "deadline")
                ? "CASE WHEN deadline IS NULL THEN NULL ELSE deadline || 'T23:59:00' END"
                : "NULL";
        String repetition = legacyColumn(db, "repetition_reps", "0");
        String unit = legacyColumn(db, "repetition_periodUnit", "'DAY'");
        String due = legacyColumn(db, "repetition_periodStart", legacyColumn(db, "created", "date('now')"));
        String created = legacyColumn(db, "created", "date('now')");
        String completed = legacyColumn(db, "completed", "0");
        String streak = legacyColumn(db, "history_currentStreak", "0");
        String total = legacyColumn(db, "history_completions", "0");

        db.execSQL(String.format(Locale.ROOT, """
                INSERT OR IGNORE INTO obligations (
                    id, kind, title, durationMinutes, deadlineAt, cadenceDays, nextDueDate,
                    preferredTime, stepsJson, createdAt, completed, currentStreak, bestStreak,
                    totalCompletions, manualOrderOn, manualOrderRank)
                SELECT %s,
                    CASE WHEN %s > 0 THEN 'ROUTINE' ELSE 'TASK' END,
                    COALESCE(%s, 'Aufgabe'), MAX(5, COALESCE(%s, 30)), %s,
                    CASE %s WHEN 'WEEK' THEN 7 WHEN 'MONTH' THEN 30 ELSE 1 END,
                    CASE WHEN %s > 0 THEN COALESCE(%s, date('now')) ELSE NULL END,
                    NULL, NULL, COALESCE(%s, date('now')) || 'T00:00:00', %s,
                    COALESCE(%s, 0), COALESCE(%s, 0), COALESCE(%s, 0), NULL, 0
                FROM task_core
                """,
                id, repetition, title, duration, deadline, unit, repetition, due,
                created, completed, streak, streak, total));
    }

    public synchronized List<Obligation> readAll() {
        List<Obligation> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "obligations", null, null, null, null, null, "createdAt, title")) {
            while (cursor.moveToNext()) {
                result.add(readObligation(cursor));
            }
        }
        return result;
    }

    public synchronized Obligation read(String id) {
        try (Cursor cursor = getReadableDatabase().query(
                "obligations", null, "id = ?", new String[]{id}, null, null, null)) {
            return cursor.moveToFirst() ? readObligation(cursor) : null;
        }
    }

    public synchronized List<Completion> readRecentCompletions(int limit) {
        List<Completion> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "completions", new String[]{"id", "obligationId", "completedAt"},
                null, null, null, null, "completedAt DESC", Integer.toString(limit))) {
            while (cursor.moveToNext()) {
                result.add(new Completion(
                        cursor.getString(0), cursor.getString(1), LocalDateTime.parse(cursor.getString(2))));
            }
        }
        return result;
    }

    public synchronized void save(Obligation obligation) {
        getWritableDatabase().insertWithOnConflict(
                "obligations", null, values(obligation), SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void delete(String id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("completions", "obligationId = ?", new String[]{id});
            db.delete("obligations", "id = ?", new String[]{id});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized Obligation complete(String id, LocalDateTime completedAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Obligation obligation = read(id);
            if (obligation == null || !obligation.isOpenOn(completedAt.toLocalDate())) {
                return null;
            }
            completeInTransaction(db, obligation, completedAt);
            db.setTransactionSuccessful();
            return obligation;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized Obligation setStepCompleted(
            String obligationId,
            String stepId,
            boolean completed,
            LocalDateTime changedAt) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Obligation obligation = read(obligationId);
            LocalDate day = changedAt.toLocalDate();
            if (obligation == null || !obligation.isRoutine() || !obligation.isOpenOn(day)) {
                return null;
            }
            LocalDate occurrence = obligation.occurrenceDate(day);
            RoutineStep target = null;
            for (RoutineStep step : obligation.activeStepsFor(day)) {
                if (step.id.equals(stepId)) {
                    target = step;
                    break;
                }
            }
            if (target == null) return null;

            target.setCompletedFor(occurrence, completed, changedAt);
            if (completed && obligation.allActiveStepsCompleted(day)) {
                completeInTransaction(db, obligation, changedAt);
            } else {
                db.insertWithOnConflict(
                        "obligations", null, values(obligation), SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
            return obligation;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void saveManualOrder(LocalDate day, List<String> orderedIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues clear = new ContentValues();
            clear.putNull("manualOrderOn");
            clear.put("manualOrderRank", 0);
            db.update(
                    "obligations", clear, "manualOrderOn = ?", new String[]{day.toString()});

            long rank = 1;
            for (String id : orderedIds) {
                ContentValues order = new ContentValues();
                order.put("manualOrderOn", day.toString());
                order.put("manualOrderRank", rank++);
                db.update("obligations", order, "id = ?", new String[]{id});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void completeInTransaction(SQLiteDatabase db, Obligation obligation, LocalDateTime completedAt) {
        RoutineCadence.complete(obligation, completedAt.toLocalDate());
        obligation.manualOrderOn = null;
        obligation.manualOrderRank = 0;
        db.insertWithOnConflict(
                "obligations", null, values(obligation), SQLiteDatabase.CONFLICT_REPLACE);

        ContentValues completion = new ContentValues();
        completion.put("id", UUID.randomUUID().toString());
        completion.put("obligationId", obligation.id);
        completion.put("completedAt", completedAt.toString());
        db.insertOrThrow("completions", null, completion);
    }

    private ContentValues values(Obligation item) {
        ContentValues values = new ContentValues();
        values.put("id", item.id);
        values.put("kind", item.kind.name());
        values.put("title", item.title.trim());
        values.put("durationMinutes", Math.max(5, item.durationMinutes));
        putNullable(values, "deadlineAt", item.deadlineAt);
        values.put("cadenceDays", item.isRoutine() ? Math.max(1, item.cadenceDays) : 0);
        putNullable(values, "nextDueDate", item.isRoutine() ? item.nextDueDate : null);
        putNullable(values, "preferredTime", item.timePreference);
        values.put("stepsJson", encodeSteps(item.steps));
        values.put("createdAt", item.createdAt.toString());
        values.put("completed", item.completed ? 1 : 0);
        values.put("currentStreak", item.currentStreak);
        values.put("bestStreak", item.bestStreak);
        values.put("totalCompletions", item.totalCompletions);
        putNullable(values, "manualOrderOn", item.manualOrderOn);
        values.put("manualOrderRank", item.manualOrderRank);
        return values;
    }

    private Obligation readObligation(Cursor cursor) {
        Obligation item = new Obligation();
        item.id = text(cursor, "id");
        item.kind = Obligation.Kind.valueOf(text(cursor, "kind"));
        item.title = text(cursor, "title");
        item.durationMinutes = integer(cursor, "durationMinutes");
        item.deadlineAt = parseDateTime(nullableText(cursor, "deadlineAt"));
        item.cadenceDays = integer(cursor, "cadenceDays");
        item.nextDueDate = parseDate(nullableText(cursor, "nextDueDate"));
        item.timePreference = parseTimePreference(nullableText(cursor, "preferredTime"));
        item.steps = decodeSteps(nullableText(cursor, "stepsJson"), item.id);
        item.createdAt = LocalDateTime.parse(text(cursor, "createdAt"));
        item.completed = integer(cursor, "completed") != 0;
        item.currentStreak = integer(cursor, "currentStreak");
        item.bestStreak = integer(cursor, "bestStreak");
        item.totalCompletions = integer(cursor, "totalCompletions");
        item.manualOrderOn = parseDate(nullableText(cursor, "manualOrderOn"));
        item.manualOrderRank = cursor.getLong(cursor.getColumnIndexOrThrow("manualOrderRank"));
        return item;
    }

    private String encodeSteps(List<RoutineStep> steps) {
        if (steps == null || steps.isEmpty()) return null;
        JSONArray encoded = new JSONArray();
        for (RoutineStep step : steps) {
            if (step == null || step.title.trim().isEmpty()) continue;
            JSONObject value = new JSONObject();
            JSONArray days = new JSONArray();
            for (DayOfWeek day : DayOfWeek.values()) {
                if (step.days.contains(day)) days.put(day.name());
            }
            try {
                value.put("id", step.id);
                value.put("title", step.title);
                value.put("days", days);
                value.put("completedFor", step.completedFor == null
                        ? JSONObject.NULL : step.completedFor.toString());
                value.put("completedAt", step.completedAt == null
                        ? JSONObject.NULL : step.completedAt.toString());
            } catch (JSONException error) {
                throw new IllegalStateException("Routineschritte konnten nicht gespeichert werden", error);
            }
            encoded.put(value);
        }
        return encoded.length() == 0 ? null : encoded.toString();
    }

    private List<RoutineStep> decodeSteps(String json, String obligationId) {
        List<RoutineStep> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                JSONObject value = array.getJSONObject(index);
                EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                JSONArray encodedDays = value.optJSONArray("days");
                if (encodedDays != null) {
                    for (int day = 0; day < encodedDays.length(); day++) {
                        days.add(DayOfWeek.valueOf(encodedDays.getString(day)));
                    }
                }
                String title = value.optString("title");
                String id = value.optString("id");
                if (id.isBlank()) {
                    id = UUID.nameUUIDFromBytes(
                            (obligationId + ":" + index + ":" + title)
                                    .getBytes(StandardCharsets.UTF_8)).toString();
                }
                result.add(new RoutineStep(
                        id,
                        title,
                        days,
                        parseDate(value.isNull("completedFor")
                                ? null : value.optString("completedFor", null)),
                        parseDateTime(value.isNull("completedAt")
                                ? null : value.optString("completedAt", null))));
            }
        } catch (JSONException | IllegalArgumentException error) {
            throw new IllegalStateException("Routineschritte konnten nicht gelesen werden", error);
        }
        return result;
    }

    private static void putNullable(ContentValues values, String key, Object value) {
        if (value == null) values.putNull(key); else values.put(key, value.toString());
    }

    private static String text(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static String nullableText(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static int integer(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private static LocalDate parseDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private static LocalDateTime parseDateTime(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }

    private static TimePreference parseTimePreference(String value) {
        return value == null ? null : TimePreference.valueOf(value);
    }

    private static void addColumnIfMissing(
            SQLiteDatabase db,
            String table,
            String column,
            String declaration) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + declaration);
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", new String[]{table})) {
            return cursor.moveToFirst();
        }
    }

    private static boolean columnExists(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int name = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) if (column.equals(cursor.getString(name))) return true;
            return false;
        }
    }

    private static String legacyColumn(SQLiteDatabase db, String column, String fallback) {
        return columnExists(db, "task_core", column) ? column : fallback;
    }

    private static long count(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }
}
