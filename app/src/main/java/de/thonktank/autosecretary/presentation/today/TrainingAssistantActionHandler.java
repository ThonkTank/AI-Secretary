package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.StringRes;

import java.math.BigDecimal;

import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.usecase.ResolveTrainingLoadRequest;
import de.thonktank.autosecretary.domain.usecase.TrainingUseCases;

/** Owns parsing, validation, use-case dispatch and result mapping for assistant-panel actions. */
public final class TrainingAssistantActionHandler {
    private final TrainingUseCases training;

    public TrainingAssistantActionHandler(TrainingUseCases training) {
        if (training == null) throw new IllegalArgumentException("Training use cases are required");
        this.training = training;
    }

    public Result handle(TrainingAssistantUiAction action) {
        if (action == null) throw new IllegalArgumentException("Training action is required");
        if (action instanceof TrainingAssistantUiAction.ApplyLoad)
            return applyLoad((TrainingAssistantUiAction.ApplyLoad) action);
        if (action instanceof TrainingAssistantUiAction.NoHigherLoad)
            return resolved(training.resolveTrainingLoadRequest.noHigherLoad(action.templateId));
        if (action instanceof TrainingAssistantUiAction.Later) {
            ResolveTrainingLoadRequest.Result result =
                    training.resolveTrainingLoadRequest.later(action.templateId);
            return result == ResolveTrainingLoadRequest.Result.DEFERRED
                    ? new Feedback(R.string.training_request_deferred)
                    : new Rejected(R.string.training_request_no_longer_open);
        }
        if (action instanceof TrainingAssistantUiAction.Undo)
            return training.undoLatestTrainingAdjustment.execute(action.templateId)
                    ? Completed.INSTANCE
                    : new Rejected(R.string.training_undo_no_longer_available);
        throw new IllegalArgumentException("Unsupported training action");
    }

    private Result applyLoad(TrainingAssistantUiAction.ApplyLoad action) {
        Long milliUnits = parseMilliUnits(action.rawLoad);
        if (milliUnits == null || milliUnits <= 0 || !adjustable(action.currentMode)
                || action.currentUnit == ResistanceLoad.Unit.NONE)
            return new Rejected(R.string.training_load_invalid);
        ResistanceLoad answer = ResistanceLoad.numeric(
                action.currentMode, action.currentUnit, milliUnits);
        return resolved(training.resolveTrainingLoadRequest.applyConcreteLoad(
                action.templateId, answer));
    }

    private static Long parseMilliUnits(String rawLoad) {
        if (rawLoad == null || rawLoad.trim().isEmpty()) return null;
        try {
            return new BigDecimal(rawLoad.trim().replace(',', '.'))
                    .movePointRight(3).longValueExact();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean adjustable(ResistanceLoad.Mode mode) {
        return mode == ResistanceLoad.Mode.EXTERNAL
                || mode == ResistanceLoad.Mode.BODYWEIGHT_PLUS
                || mode == ResistanceLoad.Mode.ASSISTED_BODYWEIGHT;
    }

    private static Result resolved(ResolveTrainingLoadRequest.Result result) {
        switch (result) {
            case APPLIED:
            case SETS_ADDED:
            case HELD:
                return Completed.INSTANCE;
            case INVALID_LOAD:
                return new Rejected(R.string.training_load_invalid);
            case WRONG_DIRECTION:
                return new Rejected(R.string.training_load_wrong_direction);
            case JUMP_TOO_LARGE:
                return new Rejected(R.string.training_load_jump_too_large);
            case NO_OPEN_REQUEST:
            case DEFERRED:
            default:
                return new Rejected(R.string.training_request_no_longer_open);
        }
    }

    public interface Result { }

    public static final class Completed implements Result {
        private static final Completed INSTANCE = new Completed();
        private Completed() { }
    }

    public static final class Feedback implements Result {
        @StringRes public final int message;
        public Feedback(@StringRes int message) { this.message = message; }
    }

    public static final class Rejected implements Result {
        @StringRes public final int message;
        public Rejected(@StringRes int message) { this.message = message; }
    }
}
