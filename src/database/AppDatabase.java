package database;

import database.task.*;
import constants.Period;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.Database;
import androidx.room.TypeConverters;
import androidx.room.RoomDatabase;
import androidx.room.Room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import android.content.Context;

@Database (
        entities = {TaskPrefSlot.class, TaskRelation.class, TaskCore.class, TaskFollowUp.class, TaskSlot.class},
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
            instance = Room.databaseBuilder(context, AppDatabase.class, "autosecretary.db")
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(SupportSQLiteDatabase db) {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            TaskDAO dao = instance.taskDao();
                            List<Task> newTasks = new ArrayList<>();
                            newTasks.add(new Task("Täglich", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 15));
                            newTasks.add(new Task("overdue", 1, 1, Period.DAY, LocalDate.now().minusDays(7), 1, LocalTime.of(9, 0), 1));
                            newTasks.add(new Task("Cooldown", 1, 1, Period.WEEK, null, 7, null, 15));
                            Task parent = new Task("Parent", 1, 1, Period.MONTH, null, 1, LocalTime.of(6, 0), 30);
                            newTasks.add(parent);
                            parent.children.add(new Task("Child A", 3, 5, Period.DAY, null, 1, LocalTime.of(6, 0), 15));
                            parent.children.add(new Task("Child B", 1, 4, Period.DAY, null, 1, LocalTime.of(6, 0), 5));
                            Task childC = new Task("Child C", 1, 1, Period.WEEK, null, 1, LocalTime.of(6, 0), 15);
                            parent.children.add(childC);
                            childC.children.add(new Task("Grandchild", 1, 1, Period.WEEK, null, 1, LocalTime.of(6, 0), 20));
                            dao.writeList(newTasks);
                        });
                    }
                })
            .build();
        }
        return instance;
    }
}                         