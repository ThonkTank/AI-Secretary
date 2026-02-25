package com.autosecretary.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetImportDao;
import com.autosecretary.features.budget.data.BudgetImportEntity;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetLimitDao;
import com.autosecretary.features.budget.data.BudgetLookupDao;
import com.autosecretary.features.budget.data.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.TransactionDao;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDao;
import com.autosecretary.features.task.data.TaskSlot;

@Database(
        entities = {
                TaskPrefSlot.class,
                TaskRelation.class,
                TaskCore.class,
                TaskSlot.class,
                TaskPrerequisite.class,
                TaskScheduleConfig.class,
                BudgetAccount.class,
                BudgetCategory.class,
                BudgetTransactionEntity.class,
                BudgetLimit.class,
                BudgetImportEntity.class,
                BudgetRecurringTemplateEntity.class
        },
        version = 13,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE budget_category ADD COLUMN icon TEXT NOT NULL DEFAULT '🏷️'");
            database.execSQL("ALTER TABLE budget_category ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#9E9E9E'");
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_schedule_config (
                        dayOfWeek TEXT NOT NULL,
                        startTime TEXT,
                        endTime TEXT,
                        PRIMARY KEY(dayOfWeek)
                    )
                    """);
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_core ADD COLUMN goalIcon TEXT NOT NULL DEFAULT '" + TaskCore.DEFAULT_GOAL_ICON + "'");
            database.execSQL("ALTER TABLE task_core ADD COLUMN goalColorHex TEXT NOT NULL DEFAULT '" + TaskCore.DEFAULT_GOAL_COLOR_HEX + "'");
        }
    };

    public abstract TaskDAO taskDao();

    public abstract TaskScheduleConfigDao taskScheduleConfigDao();

    public abstract BudgetLookupDao budgetLookupDao();

    public abstract TransactionDao transactionDao();

    public abstract BudgetLimitDao budgetLimitDao();

    public abstract BudgetImportDao budgetImportDao();

    public abstract BudgetRecurringTemplateDao budgetRecurringTemplateDao();

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, AppDatabase.class, "autosecretary.db")
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
