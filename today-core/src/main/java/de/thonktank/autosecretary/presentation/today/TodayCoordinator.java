package de.thonktank.autosecretary.presentation.today;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;

/** Owns the local step selection and the drag and accessibility reorder lifecycle. */
public final class TodayCoordinator {
    private final TodayReducer reducer = new TodayReducer();
    private final Consumer<TodayReducer.ReorderWrite> persist;
    private final Consumer<TodayFeatureState> publish;
    private long sequence;
    private TodayFeatureState state;

    public TodayCoordinator(TodayUiModel initial, Consumer<TodayReducer.ReorderWrite> persist,
                            Consumer<TodayFeatureState> publish) {
        this.persist = Objects.requireNonNull(persist);
        this.publish = Objects.requireNonNull(publish);
        state = TodayFeatureState.idle(initial);
    }

    public synchronized TodayFeatureState state() { return state; }

    public synchronized void applyStepAction(TodayAction action) {
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
                apply(reducer.drop(state, action.id, action.relatedId, nextCommandId()));
                return;
            case MOVE_STEP:
                moveImmediately(action.id, action.relatedId);
                return;
        }
        throw new AssertionError("Expected a step selection or reorder action, got " + action.kind);
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
        apply(reducer.drop(state, stepId, beforeStepId, nextCommandId()));
    }

    private String nextCommandId() { return "today-reorder-" + ++sequence; }

    private void apply(TodayReducer.Result result) {
        if (result.state != state) {
            state = result.state;
            publish.accept(state);
        }
        if (result.write != null) persist.accept(result.write);
    }
}
