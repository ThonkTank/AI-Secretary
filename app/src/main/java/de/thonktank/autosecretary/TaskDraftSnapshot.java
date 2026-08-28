package de.thonktank.autosecretary;

import android.os.Bundle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;

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
    private final MissedOccurrenceMode missedOccurrenceMode;
    private final List<EditorStepState> steps;
    private final TaskFlowDraft flow;
    private final boolean legacyDifferent;

    private TaskDraftSnapshot(TaskEditorDraft draft, boolean legacyDifferent) {
        title = draft.title.trim(); slot = draft.slot; estimatedMinutes = draft.estimatedMinutes;
        recurrence = draft.recurrence; intervalDays = draft.intervalDays;
        weekdayMask = draft.weekdayMask; timeOfDayMask = draft.timeOfDayMask;
        boundKind = draft.boundKind; boundUntilOn = draft.boundUntilOn;
        boundWeeks = draft.boundWeeks; remainingCount = draft.remainingCount;
        deadlineOn = draft.deadlineOn; note = draft.note;
        missedOccurrenceMode = draft.missedOccurrenceMode;
        steps = Collections.unmodifiableList(new ArrayList<>(draft.steps));
        flow = draft.flow;
        this.legacyDifferent = legacyDifferent;
    }

    public static TaskDraftSnapshot from(TaskEditorDraft draft) {
        return new TaskDraftSnapshot(draft, false);
    }

    static TaskDraftSnapshot legacyDifferent(TaskEditorDraft draft) {
        return new TaskDraftSnapshot(draft, true);
    }

    TaskDraftSnapshot withCapacityCatalog(List<CapacityResource> catalog) {
        TaskEditorDraft value = new TaskEditorDraft(title, slot, estimatedMinutes, recurrence,
                intervalDays, weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, missedOccurrenceMode, steps, 1,
                flow.mergeCatalog(catalog));
        return new TaskDraftSnapshot(value, legacyDifferent);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBundle("draft", new TaskEditorDraft(title, slot, estimatedMinutes, recurrence,
                intervalDays, weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, missedOccurrenceMode, steps, 1,
                flow).toBundle());
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
                && missedOccurrenceMode == value.missedOccurrenceMode
                && steps.equals(value.steps) && flow.equals(value.flow);
    }

    @Override public int hashCode() {
        return Objects.hash(title, slot, estimatedMinutes, recurrence, intervalDays, weekdayMask,
                timeOfDayMask, boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn,
                note, missedOccurrenceMode, steps, flow, legacyDifferent);
    }
}
