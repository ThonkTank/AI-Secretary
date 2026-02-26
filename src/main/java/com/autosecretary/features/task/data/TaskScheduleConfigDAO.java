package com.autosecretary.features.task.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TaskScheduleConfigDAO {

    @Query("SELECT * FROM task_schedule_config")
    List<TaskScheduleConfig> readAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void writeAll(List<TaskScheduleConfig> configs);
}
