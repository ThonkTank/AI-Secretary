package de.thonktank.autosecretary.presentation.today;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;

/** Closed dispatcher and state owner for the Today feature. */
public final class TodayCoordinator implements TodayActionSink {
    @FunctionalInterface public interface CommandSink {
        void execute(TodayCommand command);
    }

    @FunctionalInterface public interface StateSink {
        void publish(TodayFeatureState state);
    }

    @FunctionalInterface public interface CommandIds {
        String next();
    }

    private final TodayReducer reducer;
    private final CommandSink commands;
    private final StateSink states;
    private final CommandIds commandIds;
    private TodayFeatureState state;

    public TodayCoordinator(TodayUiModel initial, CommandSink commands, StateSink states) {
        AtomicLong sequence = new AtomicLong();
        this.reducer = new TodayReducer();
        this.commands = required(commands);
        this.states = required(states);
        this.commandIds = () -> "today-reorder-" + sequence.incrementAndGet();
        this.state = TodayFeatureState.idle(initial);
    }

    TodayCoordinator(TodayUiModel initial, TodayReducer reducer, CommandSink commands,
                     StateSink states, CommandIds commandIds) {
        this.reducer = required(reducer);
        this.commands = required(commands);
        this.states = required(states);
        this.commandIds = required(commandIds);
        this.state = TodayFeatureState.idle(initial);
    }

    public synchronized TodayFeatureState state() { return state; }

    @Override public synchronized void emit(TodayAction action) {
        if (action == null) throw new IllegalArgumentException("Today action is required");
        switch (action.kind) {
            case SELECT_STEP:
                apply(reducer.select(state, action.id));
                return;
            case BEGIN_REORDER:
                apply(reducer.begin(state, action.id, action.order));
                return;
            case PREVIEW_REORDER:
                apply(reducer.preview(state, action.id, action.order));
                return;
            case CANCEL_REORDER:
                apply(reducer.cancel(state, action.id));
                return;
            case DROP_REORDER:
                apply(reducer.drop(state, action.id, action.relatedId, commandIds.next()));
                return;
            case MOVE_STEP:
                moveImmediately(action.id, action.relatedId);
                return;
            case COMPLETE_OCCURRENCE:
                commands.execute(TodayCommand.action(TodayCommand.Kind.COMPLETE_OCCURRENCE,
                        action));
                return;
            case REQUEST_CLOSE:
                commands.execute(TodayCommand.action(TodayCommand.Kind.REQUEST_CLOSE, action));
                return;
            case COMPLETE_REMAINING:
                commands.execute(TodayCommand.action(TodayCommand.Kind.COMPLETE_REMAINING,
                        action));
                return;
            case HARVEST:
                commands.execute(TodayCommand.action(TodayCommand.Kind.HARVEST, action));
                return;
            case DEFER:
                commands.execute(TodayCommand.action(TodayCommand.Kind.DEFER, action));
                return;
            case TOGGLE_STEP:
                commands.execute(TodayCommand.action(TodayCommand.Kind.TOGGLE_STEP, action));
                return;
            case TOGGLE_STEP_WITH_DELAY:
                commands.execute(TodayCommand.action(TodayCommand.Kind.TOGGLE_STEP_WITH_DELAY,
                        action));
                return;
            case FINISH_STEP:
                commands.execute(TodayCommand.action(TodayCommand.Kind.FINISH_STEP, action));
                return;
            case ADVANCE_STEP:
                commands.execute(TodayCommand.action(TodayCommand.Kind.ADVANCE_STEP, action));
                return;
            case UNDO_OCCURRENCE:
                commands.execute(TodayCommand.action(TodayCommand.Kind.UNDO_OCCURRENCE, action));
                return;
            case ADJUST_REPETITION:
                commands.execute(TodayCommand.action(TodayCommand.Kind.ADJUST_REPETITION,
                        action));
                return;
            case ADJUST_TRAINING_LOAD:
                commands.execute(TodayCommand.action(TodayCommand.Kind.ADJUST_TRAINING_LOAD,
                        action));
                return;
            case ADJUST_TRAINING_RIR:
                commands.execute(TodayCommand.action(TodayCommand.Kind.ADJUST_TRAINING_RIR,
                        action));
                return;
            case TOGGLE_TRAINING_SAFETY:
                commands.execute(TodayCommand.action(TodayCommand.Kind.TOGGLE_TRAINING_SAFETY,
                        action));
                return;
            case EDIT_REPETITION:
                commands.execute(TodayCommand.action(TodayCommand.Kind.EDIT_REPETITION, action));
                return;
            case SUBMIT_REPETITION:
                commands.execute(TodayCommand.action(TodayCommand.Kind.SUBMIT_REPETITION,
                        action));
                return;
            case START_DURATION_TIMER:
                commands.execute(TodayCommand.action(TodayCommand.Kind.START_DURATION_TIMER,
                        action));
                return;
            case PAUSE_TIMER:
                commands.execute(TodayCommand.action(TodayCommand.Kind.PAUSE_TIMER, action));
                return;
            case RESUME_TIMER:
                commands.execute(TodayCommand.action(TodayCommand.Kind.RESUME_TIMER, action));
                return;
            case RESET_TIMER:
                commands.execute(TodayCommand.action(TodayCommand.Kind.RESET_TIMER, action));
                return;
            case OBSERVE_TIMER:
                commands.execute(TodayCommand.action(TodayCommand.Kind.OBSERVE_TIMER, action));
                return;
        }
        throw new AssertionError("Unhandled Today action " + action.kind);
    }

    public synchronized void rebind(TodayUiModel today) {
        apply(reducer.rebind(state, today));
    }

    public synchronized void reorderSucceeded(String commandId, TodayStepMoveResult result) {
        apply(reducer.succeeded(state, commandId, result));
    }

    public synchronized void reorderFailed(String commandId) {
        apply(reducer.failed(state, commandId));
    }

    private void moveImmediately(String stepId, String beforeStepId) {
        List<String> canonical = TodayFeatureState.openStepIds(state.today);
        apply(reducer.begin(state, stepId, canonical));
        if (state.reorder.phase != TodayFeatureState.Reorder.Phase.DRAGGING) return;
        List<String> preview = new ArrayList<>(canonical);
        if (!preview.remove(stepId)) return;
        int target = beforeStepId == null ? preview.size() : preview.indexOf(beforeStepId);
        if (target < 0) {
            apply(reducer.cancel(state, stepId));
            return;
        }
        preview.add(target, stepId);
        apply(reducer.preview(state, stepId, preview));
        apply(reducer.drop(state, stepId, beforeStepId, commandIds.next()));
    }

    private void apply(TodayReducer.Result result) {
        if (result.state != state) {
            state = result.state;
            states.publish(state);
        }
        if (result.command != null) commands.execute(result.command);
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("Coordinator dependency required");
        return value;
    }
}
