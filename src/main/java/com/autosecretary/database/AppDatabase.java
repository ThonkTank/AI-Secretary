package com.autosecretary.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;
import com.autosecretary.features.budget.data.dao.BudgetImportDao;
import com.autosecretary.features.budget.data.entity.BudgetImportEntity;
import com.autosecretary.features.budget.data.entity.BudgetLimitEntity;
import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.BudgetAccountCategoryDao;
import com.autosecretary.features.budget.data.dao.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.dao.BudgetTransactionDao;
import com.autosecretary.features.meal.data.dao.MealConsumptionLogDao;
import com.autosecretary.features.meal.data.dao.MealCookingPreferencesDao;
import com.autosecretary.features.meal.data.dao.MealHouseholdMemberDao;
import com.autosecretary.features.meal.data.dao.MealIngredientDao;
import com.autosecretary.features.meal.data.dao.MealPantryDao;
import com.autosecretary.features.meal.data.dao.MealPlanDao;
import com.autosecretary.features.meal.data.dao.MealRecipeDao;
import com.autosecretary.features.meal.data.dao.MealWeeklyFoodTargetDao;
import com.autosecretary.features.meal.data.entity.MealConsumptionLogEntity;
import com.autosecretary.features.meal.data.entity.MealCookingPreferencesEntity;
import com.autosecretary.features.meal.data.entity.MealHouseholdMemberEntity;
import com.autosecretary.features.meal.data.entity.MealIngredientEntity;
import com.autosecretary.features.meal.data.entity.MealMemberRatingEntity;
import com.autosecretary.features.meal.data.entity.MealPantryItemEntity;
import com.autosecretary.features.meal.data.entity.MealPlanEntity;
import com.autosecretary.features.meal.data.entity.MealRecipeEntity;
import com.autosecretary.features.meal.data.entity.MealRecipeIngredientEntity;
import com.autosecretary.features.meal.data.entity.MealShoppingListItemEntity;
import com.autosecretary.features.meal.data.entity.MealStorePackageEntity;
import com.autosecretary.features.meal.data.entity.MealWeeklyFoodTargetEntity;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.model.TaskPlannedMeal;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskPrerequisite;
import com.autosecretary.features.task.domain.model.TaskRelation;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDao;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStat;
import com.autosecretary.features.task.data.TaskTransitionStatDao;

/**
 * SQLite database abstraction for AutoSecretary using Android Room ORM.
 * <p>
 * This is a single-instance database accessible via {@link #getInstance(Context)}.
 * Room automatically handles table creation, schema versioning (v27), and type conversion.
 * </p>
 * <p>
 * <strong>Database version:</strong> 27. Schema changes require a version bump and
 * compatible Room migration(s). This project no longer uses destructive fallback
 * migrations because the app now stores user data that must be preserved.
 * </p>
 * <p>
 * <strong>Type converters:</strong> See {@link Converters}. Room stores Java objects as SQLite-compatible
 * types (e.g., {@code LocalDate} → string, enums → their {@code .name()}).
 * </p>
 * <p>
 * <strong>Architecture:</strong> App → Task/Budget feature layers → application/domain → data (DAOs here).
 * All database access is single-threaded via {@code AppCompositionRoot.getDbExecutor()}.
 * </p>
 * <p>
 * <strong>Further reading:</strong>
 * <a href="https://developer.android.com/training/data-storage/room">Android Room documentation</a>
 * </p>
 */
