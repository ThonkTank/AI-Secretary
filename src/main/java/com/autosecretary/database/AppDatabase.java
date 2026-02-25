package com.autosecretary.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetLimitDao;
import com.autosecretary.features.budget.data.BudgetLookupDao;
import com.autosecretary.features.budget.data.BudgetTransaction;
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
                BudgetTransaction.class,
                BudgetLimit.class
        },
        version = 8,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TaskDAO taskDao();

    public abstract BudgetLookupDao budgetLookupDao();

    public abstract TransactionDao transactionDao();

    public abstract BudgetLimitDao budgetLimitDao();

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `budget_account` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `archived` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """);
            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `budget_category` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `archived` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """);
            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `budget_transaction` (
                        `id` TEXT NOT NULL,
                        `accountId` TEXT NOT NULL,
                        `categoryId` TEXT,
                        `type` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `bookingDate` TEXT NOT NULL,
                        `yearMonth` TEXT NOT NULL,
                        `note` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`accountId`) REFERENCES `budget_account`(`id`) ON UPDATE CASCADE ON DELETE RESTRICT,
                        FOREIGN KEY(`categoryId`) REFERENCES `budget_category`(`id`) ON UPDATE CASCADE ON DELETE SET NULL
                    )
                    """);
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_transaction_accountId` ON `budget_transaction` (`accountId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_transaction_categoryId` ON `budget_transaction` (`categoryId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_transaction_yearMonth` ON `budget_transaction` (`yearMonth`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_transaction_bookingDate` ON `budget_transaction` (`bookingDate`)");
            database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `budget_limit` (
                        `id` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `yearMonth` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`categoryId`) REFERENCES `budget_category`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """);
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_limit_categoryId` ON `budget_limit` (`categoryId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_limit_yearMonth` ON `budget_limit` (`yearMonth`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_limit_categoryId_yearMonth` ON `budget_limit` (`categoryId`, `yearMonth`)");
        }
    };

    // Singleton-Pattern
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, AppDatabase.class, "autosecretary.db")
                    .addMigrations(MIGRATION_7_8)
                    .build();
        }
        return instance;
    }
}
