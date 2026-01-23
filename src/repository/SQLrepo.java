package repository;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import repository.parser.itemParser;
import repository.parser.todoParser;

/**
 * Basisklasse fuer SQLite-Datenbankzugriff.
 *
 * Stellt nur die gemeinsame Infrastruktur bereit:
 * - Datenbankverbindung
 * - Tabellen-Initialisierung (Schema)
 *
 * CRUD-Operationen sind in den Subklassen:
 * - itemCRUD: Items (Tasks/Goals/Projects)
 * - todoCRUD: Daily Todos
 * - configCRUD: Konfiguration
 */
public class SQLrepo {

    // JDBC-Treiber beim Laden der Klasse registrieren
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC-Treiber nicht gefunden", e);
        }
    }

    // Pfad zur Datenbank-Datei (z.B. "secretary.db" oder "/data/app/secretary.db")
    private final String dbPath;

    /**
     * Konstruktor - wird einmal beim App-Start aufgerufen.
     *
     * @param dbPath Pfad zur SQLite-Datei (wird erstellt falls nicht vorhanden)
     *
     * Beispiel: new SQLrepo("secretary.db")
     */
    public SQLrepo(String dbPath) {
        this.dbPath = dbPath;
        initializeDatabase();  // Tabelle erstellen falls nötig
    }

    // ============================================================================
    // DATENBANK-SETUP
    // ============================================================================

    /**
     * Erstellt die Tabelle falls sie noch nicht existiert.
     *
     * Wird automatisch im Konstruktor aufgerufen - du musst das nie manuell tun.
     * "CREATE TABLE IF NOT EXISTS" bedeutet: Nur erstellen wenn nicht vorhanden.
     *
     * Die Spalten entsprechen den Feldern in trackedItem:
     *   - id: Eindeutige Nummer, wird automatisch hochgezählt (AUTOINCREMENT)
     *   - title: Der Name der Aufgabe (NOT NULL = Pflichtfeld)
     *   - description: Optionale Beschreibung
     *   - usw.
     */
    private void initializeDatabase() {
        // SQL-Befehl zum Erstellen der Tabelle
        // """ ... """ ist ein Java Text Block (mehrzeiliger String)
        String itemsTable = """
            CREATE TABLE IF NOT EXISTS items (
                -- Basic Fields (Grunddaten)
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT,
                title TEXT NOT NULL,
                description TEXT,
                created TEXT,

                -- Completion Logik (Abschluss-Tracking)
                last_completion TEXT,
                completions INTEGER DEFAULT 0,
                is_completed INTEGER DEFAULT 0,

                -- Repetition Logik (Wiederholungen)
                -- Repetition ist ein komplexes Objekt, daher aufgeteilt:
                repetition_type TEXT,
                repetition_unit TEXT,
                repetition_value INTEGER,
                complete_first INTEGER DEFAULT 0,
                -- Timeframe für nextRepetition (kann Start + Ende haben)
                next_rep_start TEXT,
                next_rep_end TEXT,

                -- Erweiterte Repetition-Felder (für Subklassen)
                day_of_week TEXT,
                day_of_month INTEGER,
                required_completions INTEGER,
                rep_interval INTEGER,

                -- Planung
                time_to_complete INTEGER DEFAULT 0,
                priority TEXT,
                pref_time TEXT,

                -- Relationen
                -- parent: einzelne ID (Long)
                -- children: "1,2,3" (List<Long>)
                -- followups: "5:3,8:1" (Map<Long,Integer> als id:count Paare)
                parent INTEGER,
                children TEXT,
                followups TEXT,

                -- Goal-spezifisch
                daily_subgoal_limit INTEGER DEFAULT 1,
                is_block INTEGER DEFAULT 0,
                sequence_order INTEGER DEFAULT 0,

                -- History (Statistiken)
                current_streak INTEGER DEFAULT 0,
                average_streak INTEGER DEFAULT 0,
                nr_of_streaks INTEGER DEFAULT 0,
                total_completions INTEGER DEFAULT 0,

                -- Scheduling Constraints
                min_interval_days INTEGER DEFAULT 0,
                cooldown INTEGER DEFAULT 0,

                -- Aktuelle Einplanungen (kommaseparierte ISO-Daten, z.B. "2026-01-21,2026-01-23")
                scheduled TEXT
            )
            """;

        // Tabelle für Config-Schedules (Map<DayOfWeek, DaySchedule>)
        String schedulesTable = """
            CREATE TABLE IF NOT EXISTS config_schedules (
                day_of_week TEXT PRIMARY KEY,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL
            )
            """;

        // Tabelle für den Tagesplan (generierte ToDo-Liste)
        String dailyTodosTable = """
            CREATE TABLE IF NOT EXISTS todos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                start_time TEXT,
                end_time TEXT
            )
            """;

        // Tabelle für TimeSlots (verschachtelt: Goal-Slots → Task-Slots)
        String timeSlotsTable = """
            CREATE TABLE IF NOT EXISTS time_slots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                todo_id INTEGER NOT NULL,
                parent_slot_id INTEGER,
                start_time TEXT,
                end_time TEXT,
                item_id INTEGER,
                completed INTEGER DEFAULT 0,
                FOREIGN KEY (todo_id) REFERENCES todos(id),
                FOREIGN KEY (parent_slot_id) REFERENCES time_slots(id),
                FOREIGN KEY (item_id) REFERENCES items(id)
            )
            """;

        // try-with-resources: Connection und Statement werden automatisch geschlossen
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(itemsTable);
            stmt.execute(schedulesTable);
            stmt.execute(dailyTodosTable);
            stmt.execute(timeSlotsTable);

        } catch (SQLException e) {
            // Wenn etwas schiefgeht, werfen wir einen Fehler
            throw new RuntimeException("Datenbank konnte nicht initialisiert werden", e);
        }
    }

    /**
     * Stellt eine Verbindung zur SQLite-Datenbank her.
     *
     * "jdbc:sqlite:" ist das Protokoll für SQLite-Verbindungen.
     * Die Connection muss nach Benutzung geschlossen werden (passiert automatisch
     * durch try-with-resources in den anderen Methoden).
     *
     * @return Eine offene Datenbankverbindung
     */
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    // ============================================================================
    // LOOKUP - Durchsucht Tabellen nach spezifischen Werten
    // ============================================================================

    private void validateIdentifier(String identifier) {
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Ungültiger Identifier: " + identifier);
        }
    }

    /**
     * Durchsucht eine Tabelle nach Zeilen die den Filtern entsprechen.
     * Gibt eine Liste mit dem Wert der angegebenen Spalte pro Treffer zurück.
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
            .collect(java.util.stream.Collectors.joining(" AND "));

        String sql = String.format("SELECT %s FROM %s WHERE %s", outputColumn, table, whereClause);

        List<T> result = new java.util.ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            int i = 1;
            for (String value : filters.values()) {
                pstmt.setString(i++, value);
            }

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Object raw = rs.getObject(1);
                result.add((T) convertValue(table, outputColumn, raw));
            }
            return result;

        } catch (SQLException e) {
            throw new RuntimeException("Lookup fehlgeschlagen: " + sql, e);
        }
    }

    /**
     * Durchsucht eine Tabelle und gibt den ersten Treffer zurück (oder null).
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
        return switch (table) {
            case "items" -> itemParser.convertValue(column, raw);
            case "todos", "config_schedules" -> todoParser.convertValue(column, raw);
            default -> raw;
        };
    }

    // ============================================================================
    // FETCH - Lädt ein Objekt anhand seiner ID
    // ============================================================================

    /**
     * Lädt eine Entity aus der Datenbank anhand ihrer ID.
     *
     * Beispiel:
     *   trackedItem item = repo.fetch(Table.ITEMS, 5);
     */
    @SuppressWarnings("unchecked")
    public <T> T fetch(Table<T> table, long id) {
        String tableName = table.name();
        validateIdentifier(tableName);
        String sql = String.format("SELECT * FROM %s WHERE id = ?", tableName);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) return null;

            Map<String, Object> row = new HashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int j = 1; j <= meta.getColumnCount(); j++) {
                row.put(meta.getColumnName(j), rs.getObject(j));
            }

            return (T) switch (tableName) {
                case "items" -> itemParser.fromRow(itemParser.convertRow(row));
                case "todos" -> todoParser.fromRow(todoParser.convertRow(row), conn);
                default -> throw new IllegalArgumentException("Unbekannte Tabelle: " + tableName);
            };

        } catch (SQLException e) {
            throw new RuntimeException("Fetch fehlgeschlagen: " + tableName + " id=" + id, e);
        }
    }

    /**
     * Lädt eine Entity aus der Datenbank anhand von Filtern.
     * Gibt das erste Ergebnis zurück, oder null wenn nichts gefunden.
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
     *   repo.write(myTrackedItem);  // trackedItem → items
     *   repo.write(myTodoList);     // todoList → daily_todos
     */
    public void write(Object entity) {
        try (Connection conn = getConnection()) {
            switch (entity) {
                case entities.trackedItem item -> itemParser.toRow(conn, item);
                case entities.todoList todo -> todoParser.toRow(conn, todo);
                default -> throw new IllegalArgumentException("Unbekannter Entity-Typ: " + entity.getClass().getName());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Write fehlgeschlagen", e);
        }
    }
}
