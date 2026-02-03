package repository;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import data.constants;
import repository.parser.itemParser;
import repository.parser.todoParser;

/**
 * Basisklasse fuer SQLite-Datenbankzugriff (Android SQLiteOpenHelper).
 *
 * Stellt die gemeinsame Infrastruktur bereit:
 * - Datenbankverbindung via SQLiteOpenHelper
 * - Tabellen-Initialisierung (Schema)
 *
 * Public API:
 * - lookup(table, filters, column) - Erster Treffer oder null
 * - lookups(table, filters, column) - Alle Treffer als Liste
 * - fetch(Table, id) / fetch(Table, filters) - Entity laden
 * - write(entity) - INSERT oder UPDATE
 */
public class SQLrepo extends SQLiteOpenHelper implements Repo {

    public SQLrepo(Context context) {
        super(context, constants.DB_NAME, null, constants.DB_VERSION);
    }

    // ============================================================================
    // DATENBANK-SETUP
    // ============================================================================

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.enableWriteAheadLogging();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS items ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "type TEXT,"
            + "title TEXT NOT NULL,"
            + "description TEXT,"
            + "created TEXT,"
            + "last_completion TEXT,"
            + "completions INTEGER DEFAULT 0,"
            + "is_completed INTEGER DEFAULT 0,"
            + "repetition_type TEXT,"
            + "repetition_unit TEXT,"
            + "repetition_value INTEGER,"
            + "complete_first INTEGER DEFAULT 0,"
            + "day_of_week TEXT,"
            + "day_of_month INTEGER,"
            + "required_completions INTEGER,"
            + "rep_interval INTEGER,"
            + "time_to_complete INTEGER DEFAULT 0,"
            + "priority TEXT,"
            + "pref_time TEXT,"
            + "parent INTEGER,"
            + "children TEXT,"
            + "followups TEXT,"
            + "daily_subgoal_limit INTEGER DEFAULT 1,"
            + "is_block INTEGER DEFAULT 0,"
            + "sequence_order INTEGER DEFAULT 0,"
            + "current_streak INTEGER DEFAULT 0,"
            + "average_streak INTEGER DEFAULT 0,"
            + "nr_of_streaks INTEGER DEFAULT 0,"
            + "total_completions INTEGER DEFAULT 0,"
            + "min_interval_days INTEGER DEFAULT 0,"
            + "cooldown INTEGER DEFAULT 0,"
            + "blocked_days TEXT,"
            + "scheduled TEXT,"
            + "progress_current INTEGER DEFAULT 0,"
            + "progress_target INTEGER DEFAULT 0,"
            + "progress_unit TEXT,"
            + "deadline TEXT,"
            + "goal_icon TEXT,"
            + "goal_color TEXT"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS config_schedules ("
            + "day_of_week TEXT PRIMARY KEY,"
            + "start_time TEXT NOT NULL,"
            + "end_time TEXT NOT NULL"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS todos ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "date TEXT NOT NULL,"
            + "start_time TEXT,"
            + "end_time TEXT"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS time_slots ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "todo_id INTEGER NOT NULL,"
            + "parent_slot_id INTEGER,"
            + "start_time TEXT,"
            + "end_time TEXT,"
            + "item_id INTEGER,"
            + "completed INTEGER DEFAULT 0,"
            + "is_calendar_event INTEGER DEFAULT 0,"
            + "calendar_title TEXT,"
            + "work_start TEXT,"
            + "work_end TEXT,"
            + "FOREIGN KEY (todo_id) REFERENCES todos(id),"
            + "FOREIGN KEY (parent_slot_id) REFERENCES time_slots(id),"
            + "FOREIGN KEY (item_id) REFERENCES items(id)"
            + ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Kein Legacy-Support: DB wird bei jedem Update geloescht und neu geseeded (siehe mainActivity)
    }

    // ============================================================================
    // HELPER
    // ============================================================================

