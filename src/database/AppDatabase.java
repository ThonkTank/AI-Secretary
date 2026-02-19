package database;

import database.task.*;
import androidx.room.Database;
import androidx.room.TypeConverters;
import androidx.room.RoomDatabase;
import androidx.room.Room;
import android.content.Context;

@Database (
        entities = {TaskPrefSlot.class, TaskBlockedDay.class, TaskCore.class, TaskFollowUp.class, TaskSlot.class},
        version = 1,
        exportSchema = false
    )
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    
    public abstract TaskDAO taskDao();

    //Singleton-Pattern
    private static AppDatabase instance;

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                "autosecretary.db"
            ).build();
        }
        return instance;
    }
}   
