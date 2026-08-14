package com.autosecretary.ui;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.StepCompletion;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Maps application data into complete presentation rows before adapter binding. */
public final class UiModelMapper {
    private UiModelMapper() { }

    public static Dashboard dashboard(
            DashboardData data,
            com.autosecretary.application.TodayTimeline timeline,
            ZoneId zone,
            String hiddenCalendarTitle) {
        LocalDate today = timeline.day();
        List<TodayRow> todayRows = new TodayPresenter().present(
                data, timeline, zone, hiddenCalendarTitle).rows();
        List<WorkItemRow> rows = data.workItems().stream()
                .map(item -> workItemRow(item, data.stepCompletions(),
                        data.completions(), today))
                .collect(java.util.stream.Collectors.toList());
        return new Dashboard(todayRows, rows);
    }

    private static WorkItemRow workItemRow(
            WorkItem item,
            List<StepCompletion> stepCompletions,
            List<com.autosecretary.application.CompletionRecord> completions,
            LocalDate today) {
        String occurrenceKey = item instanceof Routine routine
                ? routine.nextDueDate().toString() : "TASK";
        List<StepRow> steps = stepRows(item, occurrenceKey, today, stepCompletions);
        int completedSteps = (int) steps.stream().filter(StepRow::completed).count();
        boolean completed = item instanceof Task task && task.completed();
        return new WorkItemRow(item.id(), item instanceof Routine, item.title(),
                group(item, completions, today), metadata(item, completions, today),
                item.isOpenOn(today), completed,
                completedSteps, steps.size(), completed
                ? completionDate(item.id(), completions) : null);
    }

    static List<StepRow> stepRows(
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

    private static String metadata(
            WorkItem item,
            List<com.autosecretary.application.CompletionRecord> completions,
            LocalDate today) {
        if (item instanceof Routine routine) {
            String streak = routine.stats().currentStreak() > 0
                    ? " · " + routine.stats().currentStreak() + " Ringe" : "";
            String due = routine.nextDueDate().isBefore(today) ? "seit gestern offen"
                    : routine.nextDueDate().equals(today) ? "heute fällig"
                    : routine.nextDueDate().equals(today.plusDays(1)) ? "morgen"
                    : "wieder in " + java.time.temporal.ChronoUnit.DAYS.between(
                            today, routine.nextDueDate()) + " Tagen";
            return "alle " + routine.cadenceDays() + " Tage · " + due + streak;
        }
        Task task = (Task) item;
        if (task.completed()) return completionGroup(item.id(), completions, today);
        String duration = "ca. " + task.durationMinutes() + " Min";
        if (task.deadlineAt() == null) return duration + " · ohne Termin";
        LocalDate deadline = task.deadlineAt().toLocalDate();
        if (deadline.isBefore(today)) return duration + " · seit gestern offen";
        if (deadline.equals(today)) return duration + " · heute";
        if (deadline.equals(today.plusDays(1))) return duration + " · morgen";
        if (!deadline.isAfter(today.plusDays(7))) return duration + " · diese Woche";
        return duration + " · mit Deadline";
    }

    private static String group(
            WorkItem item,
            List<com.autosecretary.application.CompletionRecord> completions,
            LocalDate today) {
        if (item instanceof Task task && task.completed()) {
            return completionGroup(item.id(), completions, today);
        }
        if (item.deadlineAt() != null && item.deadlineAt().toLocalDate().isBefore(today)) {
            return "überfällig";
        }
        if (item instanceof Routine routine) {
            if (routine.nextDueDate().isBefore(today)) return "überfällig";
            if (routine.isOpenOn(today)) return "heute fällig";
            return !routine.nextDueDate().isAfter(today.plusDays(7))
                    ? "diese Woche" : "seltener";
        }
        if (item.deadlineAt() == null) return "ohne Termin";
        return item.deadlineAt().toLocalDate().equals(today) ? "heute" : "diese Woche";
    }

    private static String completionGroup(
            String itemId,
            List<com.autosecretary.application.CompletionRecord> completions,
            LocalDate today) {
        LocalDate completed = completionDate(itemId, completions);
        if (completed == null) completed = today;
        if (completed.equals(today)) return "heute";
        if (completed.equals(today.minusDays(1))) return "gestern";
        return "älter";
    }

    private static LocalDate completionDate(
            String itemId,
            List<com.autosecretary.application.CompletionRecord> completions) {
        return completions.stream()
                .filter(value -> value.workItemId().equals(itemId))
                .map(value -> value.completedAt().toLocalDate())
                .max(LocalDate::compareTo).orElse(null);
    }

}
