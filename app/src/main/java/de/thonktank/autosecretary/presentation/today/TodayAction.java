package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.Nullable;

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
        ADVANCE_STEP,
        UNDO_OCCURRENCE,
        ADJUST_REPETITION,
        EDIT_REPETITION,
        SUBMIT_REPETITION,
        BEGIN_REORDER,
        PREVIEW_REORDER,
        CANCEL_REORDER,
        DROP_REORDER,
        MOVE_STEP
    }

    public final Kind kind;
    public final String id;
    @Nullable public final String relatedId;
    @Nullable public final String text;
    public final int value;
    public final List<String> order;

    private TodayAction(Kind kind, String id, @Nullable String relatedId,
                        @Nullable String text, int value, List<String> order) {
        if (kind == null) throw new IllegalArgumentException("Today action kind is required");
        this.kind = kind;
        this.id = id == null ? "" : id;
        this.relatedId = emptyToNull(relatedId);
        this.text = text;
        this.value = value;
        this.order = Collections.unmodifiableList(new ArrayList<>(order));
    }

    public static TodayAction completeOccurrence(String occurrenceId) {
        return identified(Kind.COMPLETE_OCCURRENCE, occurrenceId);
    }

    public static TodayAction requestClose(String taskId, String title) {
        return new TodayAction(Kind.REQUEST_CLOSE, requiredId(taskId), null,
                title == null ? "" : title, 0, Collections.emptyList());
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

    public static TodayAction advanceStep(String stepId) {
        return identified(Kind.ADVANCE_STEP, stepId);
    }

    public static TodayAction undoOccurrence(String occurrenceId) {
        return identified(Kind.UNDO_OCCURRENCE, occurrenceId);
    }

    public static TodayAction adjustRepetition(String stepId, int delta) {
        if (delta == 0) throw new IllegalArgumentException("Adjustment must not be zero");
        return new TodayAction(Kind.ADJUST_REPETITION, requiredId(stepId), null,
                null, delta, Collections.emptyList());
    }

    public static TodayAction editRepetition(String stepId, int index) {
        if (index < 0) throw new IllegalArgumentException("Saved result index is required");
        return new TodayAction(Kind.EDIT_REPETITION, requiredId(stepId), null,
                null, index, Collections.emptyList());
    }

    public static TodayAction submitRepetition(String stepId) {
        return identified(Kind.SUBMIT_REPETITION, stepId);
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

    public static TodayAction dropReorder(String stepId, @Nullable String beforeStepId) {
        return new TodayAction(Kind.DROP_REORDER, requiredId(stepId), beforeStepId,
                null, 0, Collections.emptyList());
    }

    public static TodayAction moveStep(String stepId, @Nullable String beforeStepId) {
        return new TodayAction(Kind.MOVE_STEP, requiredId(stepId), beforeStepId,
                null, 0, Collections.emptyList());
    }

    private static TodayAction identified(Kind kind, String id) {
        return new TodayAction(kind, requiredId(id), null, null, 0,
                Collections.emptyList());
    }

    private static TodayAction ordered(Kind kind, String id, @Nullable String relatedId,
                                       List<String> order) {
        if (order == null) throw new IllegalArgumentException("Step order is required");
        List<String> copy = new ArrayList<>();
        for (String stepId : order) copy.add(requiredId(stepId));
        return new TodayAction(kind, requiredId(id), relatedId, null, 0, copy);
    }

    private static String requiredId(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Today action identity is required");
        return value;
    }

    @Nullable private static String emptyToNull(@Nullable String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
