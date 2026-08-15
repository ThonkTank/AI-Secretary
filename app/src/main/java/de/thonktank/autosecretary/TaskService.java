package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.RoomTaskRepository;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RoutineProgress;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Compatibility facade while commands are extracted into individual use cases. */
public final class TaskService {
    private final TaskRepository repository;
    private final Clock clock;

    public TaskService(AppDatabase database) {
        this(new RoomTaskRepository(database), new SystemClock());
    }

    TaskService(AppDatabase database, Clock clock) {
        this(new RoomTaskRepository(database), clock);
    }

    TaskService(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DashboardState dashboard() {
        repository.inTransaction(() -> materializeDueTasks(clock.today()));
        return buildDashboard(clock.today());
    }

    public void create(String title, TaskSlot slot, Recurrence recurrence, int intervalDays,
                       int weekdayMask, List<String> steps, boolean ongoing, String condition) {
        validate(title, recurrence, weekdayMask, ongoing, condition);
        LocalDate today = clock.today();
        repository.inTransaction(() -> {
            TaskId id = TaskId.of(UUID.randomUUID().toString());
            Task task = Task.create(id, title, slot, recurrence, intervalDays, weekdayMask,
                    ongoing, condition, today, repository.nextTaskOrder(slot));
            repository.insertTask(task);
            insertTemplates(task.id, steps);
            materializeDueTasks(today);
        });
    }

    public void update(String taskId, String title, TaskSlot slot) {
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        repository.inTransaction(() -> {
            Task task = repository.findTask(TaskId.of(taskId));
            if (task == null) return;
            long order = task.slot == slot ? task.displayOrder : repository.nextTaskOrder(slot);
            repository.updateTask(task.edit(title, slot, order));
        });
    }

    public void move(String taskId, TaskSlot slot) {
        repository.inTransaction(() -> {
            Task task = repository.findTask(TaskId.of(taskId));
            if (task != null) repository.updateTask(task.move(slot, repository.nextTaskOrder(slot)));
        });
    }

    public void delete(String taskId) {
        repository.inTransaction(() -> repository.deleteTask(TaskId.of(taskId)));
    }

    public void toggleStep(String stepId) {
        repository.inTransaction(() -> {
            OccurrenceStep step = repository.findOccurrenceStep(stepId);
            if (step == null) return;
            Occurrence occurrence = repository.findOccurrence(step.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            repository.updateOccurrenceStep(step.toggle());
        });
    }

    public void complete(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return;
        repository.inTransaction(() -> {
            Occurrence occurrence = repository.findOccurrence(occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            for (OccurrenceStep step : repository.occurrenceSteps(occurrenceId))
                if (!step.done) repository.updateOccurrenceStep(step.complete());
            Task task = repository.findTask(occurrence.taskId);
            if (task != null) completeOccurrence(occurrence, task, clock.today());
        });
    }

    public void defer(String occurrenceOrTaskId) {
        repository.inTransaction(() -> {
            List<TaskSnapshot> open = new ArrayList<>();
            for (TaskSnapshot item : buildDashboard(clock.today()).tasks) if (!item.done) open.add(item);
            int index = -1;
            for (int i = 0; i < open.size(); i++)
                if (occurrenceOrTaskId.equals(open.get(i).occurrenceId)
                        || occurrenceOrTaskId.equals(open.get(i).taskId)) {
                    index = i;
                    break;
                }
            if (index < 0 || index >= open.size() - 1) return;
            Task first = repository.findTask(TaskId.of(open.get(index).taskId));
            Task second = repository.findTask(TaskId.of(open.get(index + 1).taskId));
            if (first == null || second == null) return;
            repository.updateTask(first.withDisplayOrder(second.displayOrder));
            repository.updateTask(second.withDisplayOrder(first.displayOrder));
        });
    }

    public void closeOngoingTask(String taskId) {
        repository.inTransaction(() -> {
            Task task = repository.findTask(TaskId.of(taskId));
            if (task == null || !task.ongoing || task.conditionText.isEmpty()
                    || task.conditionDone) return;
            LocalDate today = clock.today();
            Occurrence open = repository.openOccurrence(task.id);
            if (open != null) {
                for (OccurrenceStep step : repository.occurrenceSteps(open.id))
                    if (!step.done) repository.updateOccurrenceStep(step.complete());
                completeOccurrence(open, task, today);
                task = repository.findTask(task.id);
            } else {
                awardXp();
            }
            repository.updateTask(task.closeCondition(today));
        });
    }

    private void validate(String title, Recurrence recurrence, int weekdayMask,
                          boolean ongoing, String condition) {
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        if (recurrence == Recurrence.WEEKDAYS && !ScheduleCalculator.hasWeekday(weekdayMask))
            throw new IllegalArgumentException("geht so nicht: Wähle mindestens einen Wochentag.");
        if (ongoing && (condition == null || condition.trim().isEmpty()))
            throw new IllegalArgumentException("geht so nicht: Ein fortlaufendes Vorhaben braucht eine Bedingung.");
    }

    private void insertTemplates(TaskId taskId, List<String> steps) {
        List<TaskStepTemplate> templates = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++)
            if (steps.get(i) != null && !steps.get(i).trim().isEmpty())
                templates.add(new TaskStepTemplate(UUID.randomUUID().toString(), taskId, i, steps.get(i)));
        repository.insertTemplates(templates);
    }

    private void materializeDueTasks(LocalDate today) {
        for (Task task : repository.activeTasks()) {
            if (task.displayOrder == 0) {
                task = task.withDisplayOrder(repository.nextTaskOrder(task.slot));
                repository.updateTask(task);
            }
            if (repository.openOccurrence(task.id) != null || !ScheduleCalculator.isDue(task, today)) continue;
            Occurrence occurrence = new Occurrence(UUID.randomUUID().toString(), task.id,
                    task.nextDueOn, OccurrenceState.OPEN,
                    repository.nextOpenOccurrenceOrder(task.slot), null);
            repository.insertOccurrence(occurrence);
            List<OccurrenceStep> copied = new ArrayList<>();
            for (TaskStepTemplate step : repository.templates(task.id))
                copied.add(new OccurrenceStep(UUID.randomUUID().toString(), occurrence.id,
                        step.position, step.text, false));
            repository.insertOccurrenceSteps(copied);
        }
    }

    private void completeOccurrence(Occurrence occurrence, Task task, LocalDate completedOn) {
        repository.updateOccurrence(occurrence.complete(completedOn));
        awardXp();
        RoutineProgress progress = task.routineProgress;
        if (task.recurrence != Recurrence.ONCE) {
            boolean onTime = occurrence.scheduledOn.equals(completedOn);
            boolean previousOnTime = task.hasCompletedOccurrence
                    && task.lastScheduledOn != null
                    && task.lastScheduledOn.equals(task.lastCompletedOn);
            progress = progress.recordCompletion(onTime, previousOnTime, completedOn);
        }
        LocalDate next = ScheduleCalculator.nextDue(task, completedOn);
        boolean archive = !task.ongoing && task.recurrence == Recurrence.ONCE;
        repository.updateTask(task.afterOccurrence(
                occurrence.scheduledOn, completedOn, next, progress, archive));
    }

    private void awardXp() {
        repository.setXp(repository.xp() + 10);
    }

    private DashboardState buildDashboard(LocalDate today) {
        Map<TaskId, Task> tasks = new HashMap<>();
        for (Task task : repository.allTasks()) tasks.put(task.id, task);
        List<TaskSnapshot> result = new ArrayList<>();
        Set<TaskId> included = new HashSet<>();
        for (Occurrence occurrence : repository.openOccurrences()) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null || task.archived || task.conditionDone) continue;
            result.add(snapshot(task, occurrence, false, today));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.ongoing && !task.conditionText.isEmpty() && !task.archived
                    && !task.conditionDone && !included.contains(task.id)) {
                result.add(snapshot(task, null, false, today));
                included.add(task.id);
            }
        for (Occurrence occurrence : repository.completedOccurrences(today)) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null || included.contains(task.id)) continue;
            result.add(snapshot(task, occurrence, true, today));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.archived && today.equals(task.lastCompletedOn) && !included.contains(task.id))
                result.add(snapshot(task, null, true, today));
        Collections.sort(result, Comparator.comparingLong(item -> item.displayOrder));
        return new DashboardState(repository.xp(), result);
    }

    private TaskSnapshot snapshot(Task task, Occurrence occurrence, boolean done, LocalDate today) {
        List<TaskStepSnapshot> steps = new ArrayList<>();
        int remaining = 0;
        String next = task.conditionText;
        if (occurrence != null)
            for (OccurrenceStep step : repository.occurrenceSteps(occurrence.id)) {
                boolean stepDone = done || step.done;
                steps.add(new TaskStepSnapshot(step.id, step.text, stepDone));
                if (!stepDone) {
                    remaining++;
                    if (remaining == 1) next = step.text;
                }
            }
        if (next == null || next.isEmpty())
            next = steps.isEmpty() ? "Als erledigt markieren" : "Alles erledigt";
        boolean overdue = occurrence != null && !done && occurrence.scheduledOn.isBefore(today);
        return new TaskSnapshot(task.id.value, occurrence == null ? "" : occurrence.id,
                task.title, task.slot, softTime(task.slot, task.ongoing), next, task.recurrence,
                steps, remaining, !task.conditionText.isEmpty(), task.ongoing, done, overdue,
                task.recurrence == Recurrence.ONCE ? 0 : task.routineProgress.weekStreak,
                task.displayOrder);
    }

    static String softTime(TaskSlot slot, boolean ongoing) {
        if (ongoing) return "fortlaufend, bis die Bedingung erfüllt ist";
        if (slot == TaskSlot.MORNING) return "heute am Morgen";
        if (slot == TaskSlot.MIDDAY) return "um die Mittagszeit";
        if (slot == TaskSlot.EVENING) return "heute am Abend";
        return "später, sobald Platz ist";
    }
}
