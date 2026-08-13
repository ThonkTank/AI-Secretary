package com.autosecretary.ui;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.StepCompletion;
import com.autosecretary.domain.BusyInterval;
import com.autosecretary.domain.PlanAssignment;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimePreference;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Maps application data into complete presentation rows before adapter binding. */
public final class UiModelMapper {
    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

    private UiModelMapper() { }

    public static Dashboard dashboard(DashboardData data, LocalDate today) {
        List<FocusRow> focus = data.focus().stream()
                .map(assignment -> focusRow(assignment, data, today))
                .collect(java.util.stream.Collectors.toList());
        List<WorkItemRow> rows = data.workItems().stream()
                .map(item -> workItemRow(item, data.stepCompletions(), today))
                .collect(java.util.stream.Collectors.toList());
        List<CalendarRow> calendar = data.calendar().stream()
                .map(item -> new CalendarRow(item.start(), item.end(), item.title()))
                .collect(java.util.stream.Collectors.toList());
        return new Dashboard(focus, rows, calendar);
    }

    private static FocusRow focusRow(
            PlanAssignment assignment, DashboardData data, LocalDate today) {
        WorkItem item = assignment.workItem();
        List<StepRow> steps = stepRows(item, assignment.occurrenceKey(),
                assignment.start().toLocalDate(), data.stepCompletions());
        String preceding = data.calendar().stream()
                .filter(value -> !value.end().isAfter(assignment.start()))
                .filter(value -> value.end().toLocalDate().equals(assignment.start().toLocalDate()))
                .reduce((left, right) -> right).map(BusyInterval::title).orElse(null);
        return new FocusRow(item.id(), item.title(), item.durationMinutes(),
                assignment.start(), assignment.end(), steps, preceding);
    }

    private static WorkItemRow workItemRow(
            WorkItem item, List<StepCompletion> completions, LocalDate today) {
        String occurrenceKey = item instanceof Routine routine
                ? routine.nextDueDate().toString() : "TASK";
        List<StepRow> steps = stepRows(item, occurrenceKey, today, completions);
        int completedSteps = (int) steps.stream().filter(StepRow::completed).count();
        boolean completed = item instanceof Task task && task.completed();
        return new WorkItemRow(item.id(), item instanceof Routine, item.title(),
                group(item, today), metadata(item), item.isOpenOn(today), completed,
                completedSteps, steps.size());
    }

    private static List<StepRow> stepRows(
            WorkItem item,
            String occurrenceKey,
            LocalDate effectiveDay,
            List<StepCompletion> completions) {
        LocalDate stepDay = item instanceof Routine routine ? routine.nextDueDate() : effectiveDay;
        return item.steps().stream().filter(step -> step.appliesOn(stepDay.getDayOfWeek()))
                .map(step -> new StepRow(step.id(), step.title(), completions.stream()
                        .anyMatch(value -> value.stepId().equals(step.id())
                                && value.occurrenceKey().equals(occurrenceKey))))
                .collect(java.util.stream.Collectors.toList());
    }

    private static String metadata(WorkItem item) {
        String preference = preference(item.timePreference());
        String flexibility = item.flexible() ? " · flexibel" : "";
        if (item instanceof Routine routine) {
            String streak = routine.stats().currentStreak() > 0
                    ? " · " + routine.stats().currentStreak() + " Ringe" : "";
            return "alle " + routine.cadenceDays() + " Tage · nächste Fälligkeit "
                    + routine.nextDueDate() + preference + flexibility + streak;
        }
        Task task = (Task) item;
        if (task.completed()) return "Erledigt";
        if (task.deadlineAt() == null) return "ca. " + task.durationMinutes()
                + " Min · ohne Deadline" + preference + flexibility;
        return "ca. " + task.durationMinutes() + " Min · bis "
                + task.deadlineAt().format(DEADLINE) + preference + flexibility;
    }

    private static String group(WorkItem item, LocalDate today) {
        if (item instanceof Task task && task.completed()) return "heute erledigt";
        if (item.deadlineAt() != null && item.deadlineAt().toLocalDate().isBefore(today)) {
            return "überfällig";
        }
        if (item instanceof Routine routine) {
            if (routine.nextDueDate().isBefore(today)) return "überfällig";
            return routine.isOpenOn(today) ? "heute fällig" : "seltener";
        }
        if (item.deadlineAt() == null) return "ohne Termin";
        return item.deadlineAt().toLocalDate().equals(today) ? "heute" : "diese Woche";
    }

    private static String preference(TimePreference value) {
        if (value == null) return "";
        return switch (value) {
            case MORNING -> " · morgens";
            case MIDDAY -> " · mittags";
            case EVENING -> " · abends";
        };
    }
}
