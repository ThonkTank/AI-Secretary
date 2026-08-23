package de.thonktank.autosecretary;

import android.os.Bundle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;

/** Typed, semantic baseline used for dirty checking. */
public final class TaskDraftSnapshot {
    private final String title;
    private final TaskSlot slot;
    private final Integer estimatedMinutes;
    private final Recurrence recurrence;
    private final int intervalDays;
    private final int weekdayMask;
    private final int timeOfDayMask;
    private final TaskBoundKind boundKind;
    private final LocalDate boundUntilOn;
    private final Integer boundWeeks;
    private final Integer remainingCount;
    private final LocalDate deadlineOn;
    private final String note;
    private final List<EditorStepState> steps;
    private final boolean legacyDifferent;

    private TaskDraftSnapshot(TaskEditorDraft draft, boolean legacyDifferent) {
        title = draft.title.trim(); slot = draft.slot; estimatedMinutes = draft.estimatedMinutes;
        recurrence = draft.recurrence; intervalDays = draft.intervalDays;
        weekdayMask = draft.weekdayMask; timeOfDayMask = draft.timeOfDayMask;
        boundKind = draft.boundKind; boundUntilOn = draft.boundUntilOn;
        boundWeeks = draft.boundWeeks; remainingCount = draft.remainingCount;
        deadlineOn = draft.deadlineOn; note = draft.note;
        steps = Collections.unmodifiableList(new ArrayList<>(draft.steps));
        this.legacyDifferent = legacyDifferent;
    }

    public static TaskDraftSnapshot from(TaskEditorDraft draft) {
        return new TaskDraftSnapshot(draft, false);
    }

    static TaskDraftSnapshot legacyDifferent(TaskEditorDraft draft) {
        return new TaskDraftSnapshot(draft, true);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBundle("draft", new TaskEditorDraft(title, slot, estimatedMinutes, recurrence,
                intervalDays, weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, steps, 1).toBundle());
        bundle.putBoolean("legacy_different", legacyDifferent);
        return bundle;
    }

    static TaskDraftSnapshot fromBundle(Bundle bundle, TaskEditorDraft fallback) {
        if (bundle == null || bundle.getBundle("draft") == null) return from(fallback);
        return new TaskDraftSnapshot(TaskEditorDraft.fromBundle(bundle.getBundle("draft")),
                bundle.getBoolean("legacy_different"));
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof TaskDraftSnapshot)) return false;
        TaskDraftSnapshot value = (TaskDraftSnapshot) other;
        return intervalDays == value.intervalDays && weekdayMask == value.weekdayMask
                && timeOfDayMask == value.timeOfDayMask
                && legacyDifferent == value.legacyDifferent && title.equals(value.title)
                && slot == value.slot && Objects.equals(estimatedMinutes, value.estimatedMinutes)
                && recurrence == value.recurrence && boundKind == value.boundKind
                && Objects.equals(boundUntilOn, value.boundUntilOn)
                && Objects.equals(boundWeeks, value.boundWeeks)
                && Objects.equals(remainingCount, value.remainingCount)
                && Objects.equals(deadlineOn, value.deadlineOn) && note.equals(value.note)
                && steps.equals(value.steps);
    }

    @Override public int hashCode() {
        return Objects.hash(title, slot, estimatedMinutes, recurrence, intervalDays, weekdayMask,
                timeOfDayMask, boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn,
                note, steps, legacyDifferent);
    }
}
