package com.autosecretary.database;

import com.autosecretary.features.task.data.*;
import com.autosecretary.constants.Period;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.Database;
import androidx.room.TypeConverters;
import androidx.room.RoomDatabase;
import androidx.room.Room;
import android.util.Log;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import android.content.Context;

@Database (
        entities = {TaskPrefSlot.class, TaskRelation.class, TaskCore.class, TaskSlot.class, TaskPrerequisite.class},
        version = 7,
        exportSchema = false
    )
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    
    public abstract TaskDAO taskDao();

    //Singleton-Pattern
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context, AppDatabase.class, "autosecretary.db")
            .fallbackToDestructiveMigration().build();
        }
        return instance;
    }
}                         