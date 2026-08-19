package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.domain.model.RepetitionProgress;

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

    public int valueFor(FocusStepUiModel step) {
        if (stepId != null && stepId.equals(step.id)) return value;
        return step.repetitionProgress == null ? 0
                : clamp(step.repetitionProgress.plannedRepetitions);
    }

    public int editingIndexFor(FocusStepUiModel step) {
        return stepId != null && stepId.equals(step.id) ? editingIndex : -1;
    }

    public RepetitionInputState adjust(FocusStepUiModel step, int delta) {
        return new RepetitionInputState(step.id, valueFor(step) + delta,
                editingIndexFor(step));
    }

    public RepetitionInputState edit(FocusStepUiModel step, int index) {
        if (step.repetitionProgress == null || index < 0
                || index >= step.repetitionProgress.actualRepetitions.size()) return this;
        return new RepetitionInputState(step.id,
                step.repetitionProgress.actualRepetitions.get(index), index);
    }

    public RepetitionInputState reconcile(@Nullable TaskSnapshot focus) {
        if (stepId == null) return this;
        if (focus != null) {
            for (FocusStepUiModel step : focus.steps) {
                if (step.done) continue;
                return stepId.equals(step.id) && step.repetitionProgress != null
                        ? this : idle();
            }
        }
        return idle();
    }

    private static int clamp(int value) { return RepetitionProgress.clampInput(value); }
}
