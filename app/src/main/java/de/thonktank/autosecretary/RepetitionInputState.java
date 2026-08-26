package de.thonktank.autosecretary;

import androidx.annotation.Nullable;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.domain.model.RepetitionProgress;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;

/** Immutable draft for the repetition stepper and an optional saved-slot correction. */
public final class RepetitionInputState {
    @Nullable public final String stepId;
    public final int value;
    public final int editingIndex;
    public final ResistanceLoad load;
    public final int rir;
    public final boolean safetyFlag;

    private RepetitionInputState(@Nullable String stepId, int value, int editingIndex,
                                 ResistanceLoad load, int rir, boolean safetyFlag) {
        this.stepId = stepId;
        this.value = clamp(value);
        this.editingIndex = editingIndex;
        this.load = load == null ? ResistanceLoad.unspecified() : load;
        this.rir = Math.max(0, Math.min(5, rir));
        this.safetyFlag = safetyFlag;
    }

    public static RepetitionInputState idle() {
        return new RepetitionInputState(null, 0, -1, ResistanceLoad.unspecified(), 2, false);
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
        return active(step, valueFor(step) + delta, editingIndexFor(step), loadFor(step),
                rirFor(step), safetyFor(step));
    }

    public RepetitionInputState edit(FocusStepUiModel step, int index) {
        if (step.repetitionProgress == null || index < 0
                || index >= step.repetitionProgress.actualRepetitions.size()) return this;
        return active(step, step.repetitionProgress.actualRepetitions.get(index), index,
                loadFor(step), rirFor(step), safetyFor(step));
    }

    public ResistanceLoad loadFor(FocusStepUiModel step) {
        return stepId != null && stepId.equals(step.id) ? load
                : step.repetitionProgress == null ? ResistanceLoad.unspecified()
                : step.repetitionProgress.plannedLoad;
    }

    public int rirFor(FocusStepUiModel step) {
        return stepId != null && stepId.equals(step.id) ? rir
                : step.repetitionProgress == null ? 2 : step.repetitionProgress.targetRir;
    }

    public boolean safetyFor(FocusStepUiModel step) {
        return stepId != null && stepId.equals(step.id) && safetyFlag;
    }

    public RepetitionInputState adjustLoad(FocusStepUiModel step, long delta) {
        ResistanceLoad current = loadFor(step);
        if (!current.adjustable()) return this;
        long value = Math.max(0, (current.milliUnits == null ? 0 : current.milliUnits) + delta);
        return active(step, valueFor(step), editingIndexFor(step),
                ResistanceLoad.numeric(current.mode, current.unit, value), rirFor(step), safetyFor(step));
    }

    public RepetitionInputState adjustRir(FocusStepUiModel step, int delta) {
        return active(step, valueFor(step), editingIndexFor(step), loadFor(step),
                rirFor(step) + delta, safetyFor(step));
    }

    public RepetitionInputState toggleSafety(FocusStepUiModel step) {
        return active(step, valueFor(step), editingIndexFor(step), loadFor(step),
                rirFor(step), !safetyFor(step));
    }

    private static RepetitionInputState active(FocusStepUiModel step, int value, int index,
                                               ResistanceLoad load, int rir, boolean safety) {
        return new RepetitionInputState(step.id, value, index, load, rir, safety);
    }

    public RepetitionInputState reconcile(@Nullable FocusTaskUiModel focus) {
        if (stepId == null) return this;
        if (focus != null) {
            for (FocusStepUiModel step : focus.steps) {
                if (step.isDone()) continue;
                return stepId.equals(step.id) && step.repetitionProgress != null
                        ? this : idle();
            }
        }
        return idle();
    }

    private static int clamp(int value) { return RepetitionProgress.clampInput(value); }
}
