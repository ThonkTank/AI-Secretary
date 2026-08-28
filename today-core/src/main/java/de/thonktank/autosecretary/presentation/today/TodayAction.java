package de.thonktank.autosecretary.presentation.today;


import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Closed, identity-only boundary for every interaction on the Today screen. */
public final class TodayAction {
    public enum Kind {
        COMPLETE_OCCURRENCE,
        REQUEST_CLOSE,
        COMPLETE_REMAINING,
        HARVEST,
        DEFER,
        TOGGLE_STEP,
        TOGGLE_STEP_WITH_DELAY,
        FINISH_STEP,
        ADVANCE_STEP,
        UNDO_OCCURRENCE,
        ADJUST_REPETITION,
        ADJUST_TRAINING_LOAD,
        ADJUST_TRAINING_RIR,
        TOGGLE_TRAINING_SAFETY,
        EDIT_REPETITION,
        SUBMIT_REPETITION,
        START_DURATION_TIMER,
        PAUSE_TIMER,
        RESUME_TIMER,
        RESET_TIMER,
        OBSERVE_TIMER,
        ADD_TASK,
        OPEN_TASK_MENU,
        EDIT_TASK,
        REQUEST_MOVE_TASK,
        MOVE_TASK,
        REQUEST_DELETE_TASK,
        CONFIRM_DELETE_TASK,
        CONFIRM_CLOSE_TASK,
        ACKNOWLEDGE_REQUEST,
        ACKNOWLEDGE_REWARD,
        BEGIN_REORDER,
        PREVIEW_REORDER,
        CANCEL_REORDER,
        DROP_REORDER,
        MOVE_STEP
    }

    public final Kind kind;
    public final String id;
    public final String relatedId;
    public final String text;
    public final int value;
    public final long longValue;
    public final List<String> order;
    public final TaskActionTarget target;
    public final TaskSlot slot;

    private TodayAction(Kind kind, String id, String relatedId,
                        String text, int value, List<String> order,
                        TaskActionTarget target, TaskSlot slot) {
        this(kind, id, relatedId, text, value, 0L, order, target, slot);
    }

    private TodayAction(Kind kind, String id, String relatedId,
                        String text, int value, long longValue, List<String> order,
                        TaskActionTarget target, TaskSlot slot) {
        if (kind == null) throw new IllegalArgumentException("Today action kind is required");
        this.kind = kind;
        this.id = id == null ? "" : id;
        this.relatedId = emptyToNull(relatedId);
        this.text = text;
        this.value = value;
        this.longValue = longValue;
        this.order = Collections.unmodifiableList(new ArrayList<>(order));
        this.target = target;
        this.slot = slot;
    }

    public static TodayAction completeOccurrence(String occurrenceId) {
        return identified(Kind.COMPLETE_OCCURRENCE, occurrenceId);
    }

    public static TodayAction requestClose(String taskId, String title) {
        return new TodayAction(Kind.REQUEST_CLOSE, requiredId(taskId), null,
                title == null ? "" : title, 0, Collections.emptyList(), null, null);
    }

    public static TodayAction completeRemaining(String occurrenceId) {
        return identified(Kind.COMPLETE_REMAINING, occurrenceId);
    }

    public static TodayAction harvest(String occurrenceId) {
        return identified(Kind.HARVEST, occurrenceId);
    }

    public static TodayAction defer(String occurrenceOrTaskId) {
        return identified(Kind.DEFER, occurrenceOrTaskId);
    }

    public static TodayAction toggleStep(String stepId) {
        return identified(Kind.TOGGLE_STEP, stepId);
    }

    public static TodayAction toggleStep(String stepId, long chosenDelayMillis) {
        if (chosenDelayMillis < 0L)
            throw new IllegalArgumentException("Delay must not be negative");
        return new TodayAction(Kind.TOGGLE_STEP_WITH_DELAY, requiredId(stepId), null,
                null, 0, chosenDelayMillis, Collections.emptyList(), null, null);
    }

    public static TodayAction finishStep(String stepId) {
        return identified(Kind.FINISH_STEP, stepId);
    }

    public static TodayAction advanceStep(String stepId) {
        return identified(Kind.ADVANCE_STEP, stepId);
    }

    public static TodayAction undoOccurrence(String occurrenceId) {
        return identified(Kind.UNDO_OCCURRENCE, occurrenceId);
    }

