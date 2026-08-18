package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks", indices = @Index(value = {"archived", "conditionDone", "displayOrder"}))
public class TaskEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String title;
    @NonNull public String slot;
    @NonNull public String recurrence;
    public int intervalDays;
    public int weekdayMask;
    public boolean ongoing;
    @NonNull public String conditionText;
    public boolean conditionDone;
    public boolean archived;
    @NonNull public String nextDueOn;
    @NonNull public String lastScheduledOn;
    @NonNull public String lastCompletedOn;
    public long displayOrder;
    public boolean hasCompletedOccurrence;
    public Integer estimatedMinutes;
    public int timeOfDayMask;
    @NonNull public String boundKind;
    @NonNull public String boundUntilOn;
    public Integer boundWeeks;
    public Integer remainingCount;
    @NonNull public String deadlineOn;
    @NonNull public String note;

    public TaskEntity(@NonNull String id, @NonNull String title, @NonNull String slot,
                      @NonNull String recurrence, int intervalDays, int weekdayMask,
                      boolean ongoing, @NonNull String conditionText, boolean conditionDone,
                      boolean archived, @NonNull String nextDueOn,
                      @NonNull String lastScheduledOn, @NonNull String lastCompletedOn,
                      long displayOrder, boolean hasCompletedOccurrence,
                      Integer estimatedMinutes, int timeOfDayMask,
                      @NonNull String boundKind, @NonNull String boundUntilOn,
                      Integer boundWeeks, Integer remainingCount,
                      @NonNull String deadlineOn, @NonNull String note) {
        this.id = id;
        this.title = title;
        this.slot = slot;
        this.recurrence = recurrence;
        this.intervalDays = intervalDays;
        this.weekdayMask = weekdayMask;
        this.ongoing = ongoing;
        this.conditionText = conditionText;
        this.conditionDone = conditionDone;
        this.archived = archived;
        this.nextDueOn = nextDueOn;
        this.lastScheduledOn = lastScheduledOn;
        this.lastCompletedOn = lastCompletedOn;
        this.displayOrder = displayOrder;
        this.hasCompletedOccurrence = hasCompletedOccurrence;
        this.estimatedMinutes = estimatedMinutes;
        this.timeOfDayMask = timeOfDayMask;
        this.boundKind = boundKind;
        this.boundUntilOn = boundUntilOn;
        this.boundWeeks = boundWeeks;
        this.remainingCount = remainingCount;
        this.deadlineOn = deadlineOn;
        this.note = note;
    }
}
