package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.ComboEntity;
import de.thonktank.autosecretary.data.local.OccurrenceEntity;
import de.thonktank.autosecretary.data.local.OccurrenceStepEntity;
import de.thonktank.autosecretary.data.local.RepetitionResultEntity;
import de.thonktank.autosecretary.data.local.RewardAssignmentEntity;
import de.thonktank.autosecretary.data.local.RewardBookingEntity;
import de.thonktank.autosecretary.data.local.StatsEntity;
import de.thonktank.autosecretary.data.local.TaskDao;
import de.thonktank.autosecretary.data.local.TaskEntity;
import de.thonktank.autosecretary.data.local.TaskScheduleEntity;
import de.thonktank.autosecretary.data.local.TaskStepEntity;
import de.thonktank.autosecretary.data.local.TimerSessionEntity;
import de.thonktank.autosecretary.data.local.TimerSessionDao;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {TaskEntity.class, TaskStepEntity.class, OccurrenceEntity.class,
        OccurrenceStepEntity.class, StatsEntity.class, ComboEntity.class,
        RewardBookingEntity.class, RewardAssignmentEntity.class,
        RepetitionResultEntity.class, TaskScheduleEntity.class, TimerSessionEntity.class},
        version = DatabaseContract.VERSION,
        exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    public abstract TaskDao tasks();
    public abstract TimerSessionDao timers();
}
