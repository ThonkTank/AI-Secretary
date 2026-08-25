package de.thonktank.autosecretary;

import android.os.Bundle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskDefinition;
import de.thonktank.autosecretary.domain.model.TaskDetails;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;

/** Immutable editor state facade with separated draft, navigation and feedback components. */
public final class EditorUiState {
    private static final int BUNDLE_VERSION = 2;
    public enum Prompt { NONE, DISCARD, DELETE }
    public enum Page { TITLE, SCHEDULE, STEPS, SUMMARY }

    public final boolean open;
    public final boolean loading;
    public final boolean saving;
    public final String taskId;
    public final TaskEditorDraft draft;
    public final TaskEditorNavigation navigation;
    public final TaskEditorFeedback feedback;
    public final TaskDraftSnapshot baseline;
    public final boolean dirty;

    // Compatibility projections used by the public listener contract and existing callers.
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
    public final List<EditorStepState> stepStates;
    public final int nextDraftIdentity;
    public final String expandedStepId;
    public final Page page;
    public final boolean returnToSummary;
    public final Set<ValidationIssue> issues;
    public final Set<Page> attemptedPages;
    public final Set<String> attemptedStepIds;
    public final Prompt prompt;
    public final String storageError;

    EditorUiState(boolean open, boolean loading, boolean saving, String taskId,
                  TaskEditorDraft draft, TaskEditorNavigation navigation,
                  TaskEditorFeedback feedback, TaskDraftSnapshot baseline) {
        this.open = open; this.loading = loading; this.saving = saving; this.taskId = taskId;
        this.draft = draft; this.navigation = navigation; this.feedback = feedback;
        this.baseline = baseline == null ? draft.snapshot() : baseline;
        dirty = !this.baseline.equals(draft.snapshot());
        title = draft.title; slot = draft.slot; estimatedMinutes = draft.estimatedMinutes;
        recurrence = draft.recurrence; intervalDays = draft.intervalDays;
        weekdayMask = draft.weekdayMask; timeOfDayMask = draft.timeOfDayMask;
        boundKind = draft.boundKind; boundUntilOn = draft.boundUntilOn;
        boundWeeks = draft.boundWeeks; remainingCount = draft.remainingCount;
        deadlineOn = draft.deadlineOn; note = draft.note; stepStates = draft.steps;
        missedOccurrenceMode = draft.missedOccurrenceMode;
        nextDraftIdentity = draft.nextDraftIdentity;
        expandedStepId = navigation.expandedStepId; page = navigation.page;
        returnToSummary = navigation.returnToSummary;
        issues = feedback.issues; attemptedPages = feedback.attemptedPages;
        attemptedStepIds = feedback.attemptedStepIds; prompt = feedback.prompt;
        storageError = feedback.storageError;
    }

    public static EditorUiState closed() { return base(false, false, null); }
    public static EditorUiState create() { return create(TaskSlot.MORNING); }

    public static EditorUiState create(TaskSlot slot) {
        TaskEditorDraft draft = new TaskEditorDraft("", slot, null, Recurrence.DAILY, 2, 0,
                TimeOfDay.fromSlot(slot).bit, TaskBoundKind.FOREVER, null, null, null, null,
                "", Collections.emptyList(), 1);
        return new EditorUiState(true, false, false, null, draft,
                new TaskEditorNavigation(Page.TITLE, false, null),
                TaskEditorFeedback.empty(), null);
    }

    public static EditorUiState loading(String taskId) { return base(true, true, taskId); }

    private static EditorUiState base(boolean open, boolean loading, String taskId) {
        TaskEditorDraft draft = new TaskEditorDraft("", TaskSlot.MORNING, null,
                Recurrence.ONCE, 2, 0, 0, TaskBoundKind.FOREVER, null, null, null, null,
                "", Collections.emptyList(), 1);
        return new EditorUiState(open, loading, false, taskId, draft,
                new TaskEditorNavigation(taskId == null ? Page.TITLE : Page.SUMMARY,
                        false, null), TaskEditorFeedback.empty(), null);
    }

