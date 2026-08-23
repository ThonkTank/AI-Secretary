package de.thonktank.autosecretary.editor;

import de.thonktank.autosecretary.EditorStepState;
import de.thonktank.autosecretary.EditorUiState;
import de.thonktank.autosecretary.ValidationIssue;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure transitions for the complete editor, independent from Android views and callbacks. */
public final class TaskEditorStateReducer {
    private TaskEditorStateReducer() { }

    public static EditorUiState addStep(EditorUiState state) {
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        EditorStepState added = EditorStepState.blank(state.nextDraftIdentity);
        steps.add(added);
        return replace(state, steps, added.id, state.nextDraftIdentity + 1);
    }

    public static EditorUiState updateStep(EditorUiState state, int index,
                                           EditorStepState step) {
        if (index < 0 || index >= state.stepStates.size()) return state;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        steps.set(index, step);
        return replace(state, steps, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState removeStep(EditorUiState state, int index) {
        if (index < 0 || index >= state.stepStates.size()) return state;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        steps.remove(index);
        return replace(state, steps, null, state.nextDraftIdentity);
    }

    public static EditorUiState moveStep(EditorUiState state, int from, int to) {
        if (from < 0 || from >= state.stepStates.size()
                || to < 0 || to >= state.stepStates.size() || from == to) return state;
        List<EditorStepState> steps = new ArrayList<>(state.stepStates);
        EditorStepState moved = steps.remove(from);
        steps.add(to, moved);
        return replace(state, steps, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState expandStep(EditorUiState state, String id) {
        return replace(state, state.stepStates, id, state.nextDraftIdentity);
    }

    public static EditorUiState updateTitle(EditorUiState state, String value) {
        return replaceDraft(state, value, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState updateNote(EditorUiState state, String value) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes,
                state.recurrence, state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                state.boundKind, state.boundUntilOn, state.boundWeeks, state.remainingCount,
                state.deadlineOn, value, state.stepStates, state.expandedStepId,
                state.nextDraftIdentity);
    }

    public static EditorUiState updateWeekdays(EditorUiState state, int value) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes,
                state.recurrence, state.intervalDays, value, state.timeOfDayMask,
                state.boundKind, state.boundUntilOn, state.boundWeeks, state.remainingCount,
                state.deadlineOn, state.note, state.stepStates, state.expandedStepId,
                state.nextDraftIdentity);
    }

    public static EditorUiState updateDuration(EditorUiState state, Integer value) {
        return replaceDraft(state, state.title, state.slot, value, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState updateInterval(EditorUiState state, int value) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes,
                state.recurrence, value, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState updateRecurrence(EditorUiState state, Recurrence recurrence) {
        int times = recurrence == Recurrence.ONCE ? 0 : state.timeOfDayMask == 0
                ? TimeOfDay.fromSlot(state.slot).bit : state.timeOfDayMask;
        int weekdays = recurrence == Recurrence.WEEKDAYS
                ? state.weekdayMask == 0 ? 1 : state.weekdayMask : 0;
        LocalDate deadline = recurrence == Recurrence.ONCE
                && state.boundKind == TaskBoundKind.UNTIL_DATE ? state.boundUntilOn
                : recurrence == Recurrence.ONCE ? state.deadlineOn : null;
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes, recurrence,
                recurrence == Recurrence.INTERVAL ? Math.max(2, state.intervalDays) : 1,
                weekdays, times, TaskBoundKind.FOREVER, null, null, null, deadline,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState toggleTime(EditorUiState state, TimeOfDay value) {
        int times = state.timeOfDayMask ^ value.bit;
        if (times == 0) return state;
        TaskSlot slot = TimeOfDay.earliestSlot(times, state.slot);
        return replaceDraft(state, state.title, slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, times, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, state.stepStates, state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState updateDeadline(EditorUiState state, LocalDate value) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes,
                state.recurrence, state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                state.boundKind, state.boundUntilOn, state.boundWeeks, state.remainingCount,
                value, state.note, state.stepStates, state.expandedStepId,
                state.nextDraftIdentity);
    }

    public static EditorUiState updateBoundKind(EditorUiState state, TaskBoundKind kind,
                                                 LocalDate today) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes,
                state.recurrence, state.intervalDays, state.weekdayMask, state.timeOfDayMask, kind,
                kind == TaskBoundKind.UNTIL_DATE ? today
                        : kind == TaskBoundKind.FOR_WEEKS ? today.plusWeeks(1) : null,
                kind == TaskBoundKind.FOR_WEEKS ? 1 : null,
                kind == TaskBoundKind.N_TIMES ? 1 : null, null, state.note, state.stepStates,
                state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState updateBound(EditorUiState state, LocalDate until,
                                             Integer weeks, Integer count) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes,
                state.recurrence, state.intervalDays, state.weekdayMask, state.timeOfDayMask,
                state.boundKind, until, weeks, count, null, state.note, state.stepStates,
                state.expandedStepId, state.nextDraftIdentity);
    }

    public static EditorUiState navigate(EditorUiState state, EditorUiState.Page page,
                                         boolean returnToSummary) {
        return state.withPage(page, returnToSummary).withExpandedStep(null);
    }

    public static EditorUiState feedback(EditorUiState state, Set<ValidationIssue> issues,
                                         EditorUiState.Prompt prompt, String storageError) {
        return state.withFeedback(issues, prompt, storageError);
    }

    public static EditorUiState validationAttempt(EditorUiState state,
                                                   EditorUiState.Page page, String stepId,
                                                   Set<ValidationIssue> issues) {
        return state.withValidationAttempt(page, stepId, issues);
    }

    public static EditorUiState liveValidation(EditorUiState state,
                                               Set<ValidationIssue> allIssues) {
        if (state.attemptedPages.isEmpty() && state.attemptedStepIds.isEmpty()) return state;
        Set<ValidationIssue> visible = new LinkedHashSet<>();
        for (ValidationIssue issue : allIssues) {
            if ((issue.stepId == null && state.attemptedPages.contains(issue.field.page))
                    || (issue.stepId != null && (state.attemptedPages.contains(
                    EditorUiState.Page.STEPS)
                    || state.attemptedStepIds.contains(issue.stepId)))) visible.add(issue);
        }
        return feedback(state, visible, state.prompt, state.storageError);
    }

    public static EditorUiState advance(EditorUiState state, Set<ValidationIssue> allIssues) {
        if (!issuesForPage(allIssues, state.page, null).isEmpty())
            return validationAttempt(state, state.page, null, allIssues);
        EditorUiState.Page target = state.returnToSummary ? EditorUiState.Page.SUMMARY
                : next(state.page);
        return navigate(state, target, false);
    }

    public static EditorUiState applyStepDetail(EditorUiState state,
                                                Set<ValidationIssue> allIssues) {
        if (!issuesForPage(allIssues, EditorUiState.Page.STEPS,
                state.expandedStepId).isEmpty())
            return validationAttempt(state, EditorUiState.Page.STEPS,
                    state.expandedStepId, allIssues);
        return expandStep(state, null);
    }

    public static boolean hasVisibleBlockingIssue(EditorUiState state, boolean detail) {
        return !issuesForPage(state.issues, state.page,
                detail ? state.expandedStepId : null).isEmpty();
    }

    public static EditorUiState routeValidationFailure(EditorUiState state,
                                                        Set<ValidationIssue> issues) {
        return routeValidationFailure(state, issues, firstIssuePage(issues),
                firstStepIssue(issues));
    }

    public static EditorUiState routeValidationFailure(EditorUiState state,
                                                        Set<ValidationIssue> issues,
                                                        EditorUiState.Page page,
                                                        String stepId) {
        EditorUiState next = state.withAllValidationAttempted(issues).withPage(page, true);
        return stepId == null ? next.withExpandedStep(null) : next.withExpandedStep(stepId);
    }

    public static EditorUiState allValidationAttempted(EditorUiState state,
                                                       Set<ValidationIssue> issues) {
        return state.withAllValidationAttempted(issues);
    }

    public static EditorUiState saving(EditorUiState state, boolean value) {
        return state.withSaving(value);
    }

    private static EditorUiState replace(EditorUiState state, List<EditorStepState> steps,
                                         String expandedStepId, int nextDraftIdentity) {
        return replaceDraft(state, state.title, state.slot, state.estimatedMinutes, state.recurrence,
                state.intervalDays, state.weekdayMask, state.timeOfDayMask, state.boundKind,
                state.boundUntilOn, state.boundWeeks, state.remainingCount, state.deadlineOn,
                state.note, steps, expandedStepId, nextDraftIdentity);
    }

    private static EditorUiState replaceDraft(EditorUiState state, String title, TaskSlot slot,
                                               Integer estimatedMinutes, Recurrence recurrence,
                                               int intervalDays, int weekdayMask,
                                               int timeOfDayMask, TaskBoundKind boundKind,
                                               LocalDate boundUntilOn, Integer boundWeeks,
                                               Integer remainingCount, LocalDate deadlineOn,
                                               String note, List<EditorStepState> steps,
                                               String expandedStepId, int nextDraftIdentity) {
        return state.draft(title, slot, estimatedMinutes, recurrence, intervalDays, weekdayMask,
                timeOfDayMask, boundKind, boundUntilOn, boundWeeks, remainingCount, deadlineOn,
                note, steps, expandedStepId, nextDraftIdentity);
    }

    private static Set<ValidationIssue> issuesForPage(Set<ValidationIssue> all,
                                                      EditorUiState.Page page,
                                                      String stepId) {
        Set<ValidationIssue> result = new LinkedHashSet<>();
        for (ValidationIssue issue : all)
            if (issue.belongsTo(page) && (stepId == null || issue.belongsToStep(stepId)))
                result.add(issue);
        return Collections.unmodifiableSet(result);
    }

    private static EditorUiState.Page firstIssuePage(Set<ValidationIssue> issues) {
        for (ValidationIssue issue : issues)
            if (issue.belongsTo(EditorUiState.Page.TITLE)) return EditorUiState.Page.TITLE;
        for (ValidationIssue issue : issues)
            if (issue.belongsTo(EditorUiState.Page.SCHEDULE)) return EditorUiState.Page.SCHEDULE;
        return EditorUiState.Page.STEPS;
    }

    private static String firstStepIssue(Set<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) if (issue.stepId != null) return issue.stepId;
        return null;
    }

    private static EditorUiState.Page next(EditorUiState.Page page) {
        if (page == EditorUiState.Page.TITLE) return EditorUiState.Page.SCHEDULE;
        if (page == EditorUiState.Page.SCHEDULE) return EditorUiState.Page.STEPS;
        return EditorUiState.Page.SUMMARY;
    }
}