    public static TodayAction adjustRepetition(String stepId, int delta) {
        if (delta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
        return new TodayAction(Kind.ADJUST_REPETITION, requiredId(stepId), null,
                null, delta, Collections.emptyList(), null, null);
    }

    public static TodayAction editRepetition(String stepId, int index) {
        if (index < 0) throw new IllegalArgumentException("Saved result index is required");
        return new TodayAction(Kind.EDIT_REPETITION, requiredId(stepId), null,
                null, index, Collections.emptyList(), null, null);
    }

    public static TodayAction adjustTrainingLoad(String stepId, int milliUnitDelta) {
        if (milliUnitDelta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
        return new TodayAction(Kind.ADJUST_TRAINING_LOAD, requiredId(stepId), null,
                null, milliUnitDelta, Collections.emptyList(), null, null);
    }

    public static TodayAction adjustTrainingRir(String stepId, int delta) {
        if (delta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
        return new TodayAction(Kind.ADJUST_TRAINING_RIR, requiredId(stepId), null,
                null, delta, Collections.emptyList(), null, null);
    }

    public static TodayAction toggleTrainingSafety(String stepId) {
        return identified(Kind.TOGGLE_TRAINING_SAFETY, stepId);
    }

    public static TodayAction submitRepetition(String stepId) {
        return identified(Kind.SUBMIT_REPETITION, stepId);
    }

    public static TodayAction startDurationTimer(String stepId, String title, int seconds) {
        if (seconds < 1) throw new IllegalArgumentException("Timer duration is required");
        return new TodayAction(Kind.START_DURATION_TIMER, requiredId(stepId), null,
                title == null ? "" : title, seconds, Collections.emptyList(), null, null);
    }

    public static TodayAction pauseTimer(String timerId) {
        return identified(Kind.PAUSE_TIMER, timerId);
    }

    public static TodayAction resumeTimer(String timerId) {
        return identified(Kind.RESUME_TIMER, timerId);
    }

    public static TodayAction resetTimer(String timerId) {
        return identified(Kind.RESET_TIMER, timerId);
    }

    public static TodayAction observeTimer(String timerId) {
        return identified(Kind.OBSERVE_TIMER, timerId);
    }

    public static TodayAction addTask() {
        return new TodayAction(Kind.ADD_TASK, "", null, null, 0,
                Collections.emptyList(), null, null);
    }

    public static TodayAction openTaskMenu(TaskActionTarget target) {
        if (target == null) throw new IllegalArgumentException("Task menu target is required");
        return new TodayAction(Kind.OPEN_TASK_MENU, target.taskId, null, null, 0,
                Collections.emptyList(), target, null);
    }

    public static TodayAction editTask(String requestId) {
        return identified(Kind.EDIT_TASK, requestId);
    }

    public static TodayAction requestMoveTask(String requestId) {
        return identified(Kind.REQUEST_MOVE_TASK, requestId);
    }

    public static TodayAction moveTask(String requestId, TaskSlot slot) {
        if (slot == null) throw new IllegalArgumentException("Task slot is required");
        return new TodayAction(Kind.MOVE_TASK, requiredId(requestId), null, null, 0,
                Collections.emptyList(), null, slot);
    }

    public static TodayAction requestDeleteTask(String requestId) {
        return identified(Kind.REQUEST_DELETE_TASK, requestId);
    }

    public static TodayAction confirmDeleteTask(String requestId) {
        return identified(Kind.CONFIRM_DELETE_TASK, requestId);
    }

    public static TodayAction confirmCloseTask(String requestId) {
        return identified(Kind.CONFIRM_CLOSE_TASK, requestId);
    }

    public static TodayAction acknowledgeRequest(String requestId) {
        return identified(Kind.ACKNOWLEDGE_REQUEST, requestId);
    }

    public static TodayAction acknowledgeReward(String rewardId) {
        return identified(Kind.ACKNOWLEDGE_REWARD, rewardId);
    }

    public static TodayAction beginReorder(String stepId, List<String> canonicalOrder) {
        return ordered(Kind.BEGIN_REORDER, stepId, null, canonicalOrder);
    }

    public static TodayAction previewReorder(String stepId, List<String> previewOrder) {
        return ordered(Kind.PREVIEW_REORDER, stepId, null, previewOrder);
    }

    public static TodayAction cancelReorder(String stepId) {
        return identified(Kind.CANCEL_REORDER, stepId);
    }

    public static TodayAction dropReorder(String stepId, String beforeStepId) {
        return new TodayAction(Kind.DROP_REORDER, requiredId(stepId), beforeStepId,
                null, 0, Collections.emptyList(), null, null);
    }

    public static TodayAction moveStep(String stepId, String beforeStepId) {
        return new TodayAction(Kind.MOVE_STEP, requiredId(stepId), beforeStepId,
                null, 0, Collections.emptyList(), null, null);
    }

    private static TodayAction identified(Kind kind, String id) {
        return new TodayAction(kind, requiredId(id), null, null, 0,
                Collections.emptyList(), null, null);
    }

    private static TodayAction ordered(Kind kind, String id, String relatedId,
                                       List<String> order) {
        if (order == null) throw new IllegalArgumentException("Step order is required");
        List<String> copy = new ArrayList<>();
        for (String stepId : order) copy.add(requiredId(stepId));
        return new TodayAction(kind, requiredId(id), relatedId, null, 0, copy, null, null);
    }

    private static String requiredId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Today action identity is required");
        return value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
