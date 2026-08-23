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
import de.thonktank.autosecretary.domain.model.TaskStepDefinition;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

public final class EditorUiState {
    public enum Prompt { NONE, DISCARD, DELETE }
    public enum Page { TITLE, SCHEDULE, STEPS, SUMMARY }

    public final boolean open;
    public final boolean loading;
    public final boolean saving;
    public final String taskId;
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
    public final List<EditorStepState> stepStates;
    public final String expandedStepId;
    public final Set<ValidationIssue> issues;
    public final Set<Page> attemptedPages;
    public final Set<String> attemptedStepIds;
    public final Prompt prompt;
    public final String storageError;
    public final int nextDraftIdentity;
    public final String baseline;
    public final boolean dirty;
    public final Page page;
    public final boolean returnToSummary;

    private EditorUiState(boolean open, boolean loading, boolean saving, String taskId,
                          String title, TaskSlot slot, Integer estimatedMinutes,
                          Recurrence recurrence, int intervalDays, int weekdayMask,
                          int timeOfDayMask, TaskBoundKind boundKind, LocalDate boundUntilOn,
                          Integer boundWeeks, Integer remainingCount, LocalDate deadlineOn,
                          String note, List<EditorStepState> steps, String expandedStepId,
                          Set<ValidationIssue> issues, Set<Page> attemptedPages,
                          Set<String> attemptedStepIds, Prompt prompt, String storageError,
                          int nextDraftIdentity, String baseline, Page page,
                          boolean returnToSummary) {
        this.open = open; this.loading = loading; this.saving = saving; this.taskId = taskId;
        this.title = title == null ? "" : title; this.slot = slot;
        this.estimatedMinutes = estimatedMinutes; this.recurrence = recurrence;
        this.intervalDays = intervalDays; this.weekdayMask = weekdayMask;
        this.timeOfDayMask = timeOfDayMask; this.boundKind = boundKind;
        this.boundUntilOn = boundUntilOn; this.boundWeeks = boundWeeks;
        this.remainingCount = remainingCount; this.deadlineOn = deadlineOn;
        this.note = note == null ? "" : note;
        this.stepStates = Collections.unmodifiableList(new ArrayList<>(steps));
        this.expandedStepId = expandedStepId;
        this.issues = Collections.unmodifiableSet(new LinkedHashSet<>(issues));
        this.attemptedPages = Collections.unmodifiableSet(new LinkedHashSet<>(attemptedPages));
        this.attemptedStepIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(attemptedStepIds));
        this.prompt = prompt; this.storageError = storageError == null ? "" : storageError;
        this.nextDraftIdentity = nextDraftIdentity;
        String signature = signature(title, slot, estimatedMinutes, recurrence, intervalDays,
                weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, steps);
        this.baseline = baseline == null ? signature : baseline;
        this.dirty = !this.baseline.equals(signature);
        this.page = page == null ? Page.TITLE : page;
        this.returnToSummary = returnToSummary;
    }

    public static EditorUiState closed() {
        return base(false, false, null);
    }

    public static EditorUiState create() {
        return create(TaskSlot.MORNING);
    }

    public static EditorUiState create(TaskSlot slot) {
        return new EditorUiState(true, false, false, null, "", slot,
                null, Recurrence.DAILY, 2, 0, TimeOfDay.fromSlot(slot).bit,
                TaskBoundKind.FOREVER, null, null,
                null, null, "", Collections.emptyList(), null, Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), Prompt.NONE, "", 1, null,
                Page.TITLE, false);
    }

    public static EditorUiState loading(String taskId) {
        return base(true, true, taskId);
    }

    private static EditorUiState base(boolean open, boolean loading, String taskId) {
        return new EditorUiState(open, loading, false, taskId, "", TaskSlot.MORNING,
                null, Recurrence.ONCE, 2, 0, 0, TaskBoundKind.FOREVER, null, null,
                null, null, "", Collections.emptyList(), null, Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), Prompt.NONE, "", 1, null,
                taskId == null ? Page.TITLE : Page.SUMMARY, false);
    }

    public static EditorUiState edit(TaskDetails details) {
        List<EditorStepState> steps = new ArrayList<>();
        for (TaskStepTemplate value : details.stepTemplates) steps.add(EditorStepState.from(value));
        return new EditorUiState(true, false, false, details.id.value, details.title,
                details.slot, details.estimatedMinutes, details.recurrence,
                details.intervalDays, details.weekdayMask, details.timeOfDayMask,
                details.boundKind, details.boundUntilOn, details.boundWeeks,
                details.remainingCount, details.deadlineOn, details.note, steps, null,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                Prompt.NONE, "", 1, null, Page.SUMMARY, false);
    }

    public EditorUiState withDraft(String title, TaskSlot slot, Recurrence recurrence,
                                   int intervalDays, int weekdayMask, List<String> labels) {
        List<EditorStepState> values = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            EditorStepState old = i < stepStates.size() ? stepStates.get(i)
                    : EditorStepState.blank(nextDraftIdentity + i);
            values.add(old.withText(labels.get(i)));
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
        return new EditorUiState(true, false, false, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, steps,
                expandedStepId, Collections.emptySet(), attemptedPages, attemptedStepIds,
                Prompt.NONE, "", nextDraftIdentity, baseline, page, returnToSummary);
    }

    public EditorUiState withFeedback(Set<ValidationIssue> issues, Prompt prompt,
                                      String storageError) {
        return new EditorUiState(open, loading, saving, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, stepStates,
                expandedStepId, issues, attemptedPages, attemptedStepIds, prompt, storageError,
                nextDraftIdentity, baseline, page, returnToSummary);
    }

    public EditorUiState withValidationAttempt(Page attemptedPage, String attemptedStepId,
                                               Set<ValidationIssue> visibleIssues) {
        Set<Page> pages = new LinkedHashSet<>(attemptedPages);
        Set<String> steps = new LinkedHashSet<>(attemptedStepIds);
        if (attemptedStepId == null) pages.add(attemptedPage);
        else steps.add(attemptedStepId);
        return new EditorUiState(open, loading, saving, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, stepStates,
                expandedStepId, visibleIssues, pages, steps, prompt, storageError,
                nextDraftIdentity, baseline, page, returnToSummary);
    }

    public EditorUiState withAllValidationAttempted(Set<ValidationIssue> visibleIssues) {
        Set<Page> pages = new LinkedHashSet<>(attemptedPages);
        pages.add(Page.TITLE); pages.add(Page.SCHEDULE); pages.add(Page.STEPS);
        Set<String> steps = new LinkedHashSet<>(attemptedStepIds);
        for (EditorStepState step : stepStates) steps.add(step.id);
        return new EditorUiState(open, loading, saving, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, stepStates,
                expandedStepId, visibleIssues, pages, steps, prompt, storageError,
                nextDraftIdentity, baseline, page, returnToSummary);
    }

    public EditorUiState withSaving(boolean value) {
        return new EditorUiState(open, loading, value, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, stepStates,
                expandedStepId, issues, attemptedPages, attemptedStepIds, prompt, storageError,
                nextDraftIdentity, baseline, page, returnToSummary);
    }

    public EditorUiState withPage(Page value, boolean returnToSummary) {
        return new EditorUiState(open, loading, saving, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, stepStates,
                expandedStepId, issues, attemptedPages, attemptedStepIds, prompt, storageError,
                nextDraftIdentity, baseline, value, returnToSummary);
    }

    public EditorUiState withExpandedStep(String id) {
        return new EditorUiState(open, loading, saving, taskId, title, slot, estimatedMinutes,
                recurrence, intervalDays, weekdayMask, timeOfDayMask, boundKind,
                boundUntilOn, boundWeeks, remainingCount, deadlineOn, note, stepStates,
                id, issues, attemptedPages, attemptedStepIds, prompt, storageError,
                nextDraftIdentity, baseline, page, returnToSummary);
    }

    public TaskDefinition definition() {
        List<TaskStepDefinition> definitions = new ArrayList<>();
        for (int i = 0; i < stepStates.size(); i++)
            definitions.add(stepStates.get(i).definition(i, recurrence == Recurrence.ONCE));
        return new TaskDefinition(title, estimatedMinutes, slot, recurrence, intervalDays,
                weekdayMask, timeOfDayMask, boundKind, boundUntilOn, boundWeeks,
                remainingCount, deadlineOn, note, definitions);
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean("open", open); bundle.putBoolean("loading", loading);
        bundle.putBoolean("saving", saving); bundle.putString("task_id", taskId);
        bundle.putString("title", title); bundle.putString("slot", slot.name());
        putInteger(bundle, "estimated", estimatedMinutes);
        bundle.putString("recurrence", recurrence.name()); bundle.putInt("interval", intervalDays);
        bundle.putInt("weekdays", weekdayMask); bundle.putInt("times", timeOfDayMask);
        bundle.putString("bound", boundKind.name()); putDate(bundle, "until", boundUntilOn);
        putInteger(bundle, "weeks", boundWeeks); putInteger(bundle, "count", remainingCount);
        putDate(bundle, "deadline", deadlineOn); bundle.putString("note", note);
        ArrayList<Bundle> values = new ArrayList<>();
        for (EditorStepState value : stepStates) values.add(value.toBundle());
        bundle.putParcelableArrayList("step_states", values);
        bundle.putString("expanded", expandedStepId);
        ArrayList<Bundle> issueValues = new ArrayList<>();
        for (ValidationIssue issue : issues) issueValues.add(issue.toBundle());
        bundle.putParcelableArrayList("validation_issues", issueValues);
        ArrayList<String> pages = new ArrayList<>();
        for (Page value : attemptedPages) pages.add(value.name());
        bundle.putStringArrayList("attempted_pages", pages);
        bundle.putStringArrayList("attempted_steps", new ArrayList<>(attemptedStepIds));
        bundle.putString("prompt", prompt.name());
        bundle.putString("storage_error", storageError); bundle.putInt("next_id", nextDraftIdentity);
        bundle.putString("baseline", baseline); bundle.putString("page", page.name());
        bundle.putBoolean("return_summary", returnToSummary);
        return bundle;
    }

    public static EditorUiState fromBundle(Bundle bundle) {
        if (bundle == null || !bundle.getBoolean("open", false)) return closed();
        List<EditorStepState> steps = new ArrayList<>();
        ArrayList<Bundle> values = bundle.getParcelableArrayList("step_states");
        if (values != null) for (Bundle value : values) steps.add(EditorStepState.fromBundle(value));
        Set<ValidationIssue> issues = issues(bundle);
        Set<Page> attemptedPages = pages(bundle.getStringArrayList("attempted_pages"));
        Set<String> attemptedSteps = strings(bundle.getStringArrayList("attempted_steps"));
        return new EditorUiState(true, bundle.getBoolean("loading"), bundle.getBoolean("saving"),
                bundle.getString("task_id"), bundle.getString("title", ""),
                enumValue(TaskSlot.class, bundle.getString("slot"), TaskSlot.MORNING),
                integer(bundle, "estimated"), enumValue(Recurrence.class,
                bundle.getString("recurrence"), Recurrence.ONCE), bundle.getInt("interval", 2),
                bundle.getInt("weekdays"), bundle.getInt("times"),
                enumValue(TaskBoundKind.class, bundle.getString("bound"), TaskBoundKind.FOREVER),
                date(bundle, "until"), integer(bundle, "weeks"), integer(bundle, "count"),
                date(bundle, "deadline"), bundle.getString("note", ""), steps,
                bundle.getString("expanded"), issues, attemptedPages, attemptedSteps,
                enumValue(Prompt.class,
                bundle.getString("prompt"), Prompt.NONE), bundle.getString("storage_error", ""),
                bundle.getInt("next_id", 1), bundle.getString("baseline"),
                enumValue(Page.class, bundle.getString("page"),
                        bundle.getString("task_id") == null ? Page.TITLE : Page.SUMMARY),
                bundle.getBoolean("return_summary"));
    }

    private static String signature(String title, TaskSlot slot, Integer estimated,
                                    Recurrence recurrence, int interval, int weekdays, int times,
                                    TaskBoundKind bound, LocalDate until, Integer weeks,
                                    Integer count, LocalDate deadline, String note,
                                    List<EditorStepState> steps) {
        StringBuilder result = new StringBuilder(Objects.toString(title, "").trim() + '|' + slot + '|' + estimated + '|'
                + recurrence + '|' + interval + '|' + weekdays + '|' + times + '|' + bound
                + '|' + until + '|' + weeks + '|' + count + '|' + deadline + '|'
                + Objects.toString(note, ""));
        for (EditorStepState step : steps)
            result.append('|').append(step.id).append(':').append(step.text).append(':')
                    .append(step.cadenceMode).append(':').append(step.weekdayMask).append(':')
                    .append(step.intervalDays).append(':')
                    .append(step.amount).append(':')
                    .append(step.note);
        return result.toString();
    }

    private static void putInteger(Bundle bundle, String key, Integer value) {
        if (value != null) { bundle.putBoolean(key + "_set", true); bundle.putInt(key, value); }
    }
    private static Integer integer(Bundle bundle, String key) {
        return bundle.getBoolean(key + "_set") ? bundle.getInt(key) : null;
    }
    private static void putDate(Bundle bundle, String key, LocalDate value) {
        if (value != null) bundle.putString(key, value.toString());
    }
    private static LocalDate date(Bundle bundle, String key) {
        String value = bundle.getString(key);
        return value == null || value.isEmpty() ? null : LocalDate.parse(value);
    }
    private static Set<ValidationIssue> issues(Bundle bundle) {
        Set<ValidationIssue> result = new LinkedHashSet<>();
        ArrayList<Bundle> values = bundle.getParcelableArrayList("validation_issues");
        if (values != null) for (Bundle value : values) {
            ValidationIssue issue = ValidationIssue.fromBundle(value);
            if (issue != null) result.add(issue);
        }
        if (!result.isEmpty() || bundle.containsKey("validation_issues")) return result;
        ArrayList<String> legacy = bundle.getStringArrayList("errors");
        if (legacy != null) for (String value : legacy) {
            ValidationIssue issue = legacyIssue(value);
            if (issue != null) result.add(issue);
        }
        return result;
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
    private static Set<Page> pages(ArrayList<String> values) {
        Set<Page> result = new LinkedHashSet<>();
        if (values != null) for (String value : values)
            result.add(enumValue(Page.class, value, Page.TITLE));
        return result;
    }
    private static Set<String> strings(ArrayList<String> values) {
        return values == null ? Collections.emptySet() : new LinkedHashSet<>(values);
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { return fallback; }
    }
}
