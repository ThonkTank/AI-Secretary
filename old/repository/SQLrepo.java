package repository;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import data.Constants;
import repository.parser.ItemParser;
import repository.parser.IngredientInstanceParser;
import repository.parser.TodoParser;
import repository.parser.AccountParser;
import repository.parser.TransactionParser;
import repository.parser.BudgetLimitParser;
import repository.parser.ImportParser;
import repository.parser.CategoryParser;
import repository.parser.HouseholdMemberParser;
import repository.parser.CookingPreferencesParser;
import repository.parser.IngredientParser;
import repository.parser.RecipeParser;
import repository.parser.IngredientInstanceParser;
import repository.parser.IngredientInstanceParser;
import repository.parser.ConsumptionParser;
import repository.parser.WeeklyFoodTargetParser;

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

    // ============================================================================
    // SINGLETON PATTERN
    // ============================================================================

    private static SQLrepo instance;

    /**
     * Gibt die Singleton-Instanz zurueck.
     * Thread-safe durch synchronized. Verwendet ApplicationContext um Memory-Leaks zu vermeiden.
     */
    public static synchronized SQLrepo getInstance(Context context) {
        if (instance == null) {
            instance = new SQLrepo(context.getApplicationContext());
        }
        return instance;
    }

    // ============================================================================
    // INSTANZ-FELDER
    // ============================================================================

    private final Context context;

    /**
     * Konstruktor - bevorzugt getInstance() verwenden fuer Singleton-Zugriff.
     * Direkter Aufruf ist noch moeglich fuer Rueckwaertskompatibilitaet.
     */
    public SQLrepo(Context context) {
        super(context, Constants.DB_NAME, null, Constants.DB_VERSION);
        this.context = context;
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
            + "min_duration_value INTEGER DEFAULT 0,"
            + "min_duration_unit TEXT DEFAULT 'MINUTES',"
            + "max_duration_value INTEGER DEFAULT 0,"
            + "max_duration_unit TEXT DEFAULT 'MINUTES',"
            + "priority TEXT,"
            + "pref_schedule TEXT,"
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
            + "cooldown INTEGER DEFAULT 0,"
            + "blocked_days TEXT,"
            + "scheduled TEXT,"
            + "progress_current INTEGER DEFAULT 0,"
            + "progress_target INTEGER DEFAULT 0,"
            + "progress_unit TEXT,"
            + "progress_per_rep INTEGER DEFAULT 0,"
            + "progress_last_period INTEGER DEFAULT 0,"
            + "time_per_progress_unit INTEGER DEFAULT 0,"
            + "progress_timing_count INTEGER DEFAULT 0,"
            + "deadline TEXT,"
            + "fixed_date TEXT,"
            + "fixed_time TEXT,"
            + "goal_icon TEXT,"
            + "goal_color TEXT,"
            + "predecessor INTEGER,"
            + "predecessor_delay INTEGER DEFAULT 0,"
            + "last_completion_time TEXT,"
            + "budget_requirement_cents INTEGER DEFAULT 0,"
            + "budget_account_id INTEGER,"
            + "budget_category_id INTEGER,"
            + "planned_meals TEXT,"
            + "meal_type TEXT"
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
            + "progress_delta INTEGER DEFAULT 0,"
            + "previous_completed_item_id INTEGER,"
            + "chain_id INTEGER,"
            + "FOREIGN KEY (todo_id) REFERENCES todos(id),"
            + "FOREIGN KEY (parent_slot_id) REFERENCES time_slots(id),"
            + "FOREIGN KEY (item_id) REFERENCES items(id)"
            + ")"
        );

        // ============== BUDGET-TRACKING TABELLEN ==============

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS accounts ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "name TEXT NOT NULL,"
            + "type TEXT NOT NULL,"
            + "current_balance_cents INTEGER DEFAULT 0,"
            + "initial_balance_cents INTEGER DEFAULT 0,"
            + "currency TEXT DEFAULT 'EUR',"
            + "institution TEXT,"
            + "account_number TEXT,"
            + "color TEXT,"
            + "icon TEXT,"
            + "is_active INTEGER DEFAULT 1,"
            + "include_in_total INTEGER DEFAULT 1,"
            + "created TEXT NOT NULL"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS transactions ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "account_id INTEGER NOT NULL,"
            + "amount_cents INTEGER NOT NULL,"
            + "transaction_date TEXT NOT NULL,"
            + "value_date TEXT,"
            + "category_id INTEGER,"
            + "subcategory TEXT,"
            + "description TEXT,"
            + "payee TEXT,"
            + "is_income INTEGER DEFAULT 0,"
            + "is_recurring INTEGER DEFAULT 0,"
            + "repetition_type TEXT,"
            + "repetition_value INTEGER,"
            + "repetition_unit TEXT,"
            + "day_of_week TEXT,"
            + "next_due TEXT,"
            + "last_occurrence TEXT,"
            + "amount_min_cents INTEGER,"
            + "amount_max_cents INTEGER,"
            + "amount_avg_cents INTEGER,"
            + "occurrence_count INTEGER DEFAULT 0,"
            + "parent_recurring_id INTEGER,"
            + "linked_transaction_id INTEGER,"
            + "import_id INTEGER,"
            + "import_hash TEXT,"
            + "is_confirmed INTEGER DEFAULT 1,"
            + "is_predicted INTEGER DEFAULT 0,"
            + "created TEXT NOT NULL,"
            + "FOREIGN KEY (account_id) REFERENCES accounts(id),"
            + "FOREIGN KEY (category_id) REFERENCES categories(id),"
            + "FOREIGN KEY (parent_recurring_id) REFERENCES transactions(id),"
            + "FOREIGN KEY (linked_transaction_id) REFERENCES transactions(id),"
            + "FOREIGN KEY (import_id) REFERENCES imports(id)"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS budget_limits ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "category_id INTEGER,"
            + "year_month TEXT NOT NULL,"
            + "limit_cents INTEGER NOT NULL,"
            + "spent_cents INTEGER DEFAULT 0,"
            + "rollover_cents INTEGER DEFAULT 0,"
            + "notes TEXT,"
            + "UNIQUE(category_id, year_month),"
            + "FOREIGN KEY (category_id) REFERENCES categories(id)"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS imports ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "account_id INTEGER NOT NULL,"
            + "file_name TEXT NOT NULL,"
            + "file_hash TEXT NOT NULL,"
            + "import_date TEXT NOT NULL,"
            + "period_start TEXT,"
            + "period_end TEXT,"
            + "total_transactions INTEGER DEFAULT 0,"
            + "new_transactions INTEGER DEFAULT 0,"
            + "auto_categorized INTEGER DEFAULT 0,"
            + "claude_model TEXT,"
            + "claude_prompt_tokens INTEGER,"
            + "claude_response_tokens INTEGER,"
            + "processing_time_ms INTEGER,"
            + "raw_content TEXT,"
            + "parsed_json TEXT,"
            + "status TEXT DEFAULT 'PENDING',"
            + "error_message TEXT,"
            + "FOREIGN KEY (account_id) REFERENCES accounts(id)"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS categories ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "name TEXT NOT NULL,"
            + "icon TEXT,"
            + "color TEXT,"
            + "is_income INTEGER DEFAULT 0,"
            + "is_built_in INTEGER DEFAULT 0,"
            + "sort_order INTEGER DEFAULT 0,"
            + "is_active INTEGER DEFAULT 1"
            + ")"
        );

        // ============== MEAL-PLANNING TABELLEN ==============

        createMealTables(db);

        // ============== PERFORMANCE-INDIZES ==============

        // Index fuer Budget-Tasks (Scheduling-Filter auf budget_requirement_cents > 0)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_budget " +
            "ON items(budget_requirement_cents) WHERE budget_requirement_cents > 0");

        // Index fuer feste Termine (Scheduling-Filter auf fixed_date IS NOT NULL)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_fixed " +
            "ON items(fixed_date) WHERE fixed_date IS NOT NULL");

        // Index fuer offene Items (haeufigster Query)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_items_open " +
            "ON items(is_completed, type) WHERE is_completed = 0");
    }

    /**
     * Erstellt alle Meal-Planning-Tabellen.
     * Aufgerufen von onCreate() und Migration v37.
     */
    static void createMealTables(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS household_members ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "name TEXT NOT NULL,"
            + "birth_year INTEGER,"
            + "gender TEXT,"
            + "weight_kg INTEGER,"
            + "height_cm INTEGER,"
            + "target_weight_kg INTEGER,"
            + "activity_level TEXT,"
            + "is_active INTEGER DEFAULT 1"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cooking_preferences ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "max_breakfast_cooking INTEGER DEFAULT 3,"
            + "max_lunch_cooking INTEGER DEFAULT 3,"
            + "max_dinner_cooking INTEGER DEFAULT 3,"
            + "max_snack_cooking INTEGER DEFAULT 0,"
            + "breakfast_cooking_days TEXT,"
            + "lunch_cooking_days TEXT,"
            + "dinner_cooking_days TEXT,"
            + "snack_cooking_days TEXT,"
            + "quick_prep_max_minutes INTEGER DEFAULT 15"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ingredients ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "name TEXT NOT NULL,"
            + "food_group TEXT,"
            + "default_unit TEXT,"
            + "grams_per_unit INTEGER DEFAULT 1,"
            + "calories_per_100 INTEGER DEFAULT 0,"
            + "protein_per_100 INTEGER DEFAULT 0,"
            + "carbs_per_100 INTEGER DEFAULT 0,"
            + "fat_per_100 INTEGER DEFAULT 0,"
            + "fiber_per_100 INTEGER DEFAULT 0,"
            + "shelf_life_days INTEGER DEFAULT 0,"
            + "requires_refrigeration INTEGER DEFAULT 0,"
            + "is_whole_unit INTEGER DEFAULT 0,"
            + "is_perishable INTEGER DEFAULT 0,"
            + "store_packages TEXT"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS recipes ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "title TEXT NOT NULL,"
            + "description TEXT,"
            + "instructions TEXT,"
            + "meal_types TEXT,"
            + "prep_time_minutes INTEGER DEFAULT 0,"
            + "cook_time_minutes INTEGER DEFAULT 0,"
            + "servings INTEGER DEFAULT 2,"
            + "min_servings INTEGER DEFAULT 1,"
            + "max_servings INTEGER DEFAULT 8,"
            + "scaling_precision TEXT DEFAULT 'ROUGH',"
            + "prep_effort TEXT DEFAULT 'MEDIUM',"
            + "ingredients_data TEXT,"
            + "tags TEXT,"
            + "last_used TEXT,"
            + "usage_count INTEGER DEFAULT 0,"
            + "is_favorite INTEGER DEFAULT 0,"
            + "total_calories INTEGER DEFAULT 0,"
            + "total_protein INTEGER DEFAULT 0,"
            + "total_carbs INTEGER DEFAULT 0,"
            + "total_fat INTEGER DEFAULT 0,"
            + "shelf_life_days INTEGER DEFAULT 0,"
            + "ratings_data TEXT"
            + ")"
        );


        db.execSQL(
            "CREATE TABLE IF NOT EXISTS shopping_list_items ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "ingredient_id INTEGER NOT NULL,"
            + "amount REAL DEFAULT 0,"
            + "is_purchased INTEGER DEFAULT 0,"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS pantry_items ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "ingredient_id INTEGER NOT NULL,"
            + "amount REAL DEFAULT 0,"
            + "unit TEXT,"
            + "purchase_date TEXT,"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS consumption_logs ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "date TEXT NOT NULL,"
            + "item_id INTEGER,"
            + "member_id INTEGER,"
            + "recipe_id INTEGER,"
            + "servings_consumed REAL DEFAULT 0,"
            + "calories INTEGER DEFAULT 0,"
            + "protein INTEGER DEFAULT 0,"
            + "carbs INTEGER DEFAULT 0,"
            + "fat INTEGER DEFAULT 0,"
            + "FOREIGN KEY (item_id) REFERENCES items(id),"
            + "FOREIGN KEY (member_id) REFERENCES household_members(id),"
            + "FOREIGN KEY (recipe_id) REFERENCES recipes(id)"
            + ")"
        );

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS weekly_food_targets ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "period_key TEXT,"
            + "grain_grams INTEGER DEFAULT 0, potato_grams INTEGER DEFAULT 0,"
            + "vegetable_grams INTEGER DEFAULT 0, fruit_grams INTEGER DEFAULT 0,"
            + "dairy_grams INTEGER DEFAULT 0, meat_grams INTEGER DEFAULT 0,"
            + "fish_grams INTEGER DEFAULT 0, egg_grams INTEGER DEFAULT 0,"
            + "fat_grams INTEGER DEFAULT 0, legume_grams INTEGER DEFAULT 0,"
            + "nut_grams INTEGER DEFAULT 0,"
            + "grain_planned INTEGER DEFAULT 0, potato_planned INTEGER DEFAULT 0,"
            + "vegetable_planned INTEGER DEFAULT 0, fruit_planned INTEGER DEFAULT 0,"
            + "dairy_planned INTEGER DEFAULT 0, meat_planned INTEGER DEFAULT 0,"
            + "fish_planned INTEGER DEFAULT 0, egg_planned INTEGER DEFAULT 0,"
            + "fat_planned INTEGER DEFAULT 0, legume_planned INTEGER DEFAULT 0,"
            + "nut_planned INTEGER DEFAULT 0"
            + ")"
        );

    }

    // ============================================================================
    // HELPER
    // ============================================================================

    private void validateIdentifier(String identifier) {
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Ungueltiger Identifier: " + identifier);
        }
    }

    private static final Set<String> ALLOWED_OPS = Set.of("=", ">=", "<=", ">", "<", "!=");

    private record WhereClause(String clause, String[] args) {}

    /**
     * Baut WHERE-Clause aus Filter-Map. Keys koennen Operatoren enthalten:
     * "column" → column = ?, "column >=" → column >= ?, etc.
     */
    private WhereClause buildWhereClause(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return new WhereClause(null, null);
        }
        List<String> conditions = new ArrayList<>();
        List<String> args = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey().trim();
            String column;
            String op;
            int spaceIdx = key.indexOf(' ');
            if (spaceIdx > 0) {
                column = key.substring(0, spaceIdx);
                op = key.substring(spaceIdx).trim();
                if (!ALLOWED_OPS.contains(op)) {
                    throw new IllegalArgumentException("Ungueltiger Operator: " + op);
                }
            } else {
                column = key;
                op = "=";
            }
            validateIdentifier(column);
            conditions.add(column + " " + op + " ?");
            args.add(entry.getValue());
        }
        return new WhereClause(String.join(" AND ", conditions), args.toArray(new String[0]));
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
        // Leere Filter erlaubt → gibt alle Einträge zurück
        if (filters == null) {
            filters = Map.of();
        }

        validateIdentifier(table);
        validateIdentifier(outputColumn);
        WhereClause where = buildWhereClause(filters);

        List<T> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(table, new String[]{outputColumn}, where.clause(), where.args(), null, null, null);
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
            case "items": return ItemParser.convertValue(column, raw);
            case "todos":
            case "config_schedules": return TodoParser.convertValue(column, raw);
            case "accounts": return AccountParser.convertValue(column, raw);
            case "transactions": return TransactionParser.convertValue(column, raw);
            case "budget_limits": return BudgetLimitParser.convertValue(column, raw);
            case "imports": return ImportParser.convertValue(column, raw);
            case "categories": return CategoryParser.convertValue(column, raw);
            case "household_members": return HouseholdMemberParser.convertValue(column, raw);
            case "cooking_preferences": return CookingPreferencesParser.convertValue(column, raw);
            case "ingredients": return IngredientParser.convertValue(column, raw);
            case "recipes": return RecipeParser.convertValue(column, raw);
            case "shopping_list_items": return IngredientInstanceParser.convertValue(column, raw);
            case "pantry_items": return IngredientInstanceParser.convertValue(column, raw);
            case "consumption_logs": return ConsumptionParser.convertValue(column, raw);
            case "weekly_food_targets": return WeeklyFoodTargetParser.convertValue(column, raw);
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
                case "items": return (T) ItemParser.fromRow(ItemParser.convertRow(row));
                case "todos": return (T) TodoParser.fromRow(TodoParser.convertRow(row), db);
                case "accounts": return (T) AccountParser.fromRow(AccountParser.convertRow(row));
                case "transactions": return (T) TransactionParser.fromRow(TransactionParser.convertRow(row));
                case "budget_limits": return (T) BudgetLimitParser.fromRow(BudgetLimitParser.convertRow(row));
                case "imports": return (T) ImportParser.fromRow(ImportParser.convertRow(row));
                case "categories": return (T) CategoryParser.fromRow(CategoryParser.convertRow(row));
                case "household_members": return (T) HouseholdMemberParser.fromRow(HouseholdMemberParser.convertRow(row));
                case "cooking_preferences": return (T) CookingPreferencesParser.fromRow(CookingPreferencesParser.convertRow(row));
                case "ingredients": return (T) IngredientParser.fromRow(IngredientParser.convertRow(row));
                case "recipes": return (T) RecipeParser.fromRow(RecipeParser.convertRow(row));
                case "shopping_list_items": return (T) IngredientInstanceParser.fromRow(IngredientInstanceParser.convertRow(row));
                case "pantry_items": return (T) IngredientInstanceParser.fromRow(IngredientInstanceParser.convertRow(row));
                case "consumption_logs": return (T) ConsumptionParser.fromRow(ConsumptionParser.convertRow(row));
                case "weekly_food_targets": return (T) WeeklyFoodTargetParser.fromRow(WeeklyFoodTargetParser.convertRow(row));
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
    // FETCH ALL - Lädt alle Einträge einer Tabelle (optimiert - single query)
    // ============================================================================

    /**
     * Lädt alle Einträge einer Tabelle.
     *
     * Beispiel:
     *   List<Recipe> recipes = repo.fetchAll(Table.RECIPES);
     */
    public <T> List<T> fetchAll(Table<T> table) {
        return fetchAll(table, null);
    }

    /**
     * Lädt Einträge einer Tabelle mit optionalen Filtern in einer einzigen Query.
     * Ersetzt das ineffiziente N+1 Query-Pattern durch Batch-Fetch.
     *
     * Beispiel:
     *   List<Account> activeAccounts = repo.fetchAll(Table.ACCOUNTS, Map.of("is_active", "1"));
     *   List<Transaction> monthTx = repo.fetchAll(Table.TRANSACTIONS, Map.of("account_id", "5"));
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> fetchAll(Table<T> table, Map<String, String> filters) {
        String tableName = table.name();
        validateIdentifier(tableName);
        WhereClause where = buildWhereClause(filters);

        List<T> result = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(tableName, null, where.clause(), where.args(), null, null, null);

        try {
            while (cursor.moveToNext()) {
                Map<String, Object> row = cursorToMap(cursor);
                T entity = (T) parseEntity(tableName, row, db);
                if (entity != null) {
                    result.add(entity);
                }
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    /**
     * Parst eine DB-Row in die entsprechende Entity.
     * Extrahiert aus dem switch-Statement in fetch() für Wiederverwendung.
     */
    private Object parseEntity(String tableName, Map<String, Object> row, SQLiteDatabase db) {
        switch (tableName) {
            case "items": return ItemParser.fromRow(ItemParser.convertRow(row));
            case "todos": return TodoParser.fromRow(TodoParser.convertRow(row), db);
            case "accounts": return AccountParser.fromRow(AccountParser.convertRow(row));
            case "transactions": return TransactionParser.fromRow(TransactionParser.convertRow(row));
            case "budget_limits": return BudgetLimitParser.fromRow(BudgetLimitParser.convertRow(row));
            case "imports": return ImportParser.fromRow(ImportParser.convertRow(row));
            case "categories": return CategoryParser.fromRow(CategoryParser.convertRow(row));
            case "household_members": return HouseholdMemberParser.fromRow(HouseholdMemberParser.convertRow(row));
            case "cooking_preferences": return CookingPreferencesParser.fromRow(CookingPreferencesParser.convertRow(row));
            case "ingredients": return IngredientParser.fromRow(IngredientParser.convertRow(row));
            case "recipes": return RecipeParser.fromRow(RecipeParser.convertRow(row));
            case "shopping_list_items": return IngredientInstanceParser.fromRow(IngredientInstanceParser.convertRow(row));
            case "pantry_items": return IngredientInstanceParser.fromRow(IngredientInstanceParser.convertRow(row));
            case "consumption_logs": return ConsumptionParser.fromRow(ConsumptionParser.convertRow(row));
            case "weekly_food_targets": return WeeklyFoodTargetParser.fromRow(WeeklyFoodTargetParser.convertRow(row));
            default: throw new IllegalArgumentException("Unbekannte Tabelle: " + tableName);
        }
    }


    // ============================================================================
    // DELETE - Löscht einen Eintrag aus der Datenbank
    // ============================================================================

    /**
     * Löscht einen Eintrag per ID.
     *
     * Beispiel:
     *   repo.delete(Table.RECIPES, 5L);
     */
    public <T> void delete(Table<T> table, long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(table.name(), "id = ?", new String[]{String.valueOf(id)});
    }

    /**
     * Löscht alle Einträge die den Filtern entsprechen.
     *
     * Beispiel:
     *   repo.delete(Table.SHOPPING_LIST_ITEMS, Map.of("period_key", "2026-02-14"));
     */
    public <T> void delete(Table<T> table, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) return;
        String tableName = table.name();
        validateIdentifier(tableName);
        WhereClause where = buildWhereClause(filters);

        SQLiteDatabase db = getWritableDatabase();
        db.delete(tableName, where.clause(), where.args());
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
        if (entity instanceof entities.TrackedItem) {
            ItemParser.toRow(db, (entities.TrackedItem) entity);
        } else if (entity instanceof entities.TodoList) {
            TodoParser.toRow(db, (entities.TodoList) entity);
        } else if (entity instanceof entities.Account) {
            AccountParser.toRow(db, (entities.Account) entity);
        } else if (entity instanceof entities.Transaction) {
            TransactionParser.toRow(db, (entities.Transaction) entity);
        } else if (entity instanceof entities.BudgetLimit) {
            BudgetLimitParser.toRow(db, (entities.BudgetLimit) entity);
        } else if (entity instanceof entities.Import) {
            ImportParser.toRow(db, (entities.Import) entity);
        } else if (entity instanceof entities.Category) {
            CategoryParser.toRow(db, (entities.Category) entity);
        } else if (entity instanceof entities.HouseholdMember) {
            HouseholdMemberParser.toRow(db, (entities.HouseholdMember) entity);
        } else if (entity instanceof entities.CookingPreferences) {
            CookingPreferencesParser.toRow(db, (entities.CookingPreferences) entity);
        } else if (entity instanceof entities.Ingredient) {
            IngredientParser.toRow(db, (entities.Ingredient) entity);
        } else if (entity instanceof entities.Recipe) {
            RecipeParser.toRow(db, (entities.Recipe) entity);
        } else if (entity instanceof entities.ShoppingListItem) {
            IngredientInstanceParser.toRow(db, (entities.ShoppingListItem) entity);
        } else if (entity instanceof entities.ConsumptionLog) {
            ConsumptionParser.toRow(db, (entities.ConsumptionLog) entity);
        } else if (entity instanceof entities.WeeklyFoodTarget) {
            WeeklyFoodTargetParser.toRow(db, (entities.WeeklyFoodTarget) entity);
        } else {
            throw new IllegalArgumentException("Unbekannter Entity-Typ: " + entity.getClass().getName());
        }
    }
}
