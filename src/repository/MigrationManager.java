package repository;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

import data.Constants;

/**
 * Verwaltet Datenbank-Migrationen mit automatischen Backups.
 *
 * Verantwortlichkeiten:
 * - Pre-Migration-Backups erstellen
 * - Schema-Migrationen ausfuehren
 * - Migrations-History tracken
 * - Backup-Rotation verwalten
 * - Wiederherstellung aus Backups ermoeglichen
 */
public class MigrationManager {

    private static final String TAG = "MigrationManager";
    private final Context context;

    public MigrationManager(Context context) {
        this.context = context;
    }

    // ================================================================
    // BACKUP OPERATIONS
    // ================================================================

    /**
     * Erstellt ein Backup der aktuellen Datenbank vor der Migration.
     * Gibt den Backup-Dateipfad zurueck, oder null bei Fehler.
     */
    public File createBackup(int fromVersion) {
        File dbFile = context.getDatabasePath(Constants.DB_NAME);
        if (!dbFile.exists()) {
            Log.w(TAG, "Database file does not exist, skipping backup");
            return null;
        }

        File backupDir = new File(context.getFilesDir(), Constants.BACKUP_DIR);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String backupName = String.format("backup_v%d_%s.db", fromVersion, timestamp);
        File backupFile = new File(backupDir, backupName);

        try {
            copyFile(dbFile, backupFile);
            Log.i(TAG, "Backup created: " + backupFile.getAbsolutePath());
            rotateBackups();
            return backupFile;
        } catch (IOException e) {
            Log.e(TAG, "Backup failed", e);
            return null;
        }
    }

    /**
     * Stellt die Datenbank aus einem Backup wieder her.
     */
    public boolean restoreFromBackup(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            Log.e(TAG, "Backup file does not exist");
            return false;
        }

