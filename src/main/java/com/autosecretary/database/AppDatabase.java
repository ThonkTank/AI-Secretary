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
import com.autosecretary.features.task.data.TaskSlot;

@Database(
        entities = {
                TaskPrefSlot.class,
                TaskRelation.class,
                TaskCore.class,
                TaskSlot.class,
                TaskPrerequisite.class,
                BudgetAccount.class,
                BudgetCategory.class,
                BudgetTransactionEntity.class,
                BudgetLimit.class,
                BudgetImportEntity.class,
                BudgetRecurringTemplateEntity.class
        },
        version = 11,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS budget_transaction_new (
                        id TEXT NOT NULL,
                        accountId TEXT NOT NULL,
                        categoryId TEXT,
                        templateId TEXT,
                        type TEXT NOT NULL,
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
                        FOREIGN KEY(templateId) REFERENCES budget_recurring_template(id) ON UPDATE CASCADE ON DELETE SET NULL
                    )
                    """);

            database.execSQL("""
                    INSERT INTO budget_transaction_new (
                        id,
                        accountId,
                        categoryId,
                        type,
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
        }
    };

    public abstract TaskDAO taskDao();

    public abstract BudgetLookupDao budgetLookupDao();

    public abstract TransactionDao transactionDao();

    public abstract BudgetLimitDao budgetLimitDao();

    public abstract BudgetImportDao budgetImportDao();

    public abstract BudgetRecurringTemplateDao budgetRecurringTemplateDao();

    // Singleton-Pattern
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, AppDatabase.class, "autosecretary.db")
                    .addMigrations(MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
