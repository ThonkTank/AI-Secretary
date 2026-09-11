package de.thonktank.autosecretary.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(tableName = "flow_run_steps",
        foreignKeys = @ForeignKey(entity = GraphRunEntity.class, parentColumns = "id",
                childColumns = "runId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("runId"), @Index(value = {"runId", "position"}, unique = true),
                @Index(value = {"runId", "sourceTemplateId"}, unique = true),
                @Index(value = {"state", "readyAtEpochMillis"})})
public final class GraphRunStepEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String runId = "";
    /** Stable snapshot ordering for rendering, never a runtime cursor. */
    public int position;
    @NonNull public String sourceTemplateId = "";
    @NonNull public String text = "";
    @NonNull public String amountKind = "";
    @Nullable public Integer plannedSets;
    @Nullable public Integer plannedReps;
    @Nullable public Integer plannedDurationSeconds;
    @NonNull public String restTimerMode = "";
    @Nullable public Integer restTimerSeconds;
    @NonNull public String plannedLoadMode = "";
    @NonNull public String plannedLoadUnit = "";
    @Nullable public Long plannedLoadMilli;
    public int targetRir;
    @NonNull public String note = "";
    @NonNull public String delayMode = "";
    public long defaultDelayMillis;
    @Nullable public Long lastUsedDelayMillis;
    @NonNull public String state = "";
    @Nullable public Long chosenDelayMillis;
    @Nullable public Long readyAtEpochMillis;
    @Nullable public Long actionAtEpochMillis;
    public long earnedTau;
}
