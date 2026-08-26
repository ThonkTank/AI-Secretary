package de.thonktank.autosecretary.presentation.today;


/** Exhaustive adapter from closed Today commands to focused application handlers. */
public final class TodayCommandDispatcher implements TodayCoordinator.CommandSink {
    public interface Handlers {
        void handleCompleteOccurrence(String occurrenceId);
        void handleRequestClose(String taskId, String title);
        void handleCompleteRemaining(String occurrenceId);
        void handleHarvest(String occurrenceId);
        void handleDefer(String occurrenceId);
        void handleToggleStep(String stepId);
        default void handleToggleStepWithDelay(String stepId, long chosenDelayMillis) {
            handleToggleStep(stepId);
        }
        void handleFinishStep(String stepId);
        void handleAdvanceStep(String stepId);
        void handleUndoOccurrence(String occurrenceId);
        void handleAdjustRepetition(String stepId, int delta);
        default void handleAdjustTrainingLoad(String stepId, int milliUnitDelta) { }
        default void handleAdjustTrainingRir(String stepId, int delta) { }
        default void handleToggleTrainingSafety(String stepId) { }
        void handleEditRepetition(String stepId, int index);
        void handleSubmitRepetition(String stepId);
        void handleStartDurationTimer(String stepId, String title, int seconds);
        void handlePauseTimer(String timerId);
        void handleResumeTimer(String timerId);
        void handleResetTimer(String timerId);
        void handleObserveTimer(String timerId);
        void handlePersistReorder(String commandId, String stepId,
                            String beforeStepId);
    }

    private final Handlers handlers;

    public TodayCommandDispatcher(Handlers handlers) {
        if (handlers == null) throw new IllegalArgumentException("Handlers are required");
        this.handlers = handlers;
    }

    @Override public void execute(TodayCommand command) {
        if (command == null) throw new IllegalArgumentException("Today command is required");
        switch (command.kind) {
            case COMPLETE_OCCURRENCE:
                handlers.handleCompleteOccurrence(command.id);
                return;
            case REQUEST_CLOSE:
                handlers.handleRequestClose(command.id,
                        command.text == null ? "" : command.text);
                return;
            case COMPLETE_REMAINING:
                handlers.handleCompleteRemaining(command.id);
                return;
            case HARVEST:
                handlers.handleHarvest(command.id);
                return;
            case DEFER:
                handlers.handleDefer(command.id);
                return;
            case TOGGLE_STEP:
                handlers.handleToggleStep(command.id);
                return;
            case TOGGLE_STEP_WITH_DELAY:
                handlers.handleToggleStepWithDelay(command.id, command.longValue);
                return;
            case FINISH_STEP:
                handlers.handleFinishStep(command.id);
                return;
            case ADVANCE_STEP:
                handlers.handleAdvanceStep(command.id);
                return;
            case UNDO_OCCURRENCE:
                handlers.handleUndoOccurrence(command.id);
                return;
            case ADJUST_REPETITION:
                handlers.handleAdjustRepetition(command.id, command.value);
                return;
            case ADJUST_TRAINING_LOAD:
                handlers.handleAdjustTrainingLoad(command.id, command.value);
                return;
            case ADJUST_TRAINING_RIR:
                handlers.handleAdjustTrainingRir(command.id, command.value);
                return;
            case TOGGLE_TRAINING_SAFETY:
                handlers.handleToggleTrainingSafety(command.id);
                return;
            case EDIT_REPETITION:
                handlers.handleEditRepetition(command.id, command.value);
                return;
            case SUBMIT_REPETITION:
                handlers.handleSubmitRepetition(command.id);
                return;
            case START_DURATION_TIMER:
                handlers.handleStartDurationTimer(command.id,
                        command.text == null ? "" : command.text, command.value);
                return;
            case PAUSE_TIMER:
                handlers.handlePauseTimer(command.id);
                return;
            case RESUME_TIMER:
                handlers.handleResumeTimer(command.id);
                return;
            case RESET_TIMER:
                handlers.handleResetTimer(command.id);
                return;
            case OBSERVE_TIMER:
                handlers.handleObserveTimer(command.id);
                return;
            case PERSIST_REORDER:
                handlers.handlePersistReorder(required(command.commandId), command.id,
                        command.relatedId);
                return;
        }
        throw new AssertionError("Unhandled Today command " + command.kind);
    }

    private static String required(String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("Reorder command id is required");
        return value;
    }
}
