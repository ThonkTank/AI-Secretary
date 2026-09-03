package de.thonktank.autosecretary.presentation.today;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;

/** Pure state machine for the Today reorder lifecycle. */
public final class TodayReducer {
    public static final class Result {
        public final TodayFeatureState state;
        public final TodayCommand command;

        private Result(TodayFeatureState state, TodayCommand command) {
            this.state = state;
            this.command = command;
        }
    }

    public Result begin(TodayFeatureState current, String movingStepId,
                        List<String> canonicalOrder) {
        if (current.reorder.phase != TodayFeatureState.Reorder.Phase.IDLE
                || !validOrder(canonicalOrder, canonicalOrder)
                || !canonicalOrder.contains(movingStepId)) return unchanged(current);
        TodayFeatureState.Reorder reorder = TodayFeatureState.Reorder.dragging(
                canonicalOrder, canonicalOrder, movingStepId);
        return changed(current.today, reorder, null, null, null);
    }

    public Result preview(TodayFeatureState current, String movingStepId,
                          List<String> previewOrder) {
        TodayFeatureState.Reorder reorder = current.reorder;
        if (reorder.phase != TodayFeatureState.Reorder.Phase.DRAGGING
                || !movingStepId.equals(reorder.movingStepId)
                || !validOrder(reorder.canonicalOrder, previewOrder)) return unchanged(current);
        return changed(current.today,
                TodayFeatureState.Reorder.dragging(reorder.canonicalOrder, previewOrder,
                        movingStepId), null, null, null);
    }

    public Result cancel(TodayFeatureState current, String movingStepId) {
        TodayFeatureState.Reorder reorder = current.reorder;
        if (reorder.phase != TodayFeatureState.Reorder.Phase.DRAGGING
                || !movingStepId.equals(reorder.movingStepId)) return unchanged(current);
        return changed(current.today, TodayFeatureState.Reorder.idle(
                reorder.canonicalOrder), null, null, null);
    }

    public Result drop(TodayFeatureState current, String movingStepId,
                       String beforeStepId, String commandId) {
        TodayFeatureState.Reorder reorder = current.reorder;
        if (reorder.phase == TodayFeatureState.Reorder.Phase.PERSISTING)
            return unchanged(current);
        if (reorder.phase != TodayFeatureState.Reorder.Phase.DRAGGING
                || !movingStepId.equals(reorder.movingStepId)
                || commandId == null || commandId.isEmpty()) return unchanged(current);
        TodayFeatureState.Reorder persisting = TodayFeatureState.Reorder.persisting(
                reorder.canonicalOrder, reorder.previewOrder, movingStepId, commandId);
        return changed(current.today, persisting, null, null,
                TodayCommand.reorder(commandId, movingStepId, beforeStepId));
    }

    public Result succeeded(TodayFeatureState current, String commandId,
                            TodayStepMoveResult result) {
        TodayFeatureState.Reorder reorder = current.reorder;
        if (reorder.phase != TodayFeatureState.Reorder.Phase.PERSISTING
                || !commandId.equals(reorder.commandId)) return unchanged(current);
        List<String> confirmed = result.openStepIds;
        TodayUiModel today = applyOpenOrder(current.today, confirmed);
        return changed(today, TodayFeatureState.Reorder.idle(confirmed), null,
                null, null);
    }

    public Result failed(TodayFeatureState current, String commandId) {
        TodayFeatureState.Reorder reorder = current.reorder;
        if (reorder.phase != TodayFeatureState.Reorder.Phase.PERSISTING
                || !commandId.equals(reorder.commandId)) return unchanged(current);
        return changed(current.today, TodayFeatureState.Reorder.idle(
                reorder.canonicalOrder), TodayFeatureState.Feedback.REORDER_FAILED, null, null);
    }

    public Result select(TodayFeatureState current, String stepId) {
        if (current.reorder.phase != TodayFeatureState.Reorder.Phase.IDLE
                || !TodayFeatureState.openStepIds(current.today).contains(stepId)
                || stepId.equals(current.selectedStepId)) return unchanged(current);
        return changed(current.today, current.reorder, null, stepId, null);
    }

    public Result rebind(TodayFeatureState current, TodayUiModel today) {
        boolean interrupted = current.reorder.phase != TodayFeatureState.Reorder.Phase.IDLE;
        String selected = sameFocus(current.today, today)
                && TodayFeatureState.openStepIds(today).contains(current.selectedStepId)
                ? current.selectedStepId : null;
        return new Result(new TodayFeatureState(today,
                TodayFeatureState.Reorder.idle(TodayFeatureState.openStepIds(today)),
                interrupted ? TodayFeatureState.Feedback.REORDER_INTERRUPTED : null,
                selected), null);
    }

    private static Result changed(TodayUiModel today,
                                  TodayFeatureState.Reorder reorder,
                                  TodayFeatureState.Feedback feedback,
                                  String selectedStepId,
                                  TodayCommand command) {
        return new Result(new TodayFeatureState(today, reorder, feedback, selectedStepId), command);
    }

    private static Result unchanged(TodayFeatureState state) {
        return new Result(state, null);
    }

    private static boolean validOrder(List<String> canonical, List<String> candidate) {
        if (canonical == null || candidate == null || canonical.size() != candidate.size())
            return false;
        Set<String> expected = new HashSet<>(canonical);
        return expected.size() == canonical.size() && expected.equals(new HashSet<>(candidate));
    }

    private static boolean sameFocus(TodayUiModel before, TodayUiModel after) {
        if (before.focus == null || after.focus == null) return before.focus == after.focus;
        String beforeId = focusIdentity(before.focus);
        return beforeId.equals(focusIdentity(after.focus));
    }

    private static String focusIdentity(FocusTaskUiModel focus) {
        return focus.occurrenceId().isEmpty() ? focus.taskId() : focus.occurrenceId();
    }

    static TodayUiModel applyOpenOrder(TodayUiModel today, List<String> openOrder) {
        FocusTaskUiModel focus = today.focus;
        if (focus == null) return today;
        Map<String, FocusStepUiModel> open = new HashMap<>();
        for (FocusStepUiModel step : focus.steps) if (!step.isDone()) open.put(step.id, step);
        if (open.size() != openOrder.size() || !open.keySet().equals(new HashSet<>(openOrder)))
            return today;
        int nextOpen = 0;
        List<FocusStepUiModel> steps = new ArrayList<>();
        for (FocusStepUiModel step : focus.steps) {
            if (step.isDone()) steps.add(step);
            else steps.add(open.get(openOrder.get(nextOpen++)));
        }
        FocusTaskUiModel updatedFocus = FocusTaskUiModel.builder(focus.actionTarget)
                .nextAction(focus.nextAction)
                .steps(steps, focus.remainingSteps)
                .ongoing(focus.ongoing)
                .overdue(focus.overdue)
                .allowDefer(focus.allowDefer)
                .harvestReady(focus.harvestReady)
                .backlogCount(focus.backlogCount)
                .reward(focus.reward, focus.vessel)
                .grainLevel(focus.grainLevel)
                .build();
        return new TodayUiModel(today.xpProgress, updatedFocus, today.timeline,
                today.completedToday, today.flowRuns);
    }
}