    private void validateIdentifier(String identifier) {
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Ungueltiger Identifier: " + identifier);
        }
    }

    private Map<String, Object> cursorToMap(Cursor cursor) {
        Map<String, Object> row = new HashMap<>();
        String[] columns = cursor.getColumnNames();
        for (int i = 0; i < columns.length; i++) {
            if (cursor.isNull(i)) {
                row.put(columns[i], null);
            } else {
                int type = cursor.getType(i);
                switch (type) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        row.put(columns[i], cursor.getLong(i));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        row.put(columns[i], cursor.getDouble(i));
                        break;
                    default:
                        row.put(columns[i], cursor.getString(i));
                        break;
                }
            }
        }
        return row;
    }

    // ============================================================================
    // LOOKUP - Durchsucht Tabellen nach spezifischen Werten
    // ============================================================================

    /**
     * Durchsucht eine Tabelle nach Zeilen die den Filtern entsprechen.
     * Gibt eine Liste mit dem Wert der angegebenen Spalte pro Treffer zurueck.
     *
     * Beispiele:
     *   lookups("items", Map.of("type", "Goal", "is_completed", "0"), "id")
     *   lookups("config_schedules", Map.of("day_of_week", "MONDAY"), "start_time")
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> lookups(String table, Map<String, String> filters, String outputColumn) {
        if (filters == null || filters.isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Filter erforderlich");
        }

        validateIdentifier(table);
        validateIdentifier(outputColumn);
        for (String col : filters.keySet()) {
            validateIdentifier(col);
        }

        String whereClause = filters.keySet().stream()
            .map(col -> col + " = ?")
            .collect(Collectors.joining(" AND "));
        String[] whereArgs = filters.values().toArray(new String[0]);

        List<T> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table, new String[]{outputColumn}, whereClause, whereArgs, null, null, null);
        try {
            while (cursor.moveToNext()) {
                Object raw;
                if (cursor.isNull(0)) {
                    raw = null;
                } else {
                    int type = cursor.getType(0);
                    switch (type) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            raw = cursor.getLong(0);
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            raw = cursor.getDouble(0);
                            break;
                        default:
                            raw = cursor.getString(0);
                            break;
                    }
                }
                result.add((T) convertValue(table, outputColumn, raw));
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /**
     * Durchsucht eine Tabelle und gibt den ersten Treffer zurueck (oder null).
     *
     * Beispiel:
     *   LocalTime start = repo.lookup("config_schedules", filter, "start_time");
     */
    public <T> T lookup(String table, Map<String, String> filters, String outputColumn) {
        List<T> results = lookups(table, filters, outputColumn);
        return results.isEmpty() ? null : results.get(0);
    }

    private Object convertValue(String table, String column, Object raw) {
        if (raw == null) return null;
        switch (table) {
            case "items": return itemParser.convertValue(column, raw);
            case "todos":
            case "config_schedules": return todoParser.convertValue(column, raw);
            default: return raw;
        }
    }

    // ============================================================================
    // FETCH - Laedt ein Objekt anhand seiner ID
    // ============================================================================

    /**
     * Laedt eine Entity aus der Datenbank anhand ihrer ID.
     *
     * Beispiel:
     *   trackedItem item = repo.fetch(Table.ITEMS, 5);
     */
    @SuppressWarnings("unchecked")
    public <T> T fetch(Table<T> table, long id) {
        String tableName = table.name();
        validateIdentifier(tableName);

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(tableName, null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (!cursor.moveToFirst()) return null;

            Map<String, Object> row = cursorToMap(cursor);

            switch (tableName) {
                case "items": return (T) itemParser.fromRow(itemParser.convertRow(row));
                case "todos": return (T) todoParser.fromRow(todoParser.convertRow(row), db);
                default: throw new IllegalArgumentException("Unbekannte Tabelle: " + tableName);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Laedt eine Entity aus der Datenbank anhand von Filtern.
     * Gibt das erste Ergebnis zurueck, oder null wenn nichts gefunden.
     *
     * Beispiel:
     *   todoList list = repo.fetch(Table.TODOS, Map.of("date", "2026-01-23"));
     */
    public <T> T fetch(Table<T> table, Map<String, String> filters) {
        List<Long> ids = lookups(table.name(), filters, "id");
        if (ids.isEmpty()) return null;
        return fetch(table, ids.get(0));
    }

    // ============================================================================
    // WRITE - Schreibt ein Objekt in die Datenbank
    // ============================================================================

    /**
     * Schreibt ein Entity in die Datenbank.
     * INSERT wenn keine ID, UPDATE wenn ID vorhanden.
     *
     * Beispiel:
     *   repo.write(myTrackedItem);
     *   repo.write(myTodoList);
     */
    public void write(Object entity) {
        SQLiteDatabase db = getWritableDatabase();
        if (entity instanceof entities.trackedItem) {
            itemParser.toRow(db, (entities.trackedItem) entity);
        } else if (entity instanceof entities.todoList) {
            todoParser.toRow(db, (entities.todoList) entity);
        } else {
            throw new IllegalArgumentException("Unbekannter Entity-Typ: " + entity.getClass().getName());
        }
    }
}
