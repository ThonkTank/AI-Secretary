package com.autosecretary.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

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
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.data.TaskPlannedMeal;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDao;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.data.TaskTransitionStat;
import com.autosecretary.features.task.data.TaskTransitionStatDao;

/**
 * SQLite database abstraction for AutoSecretary using Android Room ORM.
 * <p>
 * This is a single-instance database accessible via {@link #getInstance(Context)}.
 * Room automatically handles table creation, schema versioning (v24), and type conversion.
 * </p>
 * <p>
 * <strong>Database version:</strong> 24. Schema changes require only a version bump;
 * {@link #getInstance(Context)} uses {@code fallbackToDestructiveMigration()}, which drops
 * and recreates all tables on schema changes — intentional in this project (manual
 * {@code Migration} subclasses are forbidden; see CLAUDE.md). Always back up user data
 * before bumping the schema version.
 * </p>
 * <p>
 * <strong>Type converters:</strong> See {@link Converters}. Room stores Java objects as SQLite-compatible
 * types (e.g., {@code LocalDate} → string, enums → their {@code .name()}).
 * </p>
 * <p>
 * <strong>Architecture:</strong> App → Task/Budget feature layers → application/domain → data (DAOs here).
 * All database access is single-threaded via {@code AppCompositionRoot.databaseExecutor}.
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
        version = 24,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DB_NAME = "autosecretary.db";

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
                    // Intentional: schema changes drop and recreate all tables (user data lost on upgrade).
                    // Manual Migration subclasses are explicitly forbidden in this project — see CLAUDE.md.
                    // Always back up user data before bumping the schema version.
                    .fallbackToDestructiveMigration()
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
