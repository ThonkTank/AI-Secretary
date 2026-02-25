package com.autosecretary.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.autosecretary.features.budget.data.Account;
import com.autosecretary.features.budget.data.BudgetDao;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.Category;
import com.autosecretary.features.budget.data.Import;
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
                Account.class,
                Category.class,
                com.autosecretary.features.budget.data.Transaction.class,
                BudgetLimit.class,
                Import.class
        },
        version = 8,
        exportSchema = false
)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TaskDAO taskDao();

    public abstract BudgetDao budgetDao();

    //Singleton-Pattern
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
