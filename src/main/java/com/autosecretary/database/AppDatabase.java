package com.autosecretary.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.importing.BudgetImportDao;
import com.autosecretary.features.budget.data.importing.BudgetImportEntity;
import com.autosecretary.features.budget.data.entity.BudgetLimit;
import com.autosecretary.features.budget.data.dao.BudgetLimitDao;
import com.autosecretary.features.budget.data.dao.BudgetLookupDao;
import com.autosecretary.features.budget.data.importing.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.importing.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.dao.TransactionDao;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskPrefSlot;
import com.autosecretary.features.task.data.TaskPrerequisite;
import com.autosecretary.features.task.data.TaskRelation;
import com.autosecretary.features.task.data.TaskScheduleConfig;
import com.autosecretary.features.task.data.TaskScheduleConfigDAO;
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
        version = 15,
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

            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS budget_transaction_new (
                        id TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        categoryId TEXT,
                        templateId TEXT,
                        type TEXT NOT NULL,
                        transactionKind TEXT NOT NULL DEFAULT 'STANDARD',
                        linkedTransactionId TEXT,
                        amountCents INTEGER NOT NULL,
                        bookingDate TEXT NOT NULL,
                        yearMonth TEXT NOT NULL,
                        note TEXT,
                        importHash TEXT,
                        payee TEXT,
                        importId TEXT,
                        PRIMARY KEY(id),
                        FOREIGN KEY(accountId) REFERENCES budget_account(id) ON UPDATE CASCADE ON DELETE RESTRICT,
                        FOREIGN KEY(categoryId) REFERENCES budget_category(id) ON UPDATE CASCADE ON DELETE SET NULL,
                        FOREIGN KEY(linkedTransactionId) REFERENCES budget_transaction_new(id) ON UPDATE CASCADE ON DELETE SET NULL,
                        FOREIGN KEY(templateId) REFERENCES budget_recurring_template(id) ON UPDATE CASCADE ON DELETE SET NULL
                    )
                    """);

            database.execSQL("""
                    INSERT INTO budget_transaction_new (
                        id,
                        accountId,
                        categoryId,
                        type,
                        transactionKind,
                        linkedTransactionId,
                        amountCents,
                        bookingDate,
                        yearMonth,
                        note,
                        importHash,
                        payee,
                        importId
                    )
                    SELECT
                        id,
                        accountId,
                        categoryId,
                        type,
                        'STANDARD',
                        NULL,
                        amountCents,
                        bookingDate,
                        yearMonth,
                        note,
                        importHash,
                        payee,
                        importId
                    FROM budget_transaction
                    """);

            database.execSQL("DROP TABLE budget_transaction");
            database.execSQL("ALTER TABLE budget_transaction_new RENAME TO budget_transaction");

            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_accountId ON budget_transaction(accountId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_categoryId ON budget_transaction(categoryId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_templateId ON budget_transaction(templateId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_yearMonth ON budget_transaction(yearMonth)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_bookingDate ON budget_transaction(bookingDate)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_importHash ON budget_transaction(importHash)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_budget_transaction_linkedTransactionId ON budget_transaction(linkedTransactionId)");
            database.execSQL("ALTER TABLE budget_category ADD COLUMN icon TEXT NOT NULL DEFAULT '🏷️'");
            database.execSQL("ALTER TABLE budget_category ADD COLUMN colorHex TEXT NOT NULL DEFAULT '#9E9E9E'");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_core ADD COLUMN goalIcon TEXT NOT NULL DEFAULT '" + TaskCore.DEFAULT_GOAL_ICON + "'");
            database.execSQL("ALTER TABLE task_core ADD COLUMN goalColorHex TEXT NOT NULL DEFAULT '" + TaskCore.DEFAULT_GOAL_COLOR_HEX + "'");
        }
    };


    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_core ADD COLUMN schedulingType TEXT NOT NULL DEFAULT 'TASK'");
            database.execSQL("ALTER TABLE task_core ADD COLUMN fixedDate TEXT");
            database.execSQL("ALTER TABLE task_core ADD COLUMN fixedStart TEXT");
            database.execSQL("ALTER TABLE task_core ADD COLUMN fixedEnd TEXT");
            database.execSQL("ALTER TABLE task_core ADD COLUMN fixedDuration INTEGER");
        }
    };

    private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_slots ADD COLUMN chainId TEXT");
        }
    };

    public abstract TaskDAO taskDao();

    public abstract TaskScheduleConfigDAO taskScheduleConfigDao();

    public abstract BudgetLookupDao budgetLookupDao();

    public abstract TransactionDao transactionDao();

    public abstract BudgetLimitDao budgetLimitDao();

    public abstract BudgetImportDao budgetImportDao();

    public abstract BudgetRecurringTemplateDao budgetRecurringTemplateDao();

    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, AppDatabase.class, "autosecretary.db")
                    .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