    public static EditorUiState edit(TaskDetails details) {
        List<EditorStepState> steps = new ArrayList<>();
        for (TaskStepTemplate value : details.stepTemplates) steps.add(EditorStepState.from(value));
        TaskEditorDraft draft = new TaskEditorDraft(details.title, details.slot,
                details.estimatedMinutes, details.recurrence, details.intervalDays,
                details.weekdayMask, details.timeOfDayMask, details.boundKind,
                details.boundUntilOn, details.boundWeeks, details.remainingCount,
                details.deadlineOn, details.note, details.missedOccurrenceMode, steps, 1);
        return new EditorUiState(true, false, false, details.id.value, draft,
                new TaskEditorNavigation(Page.SUMMARY, false, null),
                TaskEditorFeedback.empty(), null);
    }

    public EditorUiState withDraft(String title, TaskSlot slot, Recurrence recurrence,
                                   int intervalDays, int weekdayMask, List<String> labels) {
        List<EditorStepState> values = new ArrayList<>();
        for (int index = 0; index < labels.size(); index++) {
            EditorStepState old = index < stepStates.size() ? stepStates.get(index)
                    : EditorStepState.blank(nextDraftIdentity + index);
            values.add(old.withText(labels.get(index)));
        }
        int times = recurrence == Recurrence.ONCE ? 0 : TimeOfDay.fromSlot(slot).bit;
        return draft(title, slot, estimatedMinutes, recurrence, intervalDays, weekdayMask,
                times, TaskBoundKind.FOREVER, null, null, null, null, note, values,
                expandedStepId, nextDraftIdentity + Math.max(0, labels.size() - stepStates.size()));
    }

    public EditorUiState draft(String title, TaskSlot slot, Integer estimatedMinutes,
                               Recurrence recurrence, int intervalDays, int weekdayMask,
                               int timeOfDayMask, TaskBoundKind boundKind,
                               LocalDate boundUntilOn, Integer boundWeeks,
                               Integer remainingCount, LocalDate deadlineOn, String note,
                               List<EditorStepState> steps, String expandedStepId,
                               int nextDraftIdentity) {
        TaskEditorDraft value = draft.withValues(title, slot, estimatedMinutes, recurrence,
                intervalDays, weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, steps, nextDraftIdentity);
        return new EditorUiState(open, loading, saving, taskId, value,
                navigation.withExpandedStep(expandedStepId),
                new TaskEditorFeedback(Collections.emptySet(), attemptedPages, attemptedStepIds,
                        Prompt.NONE, ""), baseline);
    }

    public EditorUiState withFeedback(Set<ValidationIssue> issues, Prompt prompt,
                                      String storageError) {
        return new EditorUiState(open, loading, saving, taskId, draft, navigation,
                new TaskEditorFeedback(issues, attemptedPages, attemptedStepIds, prompt,
                        storageError), baseline);
    }

    public EditorUiState withValidationAttempt(Page attemptedPage, String attemptedStepId,
                                               Set<ValidationIssue> visibleIssues) {
        Set<Page> pages = new LinkedHashSet<>(attemptedPages);
        Set<String> steps = new LinkedHashSet<>(attemptedStepIds);
        if (attemptedStepId == null) pages.add(attemptedPage); else steps.add(attemptedStepId);
        return new EditorUiState(open, loading, saving, taskId, draft, navigation,
                new TaskEditorFeedback(visibleIssues, pages, steps, prompt, storageError), baseline);
    }

    public EditorUiState withAllValidationAttempted(Set<ValidationIssue> visibleIssues) {
        Set<Page> pages = new LinkedHashSet<>(attemptedPages);
        pages.add(Page.TITLE); pages.add(Page.SCHEDULE); pages.add(Page.STEPS);
        Set<String> steps = new LinkedHashSet<>(attemptedStepIds);
        for (EditorStepState step : stepStates) steps.add(step.id);
        return new EditorUiState(open, loading, saving, taskId, draft, navigation,
                new TaskEditorFeedback(visibleIssues, pages, steps, prompt, storageError), baseline);
    }

