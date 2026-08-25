package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "timer_sessions",
        foreignKeys = @ForeignKey(entity = OccurrenceStepEntity.class,
                parentColumns = "id", childColumns = "stepId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("stepId"))
public final class TimerSessionEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String stepId;
    @NonNull public String title;
    @NonNull public String kind;
    @NonNull public String state;
    public int totalSeconds;
    public long remainingMillis;
    public long targetElapsedRealtime;
    public long targetEpochMillis;
    public int notificationId;
    public boolean completionObserved;

    public TimerSessionEntity(@NonNull String id, @NonNull String stepId, @NonNull String title,
                              @NonNull String kind, @NonNull String state, int totalSeconds,
                              long remainingMillis, long targetElapsedRealtime,
                              long targetEpochMillis, int notificationId,
                              boolean completionObserved) {
        this.id = id; this.stepId = stepId; this.title = title; this.kind = kind;
        this.state = state; this.totalSeconds = totalSeconds;
        this.remainingMillis = remainingMillis;
        this.targetElapsedRealtime = targetElapsedRealtime;
        this.targetEpochMillis = targetEpochMillis;
        this.notificationId = notificationId;
        this.completionObserved = completionObserved;
    }
}
