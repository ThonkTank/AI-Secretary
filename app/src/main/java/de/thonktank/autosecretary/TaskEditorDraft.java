package de.thonktank.autosecretary;

import android.os.Bundle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;

/** Immutable collection of the task fields edited by the wizard. */
public final class TaskEditorDraft {
    public final String title;
    public final TaskSlot slot;
    public final Integer estimatedMinutes;
    public final Recurrence recurrence;
    public final int intervalDays;
    public final int weekdayMask;
    public final int timeOfDayMask;
    public final TaskBoundKind boundKind;
    public final LocalDate boundUntilOn;
    public final Integer boundWeeks;
    public final Integer remainingCount;
    public final LocalDate deadlineOn;
    public final String note;
    public final MissedOccurrenceMode missedOccurrenceMode;
    public final List<EditorStepState> steps;
    public final int nextDraftIdentity;

    public TaskEditorDraft(String title, TaskSlot slot, Integer estimatedMinutes,
                           Recurrence recurrence, int intervalDays, int weekdayMask,
                           int timeOfDayMask, TaskBoundKind boundKind, LocalDate boundUntilOn,
                           Integer boundWeeks, Integer remainingCount, LocalDate deadlineOn,
                           String note, List<EditorStepState> steps, int nextDraftIdentity) {
        this(title, slot, estimatedMinutes, recurrence, intervalDays, weekdayMask,
                timeOfDayMask, boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn,
                note, MissedOccurrenceMode.COLLAPSE, steps, nextDraftIdentity);
    }

    public TaskEditorDraft(String title, TaskSlot slot, Integer estimatedMinutes,
                           Recurrence recurrence, int intervalDays, int weekdayMask,
                           int timeOfDayMask, TaskBoundKind boundKind, LocalDate boundUntilOn,
                           Integer boundWeeks, Integer remainingCount, LocalDate deadlineOn,
                           String note, MissedOccurrenceMode missedOccurrenceMode,
                           List<EditorStepState> steps, int nextDraftIdentity) {
        this.title = title == null ? "" : title;
        this.slot = slot == null ? TaskSlot.MORNING : slot;
        this.estimatedMinutes = estimatedMinutes;
        this.recurrence = recurrence == null ? Recurrence.ONCE : recurrence;
        this.intervalDays = intervalDays;
        this.weekdayMask = weekdayMask;
        this.timeOfDayMask = timeOfDayMask;
        this.boundKind = boundKind == null ? TaskBoundKind.FOREVER : boundKind;
        this.boundUntilOn = boundUntilOn;
        this.boundWeeks = boundWeeks;
        this.remainingCount = remainingCount;
        this.deadlineOn = deadlineOn;
        this.note = note == null ? "" : note;
        this.missedOccurrenceMode = missedOccurrenceMode == null
                ? MissedOccurrenceMode.COLLAPSE : missedOccurrenceMode;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.nextDraftIdentity = nextDraftIdentity;
    }

    public TaskEditorDraft withValues(String title, TaskSlot slot, Integer estimatedMinutes,
                                      Recurrence recurrence, int intervalDays, int weekdayMask,
                                      int timeOfDayMask, TaskBoundKind boundKind,
                                      LocalDate boundUntilOn, Integer boundWeeks,
                                      Integer remainingCount, LocalDate deadlineOn, String note,
                                      List<EditorStepState> steps, int nextDraftIdentity) {
        return new TaskEditorDraft(title, slot, estimatedMinutes, recurrence, intervalDays,
                weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, missedOccurrenceMode, steps,
                nextDraftIdentity);
    }

    public TaskEditorDraft withMissedOccurrenceMode(MissedOccurrenceMode value) {
        return new TaskEditorDraft(title, slot, estimatedMinutes, recurrence, intervalDays,
                weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, value, steps, nextDraftIdentity);
    }

    public TaskDraftSnapshot snapshot() { return TaskDraftSnapshot.from(this); }

    public TaskDefinition definition() {
        List<TaskStepDefinition> definitions = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++)
            definitions.add(steps.get(index).definition(index, recurrence == Recurrence.ONCE));
        return new TaskDefinition(title, estimatedMinutes, slot, recurrence, intervalDays,
                weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, missedOccurrenceMode, definitions);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("title", title); bundle.putString("slot", slot.name());
        BundleValues.putInteger(bundle, "estimated", estimatedMinutes);
        bundle.putString("recurrence", recurrence.name()); bundle.putInt("interval", intervalDays);
        bundle.putInt("weekdays", weekdayMask); bundle.putInt("times", timeOfDayMask);
        bundle.putString("bound", boundKind.name()); BundleValues.putDate(bundle, "until", boundUntilOn);
        BundleValues.putInteger(bundle, "weeks", boundWeeks);
        BundleValues.putInteger(bundle, "count", remainingCount);
        BundleValues.putDate(bundle, "deadline", deadlineOn); bundle.putString("note", note);
        bundle.putString("missed_mode", missedOccurrenceMode.name());
        ArrayList<Bundle> values = new ArrayList<>();
        for (EditorStepState value : steps) values.add(value.toBundle());
        bundle.putParcelableArrayList("steps", values);
        bundle.putInt("next_id", nextDraftIdentity);
        return bundle;
    }

    static TaskEditorDraft fromBundle(Bundle bundle) {
        List<EditorStepState> steps = new ArrayList<>();
        ArrayList<Bundle> values = bundle.getParcelableArrayList("steps");
        if (values != null) for (Bundle value : values) steps.add(EditorStepState.fromBundle(value));
        return new TaskEditorDraft(bundle.getString("title", ""),
                BundleValues.enumValue(TaskSlot.class, bundle.getString("slot"), TaskSlot.MORNING),
                BundleValues.integer(bundle, "estimated"),
                BundleValues.enumValue(Recurrence.class, bundle.getString("recurrence"), Recurrence.ONCE),
                bundle.getInt("interval", 2), bundle.getInt("weekdays"), bundle.getInt("times"),
                BundleValues.enumValue(TaskBoundKind.class, bundle.getString("bound"), TaskBoundKind.FOREVER),
                BundleValues.date(bundle, "until"), BundleValues.integer(bundle, "weeks"),
                BundleValues.integer(bundle, "count"), BundleValues.date(bundle, "deadline"),
                bundle.getString("note", ""), BundleValues.enumValue(MissedOccurrenceMode.class,
                bundle.getString("missed_mode"), MissedOccurrenceMode.COLLAPSE), steps,
                bundle.getInt("next_id", 1));
    }
}
