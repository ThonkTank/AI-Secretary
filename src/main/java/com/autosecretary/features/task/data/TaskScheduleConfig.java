package com.autosecretary.features.task.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity(tableName = "task_schedule_config")
public class TaskScheduleConfig {

    @PrimaryKey
    public DayOfWeek dayOfWeek;

    public LocalTime startTime;

    public LocalTime endTime;

    public TaskScheduleConfig(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
