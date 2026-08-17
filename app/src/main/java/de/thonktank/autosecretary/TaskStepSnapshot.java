package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TaskStepSnapshot {
    @NonNull public final String id;
    @NonNull public final String label;
    public final boolean done;
    @NonNull public final StepAmountKind amountKind;
    public final Integer plannedSets;
    public final Integer plannedReps;
    public final Integer plannedDurationSeconds;
    @NonNull public final String note;
    @NonNull public final List<Integer> actualRepetitions;

    public TaskStepSnapshot(@NonNull String id, @NonNull String label, boolean done) {
        this(id, label, done, StepAmountKind.NONE, null, null, null, "",
                Collections.emptyList());
    }

    public TaskStepSnapshot(@NonNull String id, @NonNull String label, boolean done,
                            @NonNull StepAmountKind amountKind, Integer plannedSets,
                            Integer plannedReps, Integer plannedDurationSeconds,
                            @NonNull String note, @NonNull List<Integer> actualRepetitions) {
        this.id = id; this.label = label; this.done = done;
        this.amountKind = amountKind; this.plannedSets = plannedSets;
        this.plannedReps = plannedReps; this.plannedDurationSeconds = plannedDurationSeconds;
        this.note = note;
        this.actualRepetitions = Collections.unmodifiableList(new ArrayList<>(actualRepetitions));
    }

    public int nextSetNumber() { return actualRepetitions.size() + 1; }
}
