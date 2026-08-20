package de.thonktank.autosecretary;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {TaskEntity.class, TaskStepEntity.class, OccurrenceEntity.class,
        OccurrenceStepEntity.class, StatsEntity.class, ComboEntity.class,
        RewardBookingEntity.class, RepetitionResultEntity.class, TaskScheduleEntity.class}, version = 12,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TaskDao tasks();
}
