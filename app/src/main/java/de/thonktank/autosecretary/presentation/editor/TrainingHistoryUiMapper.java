package de.thonktank.autosecretary.presentation.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.StepAmount;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.model.TrainingDecision;
import de.thonktank.autosecretary.domain.model.TrainingHistoryEntry;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.presentation.UiTextProvider;

/** Localizes the canonical training audit only for the task editor. */
public final class TrainingHistoryUiMapper {
    private final UiTextProvider texts;

    public TrainingHistoryUiMapper(UiTextProvider texts) {
        if (texts == null) throw new IllegalArgumentException("UI texts are required");
        this.texts = texts;
    }

    public TrainingHistoryUiModel map(TrainingContext value) {
        if (value == null) return null;
        List<String> entries = new ArrayList<>();
        int limit = Math.min(10, value.history.size());
        for (int index = 0; index < limit; index++)
            entries.add(history(value.history.get(index)));
        return new TrainingHistoryUiModel(value.templateId, entries, value.canUndo);
    }

    private String history(TrainingHistoryEntry value) {
        if (value.kind == TrainingHistoryEntry.Kind.ADJUSTMENT) {
            String label = reason(value.reason);
            String change = prescription(value.before, value.beforeLoad) + " → "
                    + prescription(value.after, value.afterLoad);
            if (value.adjustmentState == TrainingAdjustment.State.UNDONE)
                return texts.text(R.string.training_history_undone, label, change);
            return texts.text(R.string.training_history_applied, label, change);
        }
        String direction = value.loadDirection == TrainingDecision.LoadDirection.PROGRESS
                ? texts.text(R.string.training_direction_higher)
                : texts.text(R.string.training_direction_lower);
        String state;
        if (value.requestState == TrainingLoadRequest.State.OPEN)
            state = texts.text(R.string.training_request_open);
        else if (value.requestResolution == TrainingLoadRequest.Resolution.LOAD_APPLIED)
            state = texts.text(R.string.training_request_load_applied);
        else if (value.requestResolution == TrainingLoadRequest.Resolution.NO_HIGHER_LOAD)
            state = texts.text(R.string.training_request_unavailable);
        else if (value.requestResolution == TrainingLoadRequest.Resolution.MANUAL_CHANGE)
            state = texts.text(R.string.training_request_manual_change);
        else if (value.requestResolution
                == TrainingLoadRequest.Resolution.SET_RESULT_CORRECTED)
            state = texts.text(R.string.training_request_result_corrected);
        else if (value.requestResolution == TrainingLoadRequest.Resolution.UNDONE)
            state = texts.text(R.string.training_request_undone);
        else state = texts.text(R.string.training_request_cancelled);
        return texts.text(R.string.training_history_request, direction,
                formatLoad(value.beforeLoad), state);
    }

    private String prescription(StepAmount.SetsReps amount, ResistanceLoad load) {
        return texts.text(R.string.training_prescription_value, amount.sets,
                amount.repetitions, formatLoad(load));
    }

    private String formatLoad(ResistanceLoad load) {
        if (load.mode == ResistanceLoad.Mode.BODYWEIGHT)
            return texts.text(R.string.training_load_bodyweight_short);
        if (load.mode == ResistanceLoad.Mode.UNSPECIFIED) return "–";
        double amount = (load.milliUnits == null ? 0L : load.milliUnits) / 1000d;
        String prefix = load.mode == ResistanceLoad.Mode.BODYWEIGHT_PLUS ? "+"
                : load.mode == ResistanceLoad.Mode.ASSISTED_BODYWEIGHT ? "−" : "";
        return prefix + String.format(Locale.getDefault(), "%.1f %s", amount,
                load.unit == ResistanceLoad.Unit.LB ? "lb" : "kg");
    }

    private String reason(TrainingDecision.Reason value) {
        switch (value) {
            case REPETITIONS_INCREASED: return texts.text(R.string.training_reason_reps_up);
            case LOAD_APPLIED: return texts.text(R.string.training_reason_load_applied);
            case SET_ADDED: return texts.text(R.string.training_reason_set_added);
            case REPETITIONS_REDUCED: return texts.text(R.string.training_reason_reps_down);
            case SET_REMOVED: return texts.text(R.string.training_reason_set_removed);
            case SAFETY_PAUSE: return texts.text(R.string.training_reason_safety_pause);
            case VOLUME_LIMIT: return texts.text(R.string.training_reason_volume_limit);
            case BOUNDARY_REACHED: return texts.text(R.string.training_reason_boundary);
            case CALIBRATING: return texts.text(R.string.training_reason_calibrating);
            case MANUAL_CHANGE: return texts.text(R.string.training_reason_manual);
            case SET_RESULT_CORRECTED: return texts.text(R.string.training_reason_corrected);
            case UNDONE: return texts.text(R.string.training_reason_undone);
            case NEXT_LOAD_REQUIRED: return texts.text(R.string.training_direction_higher);
            case LOWER_LOAD_REQUIRED: return texts.text(R.string.training_direction_lower);
            case NONE:
            default: return texts.text(R.string.training_reason_held);
        }
    }
}