    public EditorUiState withSaving(boolean value) {
        return new EditorUiState(open, loading, value, taskId, draft, navigation, feedback, baseline);
    }

    public EditorUiState withMissedOccurrenceMode(MissedOccurrenceMode value) {
        return new EditorUiState(open, loading, saving, taskId,
                draft.withMissedOccurrenceMode(value), navigation, feedback, baseline);
    }

    public EditorUiState withPage(Page value, boolean returnValue) {
        return new EditorUiState(open, loading, saving, taskId, draft,
                navigation.withPage(value, returnValue), feedback, baseline);
    }

    public EditorUiState withExpandedStep(String id) {
        return new EditorUiState(open, loading, saving, taskId, draft,
                navigation.withExpandedStep(id), feedback, baseline);
    }

    public TaskDefinition definition() { return draft.definition(); }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("format_version", BUNDLE_VERSION);
        bundle.putBoolean("open", open); bundle.putBoolean("loading", loading);
        bundle.putBoolean("saving", saving); bundle.putString("task_id", taskId);
        bundle.putBundle("draft", draft.toBundle());
        bundle.putBundle("navigation", navigation.toBundle());
        bundle.putBundle("feedback", feedback.toBundle());
        bundle.putBundle("baseline", baseline.toBundle());
        return bundle;
    }

    public static EditorUiState fromBundle(Bundle bundle) {
        if (bundle == null || !bundle.getBoolean("open", false)) return closed();
        if (bundle.getInt("format_version") >= BUNDLE_VERSION && bundle.getBundle("draft") != null) {
            TaskEditorDraft draft = TaskEditorDraft.fromBundle(bundle.getBundle("draft"));
            Page fallback = bundle.getString("task_id") == null ? Page.TITLE : Page.SUMMARY;
            return new EditorUiState(true, bundle.getBoolean("loading"),
                    bundle.getBoolean("saving"), bundle.getString("task_id"), draft,
                    TaskEditorNavigation.fromBundle(bundle.getBundle("navigation"), fallback),
                    TaskEditorFeedback.fromBundle(bundle.getBundle("feedback")),
                    TaskDraftSnapshot.fromBundle(bundle.getBundle("baseline"), draft));
        }
        return fromLegacyBundle(bundle);
    }

    private static EditorUiState fromLegacyBundle(Bundle bundle) {
        List<EditorStepState> steps = new ArrayList<>();
        ArrayList<Bundle> values = bundle.getParcelableArrayList("step_states");
        if (values != null) for (Bundle value : values) steps.add(EditorStepState.fromBundle(value));
        TaskEditorDraft draft = new TaskEditorDraft(bundle.getString("title", ""),
                BundleValues.enumValue(TaskSlot.class, bundle.getString("slot"), TaskSlot.MORNING),
                BundleValues.integer(bundle, "estimated"), BundleValues.enumValue(Recurrence.class,
                bundle.getString("recurrence"), Recurrence.ONCE), bundle.getInt("interval", 2),
                bundle.getInt("weekdays"), bundle.getInt("times"),
                BundleValues.enumValue(TaskBoundKind.class, bundle.getString("bound"),
                        TaskBoundKind.FOREVER), BundleValues.date(bundle, "until"),
                BundleValues.integer(bundle, "weeks"), BundleValues.integer(bundle, "count"),
                BundleValues.date(bundle, "deadline"), bundle.getString("note", ""), steps,
                bundle.getInt("next_id", 1));
        String legacyBaseline = bundle.getString("baseline");
        TaskDraftSnapshot baseline = legacyBaseline == null
                || legacyBaseline.equals(legacySignature(draft)) ? draft.snapshot()
                : TaskDraftSnapshot.legacyDifferent(draft);
        return new EditorUiState(true, bundle.getBoolean("loading"), bundle.getBoolean("saving"),
                bundle.getString("task_id"), draft,
                new TaskEditorNavigation(BundleValues.enumValue(Page.class,
                        bundle.getString("page"), bundle.getString("task_id") == null
                                ? Page.TITLE : Page.SUMMARY),
                        bundle.getBoolean("return_summary"), bundle.getString("expanded")),
                legacyFeedback(bundle), baseline);
    }

    private static TaskEditorFeedback legacyFeedback(Bundle bundle) {
        Set<ValidationIssue> issues = new LinkedHashSet<>();
        ArrayList<Bundle> issueValues = bundle.getParcelableArrayList("validation_issues");
        if (issueValues != null) for (Bundle value : issueValues) {
            ValidationIssue issue = ValidationIssue.fromBundle(value);
            if (issue != null) issues.add(issue);
        }
        if (issueValues == null) {
            ArrayList<String> legacy = bundle.getStringArrayList("errors");
            if (legacy != null) for (String value : legacy) {
                ValidationIssue issue = legacyIssue(value);
                if (issue != null) issues.add(issue);
            }
        }
        Set<Page> pages = new LinkedHashSet<>();
        ArrayList<String> pageValues = bundle.getStringArrayList("attempted_pages");
        if (pageValues != null) for (String value : pageValues)
            pages.add(BundleValues.enumValue(Page.class, value, Page.TITLE));
        ArrayList<String> stepValues = bundle.getStringArrayList("attempted_steps");
        return new TaskEditorFeedback(issues, pages,
                stepValues == null ? Collections.emptySet() : new LinkedHashSet<>(stepValues),
                BundleValues.enumValue(Prompt.class, bundle.getString("prompt"), Prompt.NONE),
                bundle.getString("storage_error", ""));
    }

    private static ValidationIssue legacyIssue(String value) {
        if ("title".equals(value)) return ValidationIssue.task(ValidationIssue.Field.TITLE);
        if ("duration".equals(value)) return ValidationIssue.task(ValidationIssue.Field.DURATION);
        if ("weekdays".equals(value)) return ValidationIssue.task(ValidationIssue.Field.WEEKDAYS);
        if ("interval".equals(value)) return ValidationIssue.task(ValidationIssue.Field.INTERVAL);
        if ("times".equals(value)) return ValidationIssue.task(ValidationIssue.Field.TIMES);
        if ("bound".equals(value)) return ValidationIssue.task(ValidationIssue.Field.BOUND);
        if (value != null && value.startsWith("step-interval:"))
            return ValidationIssue.step(ValidationIssue.Field.STEP_INTERVAL,
                    value.substring("step-interval:".length()));
        if (value != null && value.startsWith("amount:"))
            return ValidationIssue.step(ValidationIssue.Field.STEP_AMOUNT,
                    value.substring("amount:".length()));
        if (value != null && value.startsWith("step:"))
            return ValidationIssue.step(ValidationIssue.Field.STEP_TITLE,
                    value.substring("step:".length()));
        return null;
    }

    private static String legacySignature(TaskEditorDraft draft) {
        StringBuilder result = new StringBuilder(Objects.toString(draft.title, "").trim()
                + '|' + draft.slot + '|' + draft.estimatedMinutes + '|' + draft.recurrence
                + '|' + draft.intervalDays + '|' + draft.weekdayMask + '|'
                + draft.timeOfDayMask + '|' + draft.boundKind + '|' + draft.boundUntilOn
                + '|' + draft.boundWeeks + '|' + draft.remainingCount + '|'
                + draft.deadlineOn + '|' + draft.note);
        result.append('|').append(draft.missedOccurrenceMode);
        for (EditorStepState step : draft.steps)
            result.append('|').append(step.id).append(':').append(step.text).append(':')
                    .append(step.cadenceMode).append(':').append(step.weekdayMask).append(':')
                    .append(step.intervalDays).append(':').append(step.amount).append(':')
                    .append(step.note);
        return result.toString();
    }
}
