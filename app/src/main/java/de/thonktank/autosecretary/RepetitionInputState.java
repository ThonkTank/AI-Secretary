package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import java.util.List;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;

/** Immutable draft for the repetition stepper and an optional saved-slot correction. */
public final class RepetitionInputState {
    @Nullable public final String stepId;
    public final int value;
    public final int editingIndex;

    private RepetitionInputState(@Nullable String stepId, int value, int editingIndex) {
        this.stepId = stepId;
        this.value = clamp(value);
        this.editingIndex = editingIndex;
    }

    public static RepetitionInputState idle() {
        return new RepetitionInputState(null, 0, -1);
    }

    public int valueFor(TaskStepUiModel step) {
        if (stepId != null && stepId.equals(step.id)) return value;
        return step.repetitionProgress == null ? 0
                : clamp(step.repetitionProgress.plannedRepetitions);
    }

    public int editingIndexFor(TaskStepUiModel step) {
        return stepId != null && stepId.equals(step.id) ? editingIndex : -1;
    }

    public RepetitionInputState adjust(TaskStepUiModel step, int delta) {
        return new RepetitionInputState(step.id, valueFor(step) + delta,
                editingIndexFor(step));
    }

    public RepetitionInputState edit(TaskStepUiModel step, int index) {
        if (step.repetitionProgress == null || index < 0
                || index >= step.repetitionProgress.actualRepetitions.size()) return this;
        return new RepetitionInputState(step.id,
                step.repetitionProgress.actualRepetitions.get(index), index);
    }

    public RepetitionInputState reconcile(List<TaskSnapshot> tasks) {
        if (stepId == null) return this;
        for (TaskSnapshot task : tasks)
            for (TaskStepUiModel step : task.steps)
                if (stepId.equals(step.id) && !step.done) return this;
        return idle();
    }

    private static int clamp(int value) { return Math.max(0, Math.min(999, value)); }
}