@Database(
        entities = {
                TaskPrefSlot.class,
                TaskRelation.class,
                TaskCore.class,
                TaskSlot.class,
                TaskPrerequisite.class,
                TaskPlannedMeal.class,
                TaskScheduleConfig.class,
                TaskTransitionStat.class,
                BudgetAccountEntity.class,
                BudgetCategoryEntity.class,
                BudgetTransactionEntity.class,
                BudgetLimitEntity.class,
                BudgetImportEntity.class,
                BudgetRecurringTemplateEntity.class,
                MealPlanEntity.class,
                MealRecipeEntity.class,
                MealRecipeIngredientEntity.class,
                MealMemberRatingEntity.class,
                MealIngredientEntity.class,
                MealStorePackageEntity.class,
                MealPantryItemEntity.class,
                MealShoppingListItemEntity.class,
                MealConsumptionLogEntity.class,
                MealHouseholdMemberEntity.class,
                MealCookingPreferencesEntity.class,
                MealWeeklyFoodTargetEntity.class
        },
        version = 27,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DB_NAME = "autosecretary.db";
    public static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // No schema delta: this bridge keeps upgrade paths explicit now that
            // destructive fallback is disabled.
        }
    };
    public static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_core ADD COLUMN startDate TEXT");
        }
    };
    public static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("""
                    CREATE TABLE task_relation_new (
                        child TEXT NOT NULL,
                        parent TEXT NOT NULL,
                        PRIMARY KEY(child, parent),
                        FOREIGN KEY(child) REFERENCES task_core(id) ON DELETE CASCADE
                    )
                    """);
            database.execSQL("""
                    INSERT OR REPLACE INTO task_relation_new (child, parent)
                    SELECT child, parent
                    FROM task_relation
                    """);
            database.execSQL("DROP TABLE task_relation");
            database.execSQL("ALTER TABLE task_relation_new RENAME TO task_relation");
            database.execSQL("CREATE INDEX index_task_relation_child ON task_relation(child)");

            database.execSQL("""
                    CREATE TABLE task_prerequisites_new (
                        taskId TEXT NOT NULL,
                        prerequisiteId TEXT NOT NULL,
                        minGapMinutes INTEGER NOT NULL,
                        PRIMARY KEY(taskId, prerequisiteId),
                        FOREIGN KEY(taskId) REFERENCES task_core(id) ON DELETE CASCADE
                    )
                    """);
            database.execSQL("""
                    INSERT OR REPLACE INTO task_prerequisites_new (taskId, prerequisiteId, minGapMinutes)
                    SELECT taskId, prerequisiteId, minGapMinutes
                    FROM task_prerequisites
                    """);
            database.execSQL("DROP TABLE task_prerequisites");
            database.execSQL("ALTER TABLE task_prerequisites_new RENAME TO task_prerequisites");
            database.execSQL("CREATE INDEX index_task_prerequisites_taskId ON task_prerequisites(taskId)");
        }
    };
    public static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("""
                    CREATE TABLE budget_recurring_template_new (
                        id TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        normalizedPayee TEXT NOT NULL,
                        displayPayee TEXT,
                        categoryId TEXT,
                        avgAmountCents INTEGER NOT NULL,
                        minAmountCents INTEGER NOT NULL,
                        maxAmountCents INTEGER NOT NULL,
                        recurringType TEXT NOT NULL,
                        type TEXT NOT NULL,
                        recurringValue INTEGER NOT NULL,
                        recurringDayOfWeek TEXT,
                        nextDue TEXT,
                        active INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(accountId) REFERENCES budget_account(id) ON DELETE RESTRICT ON UPDATE CASCADE,
                        FOREIGN KEY(categoryId) REFERENCES budget_category(id) ON DELETE SET NULL ON UPDATE CASCADE
                    )
                    """);
            database.execSQL("""
                    INSERT INTO budget_recurring_template_new (
                        id,
                        accountId,
                        normalizedPayee,
                        displayPayee,
                        categoryId,
                        avgAmountCents,
                        minAmountCents,
                        maxAmountCents,
                        recurringType,
                        type,
                        recurringValue,
                        recurringDayOfWeek,
                        nextDue,
                        active
                    )
                    SELECT
                        id,
                        accountId,
                        normalizedPayee,
                        displayPayee,
                        categoryId,
                        avgAmountCents,
                        minAmountCents,
                        maxAmountCents,
                        recurringType,
                        transactionType,
                        recurringValue,
                        recurringDayOfWeek,
                        nextDue,
                        active
                    FROM budget_recurring_template
                    """);
            database.execSQL("DROP TABLE budget_recurring_template");
            database.execSQL("ALTER TABLE budget_recurring_template_new RENAME TO budget_recurring_template");
            database.execSQL("CREATE INDEX index_budget_recurring_template_accountId ON budget_recurring_template(accountId)");
            database.execSQL("CREATE INDEX index_budget_recurring_template_categoryId ON budget_recurring_template(categoryId)");
        }
    };

    public abstract TaskDao taskDao();

    public abstract TaskScheduleConfigDao taskScheduleConfigDao();

    public abstract TaskTransitionStatDao taskTransitionStatDao();

    public abstract BudgetAccountCategoryDao budgetAccountCategoryDao();

    public abstract BudgetTransactionDao budgetTransactionDao();

    public abstract BudgetLimitDao budgetLimitDao();

    public abstract BudgetImportDao budgetImportDao();

    public abstract BudgetRecurringTemplateDao budgetRecurringTemplateDao();

    public abstract MealPlanDao mealPlanDao();

    public abstract MealRecipeDao mealRecipeDao();

    public abstract MealIngredientDao mealIngredientDao();

    public abstract MealPantryDao mealPantryDao();

    public abstract MealConsumptionLogDao mealConsumptionLogDao();

    public abstract MealHouseholdMemberDao mealHouseholdMemberDao();

    public abstract MealCookingPreferencesDao mealCookingPreferencesDao();

    public abstract MealWeeklyFoodTargetDao mealWeeklyFoodTargetDao();

    private static AppDatabase instance;

    /**
     * Returns the single AppDatabase instance, creating it if needed.
     * <p>
     * <strong>Thread-safe.</strong> Safe to call from multiple threads; synchronized ensures
     * only one database is ever created.
     * </p>
     * <p>
     * <strong>Typical usage:</strong> Call once at app startup (e.g., in {@code AutoSecretaryApplication.onCreate()}).
     * Repeated calls return the cached instance with no overhead.
     * </p>
     *
     * @param context Application context used to locate the database file.
     * @return The singleton AppDatabase instance.
     */
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DB_NAME)
                    .addMigrations(MIGRATION_23_24)
                    .addMigrations(MIGRATION_24_25)
                    .addMigrations(MIGRATION_25_26)
                    .addMigrations(MIGRATION_26_27)
                    .build();
        }
        return instance;
    }

    /**
     * Closes the database and clears the singleton instance.
     * <p>
     * <strong>Typical usage:</strong> Call when reloading data (e.g., after CSV import) or during app shutdown.
     * After calling this, the next call to {@link #getInstance(Context)} will create a fresh database connection.
     * </p>
     */
    public static synchronized void closeAndReset() {
        if (instance == null) return;
        instance.close();
        instance = null;
    }
}
