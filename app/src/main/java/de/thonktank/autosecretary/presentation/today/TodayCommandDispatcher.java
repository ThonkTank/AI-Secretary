package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

/** Exhaustive adapter from closed Today commands to focused application handlers. */
public final class TodayCommandDispatcher implements TodayCoordinator.CommandSink {
    public interface Handlers {
        void handleCompleteOccurrence(String occurrenceId);
        void handleRequestClose(String taskId, String title);
        void handleCompleteRemaining(String occurrenceId);
        void handleHarvest(String occurrenceId);
        void handleDefer(String occurrenceId);
        void handleToggleStep(String stepId);
        void handleAdvanceStep(String stepId);
        void handleUndoOccurrence(String occurrenceId);
        void handleAdjustRepetition(String stepId, int delta);
        void handleEditRepetition(String stepId, int index);
        void handleSubmitRepetition(String stepId);
        void handlePersistReorder(String commandId, String stepId,
                            @Nullable String beforeStepId);
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
            case ADVANCE_STEP:
                handlers.handleAdvanceStep(command.id);
                return;
            case UNDO_OCCURRENCE:
                handlers.handleUndoOccurrence(command.id);
                return;
            case ADJUST_REPETITION:
                handlers.handleAdjustRepetition(command.id, command.value);
                return;
            case EDIT_REPETITION:
                handlers.handleEditRepetition(command.id, command.value);
                return;
            case SUBMIT_REPETITION:
                handlers.handleSubmitRepetition(command.id);
                return;
            case PERSIST_REORDER:
                handlers.handlePersistReorder(required(command.commandId), command.id,
                        command.relatedId);
                return;
        }
        throw new AssertionError("Unhandled Today command " + command.kind);
    }

    private static String required(@Nullable String value) {
        if (value == null || value.isEmpty())
            throw new IllegalArgumentException("Reorder command id is required");
        return value;
    }
}
