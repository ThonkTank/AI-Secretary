package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks", indices = @Index(value = {"archived", "conditionDone", "catalogOrder"}))
public class TaskEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String title;
    @NonNull public String recurrence;
    public int intervalDays;
    public int weekdayMask;
    public boolean ongoing;
    @NonNull public String conditionText;
    public boolean conditionDone;
    public boolean archived;
    @NonNull public String nextDueOn;
    @Nullable public String cadenceAnchorOn;
    @Nullable public String lastScheduledOn;
    @Nullable public String lastCompletedOn;
    public long catalogOrder;
    public boolean hasCompletedOccurrence;
    public Integer estimatedMinutes;
    @NonNull public String boundKind;
    @Nullable public String boundUntilOn;
    public Integer boundWeeks;
    public Integer remainingCount;
    @Nullable public String deadlineOn;
    @NonNull public String note;

    public TaskEntity(@NonNull String id, @NonNull String title,
                      @NonNull String recurrence, int intervalDays, int weekdayMask,
                      boolean ongoing, @NonNull String conditionText, boolean conditionDone,
                      boolean archived, @NonNull String nextDueOn,
                      @Nullable String cadenceAnchorOn,
                      @Nullable String lastScheduledOn, @Nullable String lastCompletedOn,
                      long catalogOrder, boolean hasCompletedOccurrence,
                      Integer estimatedMinutes,
                      @NonNull String boundKind, @Nullable String boundUntilOn,
                      Integer boundWeeks, Integer remainingCount,
                      @Nullable String deadlineOn, @NonNull String note) {
        this.id = id;
        this.title = title;
        this.recurrence = recurrence;
        this.intervalDays = intervalDays;
        this.weekdayMask = weekdayMask;
        this.ongoing = ongoing;
        this.conditionText = conditionText;
        this.conditionDone = conditionDone;
        this.archived = archived;
        this.nextDueOn = nextDueOn;
        this.cadenceAnchorOn = cadenceAnchorOn;
        this.lastScheduledOn = lastScheduledOn;
        this.lastCompletedOn = lastCompletedOn;
        this.catalogOrder = catalogOrder;
        this.hasCompletedOccurrence = hasCompletedOccurrence;
        this.estimatedMinutes = estimatedMinutes;
        this.boundKind = boundKind;
        this.boundUntilOn = boundUntilOn;
        this.boundWeeks = boundWeeks;
        this.remainingCount = remainingCount;
        this.deadlineOn = deadlineOn;
        this.note = note;
    }
}