        File dbFile = context.getDatabasePath(Constants.DB_NAME);
        try {
            copyFile(backupFile, dbFile);
            Log.i(TAG, "Restored from backup: " + backupFile.getName());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Restore failed", e);
            return false;
        }
    }

    /**
     * Listet verfuegbare Backup-Dateien auf, neueste zuerst.
     */
    public File[] listBackups() {
        File backupDir = new File(context.getFilesDir(), Constants.BACKUP_DIR);
        if (!backupDir.exists()) {
            return new File[0];
        }

        File[] backups = backupDir.listFiles((dir, name) ->
            name.startsWith("backup_") && name.endsWith(".db"));
        if (backups == null) {
            return new File[0];
        }

        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        return backups;
    }

    /**
     * Behaelt nur die neuesten N Backups.
     */
    private void rotateBackups() {
        File[] backups = listBackups();
        if (backups.length > Constants.MAX_BACKUPS) {
            for (int i = Constants.MAX_BACKUPS; i < backups.length; i++) {
                if (backups[i].delete()) {
                    Log.i(TAG, "Deleted old backup: " + backups[i].getName());
                }
            }
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream inStream = new FileInputStream(src);
             FileOutputStream outStream = new FileOutputStream(dst);
             FileChannel inChannel = inStream.getChannel();
             FileChannel outChannel = outStream.getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    // ================================================================
    // MIGRATION OPERATIONS
    // ================================================================

    /**
     * Fuehrt alle notwendigen Migrationen von oldVersion zu newVersion aus.
     * Wird von SQLrepo.onUpgrade() aufgerufen.
     */
    public void migrate(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Migrating from v" + oldVersion + " to v" + newVersion);

        // Migrations-Meta-Tabelle erstellen falls nicht vorhanden
        createMigrationsTable(db);

        // Jede Migration einzeln ausfuehren
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            runMigration(db, version);
        }
    }

    private void createMigrationsTable(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS _migrations (" +
            "version INTEGER PRIMARY KEY," +
            "applied_at TEXT NOT NULL," +
            "description TEXT" +
            ")"
        );
    }

    private void runMigration(SQLiteDatabase db, int toVersion) {
        Log.i(TAG, "Running migration to v" + toVersion);

        switch (toVersion) {
            // v20-v30: Schema-Konsolidierung - alle neuen Spalten hinzufuegen
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
                // Alle Zwischenversionen fallen durch zur konsolidierten Migration
                if (toVersion == 30) {
                    migrateV30_SchemaConsolidation(db);
                }
                break;

            case 31:
                migrateV31_ProductionCleanup(db);
                break;

            case 32:
                migrateV32_FreeFormSchedule(db);
                break;

            case 33:
                migrateV33_PrefSchedule(db);
                break;

            case 34:
                migrateV34_MealTypeConsolidation(db);
                break;

            default:
                Log.w(TAG, "No migration defined for v" + toVersion);
        }

        // Migration dokumentieren
        db.execSQL(
            "INSERT OR REPLACE INTO _migrations (version, applied_at, description) VALUES (?, ?, ?)",
            new Object[]{toVersion, LocalDateTime.now().toString(), getMigrationDescription(toVersion)}
        );
    }

    private String getMigrationDescription(int version) {
        switch (version) {
            case 30: return "Schema consolidation - add new columns for v1.0";
            case 31: return "Production cleanup - remove test data";
            case 32: return "Free-form meal schedule - remove UNIQUE, add duration";
            case 33: return "Per-weekday preferred time slots";
            case 34: return "Meal-type on items, item-id on meal_plans";
            default: return "Migration v" + version;
        }
    }

    // ================================================================
    // MIGRATION DEFINITIONS
    // ================================================================

    /**
     * v30: Schema-Konsolidierung - fuegt alle neuen Spalten fuer v1.0 hinzu.
     * Sichere Migration: Prueft ob Spalten bereits existieren bevor sie hinzugefuegt werden.
     */
    private void migrateV30_SchemaConsolidation(SQLiteDatabase db) {
        Log.i(TAG, "v30: Running schema consolidation for v1.0");

        // ---- ITEMS TABLE: Neue Spalten ----

        // Feste Termine
        addColumnIfNotExists(db, "items", "fixed_date", "TEXT");
        addColumnIfNotExists(db, "items", "fixed_time", "TEXT");

        // Unified Chaining System (ersetzt required_predecessor)
        addColumnIfNotExists(db, "items", "predecessor", "INTEGER");
        addColumnIfNotExists(db, "items", "predecessor_delay", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "last_completion_time", "TEXT");

        // Budget-Integration
        addColumnIfNotExists(db, "items", "budget_requirement_cents", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "budget_account_id", "INTEGER");
        addColumnIfNotExists(db, "items", "budget_category_id", "INTEGER");

        // Meal-Task-Verknuepfung
        addColumnIfNotExists(db, "items", "meal_plan_id", "INTEGER");

        // Progress-Tracking (falls noch nicht vorhanden)
        addColumnIfNotExists(db, "items", "progress_current", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "progress_target", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "progress_unit", "TEXT");
        addColumnIfNotExists(db, "items", "progress_per_rep", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "progress_last_period", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "time_per_progress_unit", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "progress_timing_count", "INTEGER DEFAULT 0");

        // Deadline (falls noch nicht vorhanden)
        addColumnIfNotExists(db, "items", "deadline", "TEXT");

        // Goal-Darstellung
        addColumnIfNotExists(db, "items", "goal_icon", "TEXT");
        addColumnIfNotExists(db, "items", "goal_color", "TEXT");

        // Flexible Duration-Einheiten
        addColumnIfNotExists(db, "items", "min_duration_value", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "min_duration_unit", "TEXT DEFAULT 'MINUTES'");
        addColumnIfNotExists(db, "items", "max_duration_value", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "items", "max_duration_unit", "TEXT DEFAULT 'MINUTES'");

        // ---- TIME_SLOTS TABLE: Neue Spalten ----
        addColumnIfNotExists(db, "time_slots", "progress_delta", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "time_slots", "previous_completed_item_id", "INTEGER");
        addColumnIfNotExists(db, "time_slots", "chain_id", "INTEGER");

        // ---- Daten-Migration: required_predecessor -> predecessor ----
        migrateRequiredPredecessor(db);

        // ---- Performance-Indizes erstellen ----
        createPerformanceIndexes(db);

        Log.i(TAG, "v30: Schema consolidation completed");
    }

    /**
     * Erstellt Performance-Indizes fuer haeufige Queries.
     * Wird sowohl in Migrationen als auch in onCreate aufgerufen.
     */
    private void createPerformanceIndexes(SQLiteDatabase db) {
        String[] indexes = {
            // Index fuer Budget-Tasks (Scheduling-Filter)
            "CREATE INDEX IF NOT EXISTS idx_items_budget " +
                "ON items(budget_requirement_cents) WHERE budget_requirement_cents > 0",

            // Index fuer feste Termine (Scheduling-Filter)
            "CREATE INDEX IF NOT EXISTS idx_items_fixed " +
                "ON items(fixed_date) WHERE fixed_date IS NOT NULL",

            // Index fuer Meal-Tasks (Join auf meal_plan_id)
            "CREATE INDEX IF NOT EXISTS idx_items_meal " +
                "ON items(meal_plan_id) WHERE meal_plan_id IS NOT NULL",

            // Index fuer offene Items (haeufigster Query)
            "CREATE INDEX IF NOT EXISTS idx_items_open " +
                "ON items(is_completed, type) WHERE is_completed = 0"
        };

        for (String sql : indexes) {
            try {
                db.execSQL(sql);
            } catch (Exception e) {
                Log.w(TAG, "Could not create index: " + e.getMessage());
            }
        }
    }

    /**
     * Fuegt eine Spalte zur Tabelle hinzu, falls sie nicht existiert.
     * SQLite unterstuetzt kein "ADD COLUMN IF NOT EXISTS", daher PRAGMA-Abfrage.
     */
    private void addColumnIfNotExists(SQLiteDatabase db, String table, String column, String definition) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        boolean exists = false;

        while (cursor.moveToNext()) {
            String columnName = cursor.getString(cursor.getColumnIndex("name"));
            if (column.equals(columnName)) {
                exists = true;
                break;
            }
        }
        cursor.close();

        if (!exists) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                Log.d(TAG, "Added column " + table + "." + column);
            } catch (Exception e) {
                Log.w(TAG, "Could not add column " + table + "." + column + ": " + e.getMessage());
            }
        }
    }

    /**
     * Migriert Daten von required_predecessor zu predecessor.
     * Nur wenn required_predecessor existiert und predecessor noch leer ist.
     */
    private void migrateRequiredPredecessor(SQLiteDatabase db) {
        // Pruefen ob required_predecessor existiert
        Cursor cursor = db.rawQuery("PRAGMA table_info(items)", null);
        boolean hasRequiredPredecessor = false;
        boolean hasPredecessor = false;

        while (cursor.moveToNext()) {
            String columnName = cursor.getString(cursor.getColumnIndex("name"));
            if ("required_predecessor".equals(columnName)) hasRequiredPredecessor = true;
            if ("predecessor".equals(columnName)) hasPredecessor = true;
        }
        cursor.close();

        // Daten migrieren wenn beide Spalten existieren
        if (hasRequiredPredecessor && hasPredecessor) {
            try {
                db.execSQL(
                    "UPDATE items SET predecessor = required_predecessor " +
                    "WHERE required_predecessor IS NOT NULL AND predecessor IS NULL"
                );
                Log.i(TAG, "Migrated required_predecessor -> predecessor");
            } catch (Exception e) {
                Log.w(TAG, "Could not migrate required_predecessor: " + e.getMessage());
            }
        }
    }

    /**
     * v31: Production Cleanup - entfernt alle Testdaten, behaelt Referenzdaten.
     */
    private void migrateV31_ProductionCleanup(SQLiteDatabase db) {
        Log.i(TAG, "v31: Cleaning up test data for production release");

        // Nutzerdaten-Tabellen leeren (Schema bleibt)
        String[] dataTables = {
            "items", "todos", "time_slots",
            "accounts", "transactions", "budget_limits", "imports",
            "household_members", "cooking_preferences", "recipe_ratings",
            "recipes", "meal_plans", "meal_schedules",
            "shopping_list_items", "pantry_items", "consumption_logs",
            "weekly_food_targets"
        };

        for (String table : dataTables) {
            try {
                db.execSQL("DELETE FROM " + table);
                // Auto-Increment Counter zuruecksetzen
                db.execSQL("DELETE FROM sqlite_sequence WHERE name='" + table + "'");
                Log.d(TAG, "Cleared table: " + table);
            } catch (Exception e) {
                Log.w(TAG, "Could not clear table " + table + ": " + e.getMessage());
            }
        }

        // Referenzdaten behalten: categories, ingredients, config_schedules
        // Default-Schedule seeden falls leer
        seedDefaultSchedules(db);

        Log.i(TAG, "v31: Production cleanup completed");
    }

    private void seedDefaultSchedules(SQLiteDatabase db) {
        // Pruefen ob Schedules existieren
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM config_schedules", null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        if (count == 0) {
            Log.i(TAG, "Seeding default schedules");
            // Default-Schedule (6:00-22:00 Werktags, 8:00-22:00/20:00 Wochenende)
            String[][] schedules = {
                {"MONDAY", "06:00", "22:00"},
                {"TUESDAY", "06:00", "22:00"},
                {"WEDNESDAY", "06:00", "22:00"},
                {"THURSDAY", "06:00", "22:00"},
                {"FRIDAY", "06:00", "22:00"},
                {"SATURDAY", "08:00", "22:00"},
                {"SUNDAY", "08:00", "20:00"}
            };
            for (String[] s : schedules) {
                ContentValues cv = new ContentValues();
                cv.put("day_of_week", s[0]);
                cv.put("start_time", s[1]);
                cv.put("end_time", s[2]);
                db.insert("config_schedules", null, cv);
            }
        }
    }

    /**
     * v32: Free-form Meal Schedule - UNIQUE Constraint entfernen, duration_minutes hinzufuegen.
     * Tabelle muss neu erstellt werden (SQLite <3.35 kann kein DROP COLUMN/CONSTRAINT).
     */
    private void migrateV32_FreeFormSchedule(SQLiteDatabase db) {
        Log.i(TAG, "v32: Migrating to free-form meal schedule");

        db.beginTransaction();
        try {
            // 1. Neue Tabelle ohne UNIQUE Constraint erstellen
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS meal_schedules_new ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "day_of_week TEXT NOT NULL,"
                + "meal_type TEXT NOT NULL,"
                + "scheduled_time TEXT,"
                + "duration_minutes INTEGER DEFAULT " + 30
                + ")"
            );

            // 2. Nur aktivierte Eintraege migrieren (disabled = geloescht im neuen Modell)
            db.execSQL(
                "INSERT INTO meal_schedules_new (id, day_of_week, meal_type, scheduled_time, duration_minutes) "
                + "SELECT id, day_of_week, meal_type, scheduled_time, " + 30 + " "
                + "FROM meal_schedules WHERE is_enabled = 1"
            );

            // 3. Alte Tabelle loeschen
            db.execSQL("DROP TABLE meal_schedules");

            // 4. Neue Tabelle umbenennen
            db.execSQL("ALTER TABLE meal_schedules_new RENAME TO meal_schedules");

            db.setTransactionSuccessful();
            Log.i(TAG, "v32: Free-form meal schedule migration completed");
        } finally {
            db.endTransaction();
        }
    }

    /**
     * v33: Per-weekday preferred time slots.
     * Fuegt pref_schedule Spalte hinzu (keine Datenmigration noetig, da keine Nutzerdaten existieren).
     */
    private void migrateV33_PrefSchedule(SQLiteDatabase db) {
        Log.i(TAG, "v33: Adding pref_schedule column");
        addColumnIfNotExists(db, "items", "pref_schedule", "TEXT");
    }

    /**
     * v34: Meal-Type auf Items + Item-ID auf MealPlans.
     * Konsolidiert meal_schedules zu recurring TrackedItems.
     * Markiert bestehende Einmal-Meal-Items als completed.
     */
    private void migrateV34_MealTypeConsolidation(SQLiteDatabase db) {
        Log.i(TAG, "v34: Adding meal_type to items, item_id to meal_plans");
        addColumnIfNotExists(db, "items", "meal_type", "TEXT");
        addColumnIfNotExists(db, "meal_plans", "item_id", "INTEGER");

        // meal_schedules → recurring TrackedItems konvertieren
        convertMealSchedulesToItems(db);

        // Bestehende Einmal-Meal-Items (mealPlanId != null, nicht erledigt) als completed markieren
        int updated = db.update("items",
            createCV("is_completed", 1),
            "meal_plan_id IS NOT NULL AND is_completed = 0", null);
        if (updated > 0) Log.i(TAG, "v34: Marked " + updated + " one-off meal items as completed");

        // Index fuer Reverse-Lookup: MealPlan → TrackedItem
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_meal_plans_item " +
            "ON meal_plans(item_id) WHERE item_id IS NOT NULL");
    }

    /**
     * Liest alle meal_schedules, gruppiert nach mealType, erstellt recurring TrackedItems.
     * Bei Tag-Duplikaten (z.B. 2x Lunch am Montag) werden separate Items erstellt.
     */
    private void convertMealSchedulesToItems(SQLiteDatabase db) {
        // Alle meal_schedules lesen
        java.util.List<String[]> schedules = new java.util.ArrayList<>();
        try (android.database.Cursor c = db.rawQuery(
                "SELECT day_of_week, meal_type, scheduled_time, duration_minutes FROM meal_schedules", null)) {
            while (c.moveToNext()) {
                schedules.add(new String[]{
                    c.getString(0),  // day_of_week
                    c.getString(1),  // meal_type
                    c.getString(2),  // scheduled_time
                    String.valueOf(c.getInt(3))  // duration_minutes
                });
            }
        }
        if (schedules.isEmpty()) {
            Log.i(TAG, "v34: No meal_schedules to convert");
            return;
        }

        // Gruppieren nach mealType
        java.util.Map<String, java.util.List<String[]>> byType = new java.util.LinkedHashMap<>();
        for (String[] s : schedules) {
            byType.computeIfAbsent(s[1], k -> new java.util.ArrayList<>()).add(s);
        }

        String today = java.time.LocalDate.now().toString();
        String[] mealIcons = {"🍳", "🍽️", "🍲", "🍎"};
        String[] mealLabels = {"Frühstück", "Mittagessen", "Abendessen", "Snack"};
        String[] mealTypes = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"};

        for (var entry : byType.entrySet()) {
            String mealType = entry.getKey();
            java.util.List<String[]> slots = entry.getValue();

            // Icon und Label bestimmen
            String icon = "🍽️";
            String label = mealType;
            for (int i = 0; i < mealTypes.length; i++) {
                if (mealTypes[i].equals(mealType)) { icon = mealIcons[i]; label = mealLabels[i]; break; }
            }

            // Tage gruppieren (Tag → Liste von Zeiten) fuer Duplikat-Erkennung
            java.util.Map<String, java.util.List<String[]>> byDay = new java.util.LinkedHashMap<>();
            for (String[] s : slots) {
                byDay.computeIfAbsent(s[0], k -> new java.util.ArrayList<>()).add(s);
            }

            // Maximale Anzahl Eintraege pro Tag = Anzahl benoetigter TrackedItems
            int maxPerDay = byDay.values().stream().mapToInt(java.util.List::size).max().orElse(1);

            for (int slotIdx = 0; slotIdx < maxPerDay; slotIdx++) {
                StringBuilder prefSchedule = new StringBuilder();
                int slotCount = 0;
                int maxDuration = 30;

                for (var dayEntry : byDay.entrySet()) {
                    java.util.List<String[]> daySlots = dayEntry.getValue();
                    if (slotIdx >= daySlots.size()) continue;
                    String[] slot = daySlots.get(slotIdx);

                    // dayKey aus DayOfWeek-Name berechnen
                    int dayKey;
                    try { dayKey = java.time.DayOfWeek.valueOf(slot[0]).getValue(); }
                    catch (Exception e) { continue; }

                    String time = (slot[2] != null) ? slot[2] : "09:00";
                    int duration = 30;
                    try { duration = Integer.parseInt(slot[3]); } catch (Exception ignored) {}
                    maxDuration = Math.max(maxDuration, duration);

                    if (prefSchedule.length() > 0) prefSchedule.append(",");
                    prefSchedule.append(dayKey).append(";").append(time).append(";0");
                    slotCount++;
                }

                if (slotCount == 0) continue;

                String title = icon + " " + label + (slotIdx > 0 ? " " + (slotIdx + 1) : "");

                ContentValues cv = new ContentValues();
                cv.put("type", "TASK");
                cv.put("title", title);
                cv.put("priority", "CRITICAL");
                cv.put("meal_type", mealType);
                cv.put("repetition_type", "REPS_PER_TIME");
                cv.put("repetition_value", slotCount);
                cv.put("repetition_unit", "WEEK");
                cv.put("pref_schedule", prefSchedule.toString());
                cv.put("max_duration_value", maxDuration);
                cv.put("max_duration_unit", "MINUTES");
                cv.put("is_completed", 0);
                cv.put("created", today);

                long itemId = db.insert("items", null, cv);
                Log.i(TAG, "v34: Created meal item '" + title + "' (id=" + itemId
                    + ", " + slotCount + " slots, " + maxDuration + "min)");
            }
        }
    }

    private static ContentValues createCV(String key, int value) {
        ContentValues cv = new ContentValues();
        cv.put(key, value);
        return cv;
    }

    // ================================================================
    // USER STATE DETECTION
    // ================================================================

    /**
     * Prueft ob dies der erste Produktions-Launch ist (Upgrade von Dev).
     */
    public boolean isFirstProductionLaunch() {
        SharedPreferences prefs = context.getSharedPreferences(
            Constants.PREF_NAME, Context.MODE_PRIVATE);
        String mode = prefs.getString(Constants.PREF_APP_MODE, null);
        return mode == null || Constants.MODE_DEVELOPMENT.equals(mode);
    }

    /**
     * Markiert die App als im Produktionsmodus laufend.
     */
    public void setProductionMode() {
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(Constants.PREF_APP_MODE, Constants.MODE_PRODUCTION)
            .apply();
        Log.i(TAG, "App mode set to PRODUCTION");
    }

    /**
     * Prueft ob die App im Produktionsmodus laeuft.
     */
    public boolean isProductionMode() {
        SharedPreferences prefs = context.getSharedPreferences(
            Constants.PREF_NAME, Context.MODE_PRIVATE);
        String mode = prefs.getString(Constants.PREF_APP_MODE, null);
        return Constants.MODE_PRODUCTION.equals(mode);
    }
}
