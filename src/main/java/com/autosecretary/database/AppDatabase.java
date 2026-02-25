package com.autosecretary.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetCategory;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetImportDao;
import com.autosecretary.features.budget.data.BudgetImportEntity;
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
        version = 10,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

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
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
