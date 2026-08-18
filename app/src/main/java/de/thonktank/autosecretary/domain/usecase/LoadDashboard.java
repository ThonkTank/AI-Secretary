package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import de.thonktank.autosecretary.domain.model.ComboProgress;

public final class LoadDashboard {
    private final TaskRepository repository;

    public LoadDashboard(TaskRepository repository) {
        this.repository = repository;
    }

    public Dashboard execute(LocalDate today) {
        Map<TaskId, Task> tasks = new HashMap<>();
        for (Task task : repository.allTasks()) tasks.put(task.id, task);
        List<Occurrence> open = repository.openOccurrences();
        List<Occurrence> completed = repository.completedOccurrences(today);
        List<String> occurrenceIds = new ArrayList<>();
        for (Occurrence occurrence : open) occurrenceIds.add(occurrence.id);
        for (Occurrence occurrence : completed) occurrenceIds.add(occurrence.id);
        Map<String, List<OccurrenceStep>> steps = groupSteps(
                repository.occurrenceStepsFor(occurrenceIds));

        List<DashboardTask> result = new ArrayList<>();
        Set<TaskId> included = new HashSet<>();
        for (Occurrence occurrence : open) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null || task.archived || task.conditionDone) continue;
            result.add(item(task, occurrence, steps, false));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.ongoing && !task.conditionText.isEmpty() && !task.archived
                    && !task.conditionDone && !included.contains(task.id)) {
                result.add(new DashboardTask(task, null, new ArrayList<>(), false));
                included.add(task.id);
            }
        for (Occurrence occurrence : completed) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null) continue;
            result.add(item(task, occurrence, steps, true));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.archived && today.equals(task.lastCompletedOn) && !included.contains(task.id))
                result.add(new DashboardTask(task, null, new ArrayList<>(), true));
        result.sort(Comparator.comparingInt((DashboardTask item) -> item.done ? 1 : 0)
                .thenComparing(item -> item.occurrence == null
                        ? LocalDate.MAX : item.occurrence.scheduledOn)
                .thenComparingInt(item -> item.occurrence == null
                        ? item.task.slot.rank : item.occurrence.slot.rank)
                .thenComparingInt(item -> item.occurrence == null
                        ? Integer.MAX_VALUE : item.occurrence.sortOrder)
                .thenComparingLong(item -> item.task.displayOrder));
        Map<String, ComboProgress> combos = new HashMap<>();
        for (ComboProgress combo : repository.combos()) combos.put(combo.ownerId, combo);
        return new Dashboard(repository.xp(), result, combos);
    }

    private static DashboardTask item(Task task, Occurrence occurrence,
                                      Map<String, List<OccurrenceStep>> steps, boolean done) {
        List<OccurrenceStep> values = steps.get(occurrence.id);
        return new DashboardTask(task, occurrence,
                values == null ? new ArrayList<>() : values, done);
    }

    private static Map<String, List<OccurrenceStep>> groupSteps(List<OccurrenceStep> values) {
        Map<String, List<OccurrenceStep>> result = new HashMap<>();
        for (OccurrenceStep step : values)
            result.computeIfAbsent(step.occurrenceId, ignored -> new ArrayList<>()).add(step);
        return result;
    }
}
