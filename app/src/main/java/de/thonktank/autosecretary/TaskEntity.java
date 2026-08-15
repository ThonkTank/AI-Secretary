package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class TaskEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String title;
    @NonNull public String slot;
    @NonNull public String recurrence; // ONCE, DAILY, INTERVAL, WEEKDAYS
    public int intervalDays;
    public int weekdayMask; // ISO weekday bit: Monday = 1 << 0
    public boolean ongoing;
    @NonNull public String conditionText;
    public boolean conditionDone;
    public boolean archived;
    @NonNull public String nextDueOn;
    @NonNull public String lastScheduledOn;
    @NonNull public String lastCompletedOn;
    public int routineLevel;
    public int routineStreak;
    public boolean hasCompletedOccurrence;

    public TaskEntity(@NonNull String id, @NonNull String title, @NonNull String slot, @NonNull String recurrence,
                      int intervalDays, int weekdayMask, boolean ongoing, @NonNull String conditionText,
                      boolean conditionDone, boolean archived, @NonNull String nextDueOn,
                      @NonNull String lastScheduledOn, @NonNull String lastCompletedOn, int routineLevel, int routineStreak,
                      boolean hasCompletedOccurrence) {
        this.id = id; this.title = title; this.slot = slot; this.recurrence = recurrence;
        this.intervalDays = intervalDays; this.weekdayMask = weekdayMask; this.ongoing = ongoing;
        this.conditionText = conditionText; this.conditionDone = conditionDone; this.archived = archived;
        this.nextDueOn = nextDueOn; this.lastScheduledOn = lastScheduledOn; this.lastCompletedOn = lastCompletedOn;
        this.routineLevel = routineLevel; this.routineStreak = routineStreak;
        this.hasCompletedOccurrence = hasCompletedOccurrence;
    }
}
