package de.thonktank.autosecretary;

import androidx.room.RoomDatabase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Transactional domain layer. Activity and widget must only invoke this class for task actions. */
public final class TaskService {
    private final AppDatabase db;
    private final TaskDao dao;
    private final Clock clock;

    public TaskService(AppDatabase db) { this(db, new SystemClock()); }
    TaskService(AppDatabase db, Clock clock) { this.db = db; this.dao = db.tasks(); this.clock = clock; }

    public DashboardState dashboard() {
        db.runInTransaction(() -> materializeDueTasks(clock.today()));
        return buildDashboard();
    }

    public void create(String title, String slot, String recurrence, int intervalDays, int weekdayMask,
                       List<String> steps, boolean ongoing, String condition) {
        LocalDate today = clock.today();
        if ("WEEKDAYS".equals(recurrence) && !ScheduleCalculator.hasWeekday(weekdayMask)) throw new IllegalArgumentException("Wähle mindestens einen Wochentag.");
        db.runInTransaction(() -> {
            String id = UUID.randomUUID().toString();
            TaskEntity task = new TaskEntity(id, title.trim(), slot, recurrence, Math.max(1, intervalDays), weekdayMask,
                    ongoing, condition.trim(), false, false, today.toString(), "", "", 1, 0, false);
            dao.insertTask(task);
            List<TaskStepEntity> templates = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) if (!steps.get(i).trim().isEmpty()) templates.add(new TaskStepEntity(UUID.randomUUID().toString(), id, i, steps.get(i).trim()));
            if (!templates.isEmpty()) dao.insertTemplates(templates);
            materializeDueTasks(today);
        });
    }

    public void completeNextStep(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return;
        db.runInTransaction(() -> {
            OccurrenceEntity occurrence = dao.occurrence(occurrenceId); if (occurrence == null || !"OPEN".equals(occurrence.state)) return;
            List<OccurrenceStepEntity> steps = dao.occurrenceSteps(occurrenceId);
            for (OccurrenceStepEntity step : steps) if (!step.done) { step.done = true; dao.updateOccurrenceStep(step); break; }
            boolean complete = steps.isEmpty();
            if (!complete) { complete = true; for (OccurrenceStepEntity step : dao.occurrenceSteps(occurrenceId)) if (!step.done) { complete = false; break; } }
            if (complete) completeOccurrence(occurrence, dao.task(occurrence.taskId), clock.today());
        });
    }

    public void defer(String occurrenceId) {
        db.runInTransaction(() -> {
            DashboardState state = buildDashboard();
            TaskSnapshot current = null; for (TaskSnapshot item : state.tasks) if (occurrenceId.equals(item.occurrenceId)) { current = item; break; }
            if (current == null) return;
            List<TaskSnapshot> sameSlot = new ArrayList<>(); for (TaskSnapshot item : state.tasks) if (item.slot.equals(current.slot) && !item.occurrenceId.isEmpty()) sameSlot.add(item);
            int index = sameSlot.indexOf(current); if (index < 0 || index >= sameSlot.size() - 1) return;
            OccurrenceEntity first = dao.occurrence(current.occurrenceId); OccurrenceEntity second = dao.occurrence(sameSlot.get(index + 1).occurrenceId);
            int old = first.sortOrder; first.sortOrder = second.sortOrder; second.sortOrder = old; dao.updateOccurrence(first); dao.updateOccurrence(second);
        });
    }

    public void closeOngoingTask(String taskId) {
        db.runInTransaction(() -> {
            TaskEntity task = dao.task(taskId); if (task == null || !task.ongoing || task.conditionText.isEmpty()) return;
            task.conditionDone = true; task.archived = true; dao.updateTask(task); awardXp();
        });
    }

    private void materializeDueTasks(LocalDate today) {
        for (TaskEntity task : dao.activeTasks()) {
            if (dao.openForTask(task.id) != null || task.nextDueOn.isEmpty() || !ScheduleCalculator.isDue(task, today)) continue;
            Integer max = dao.maxOpenOrder(task.slot); int order = max == null ? 1000 : max + 1000;
            OccurrenceEntity occurrence = new OccurrenceEntity(UUID.randomUUID().toString(), task.id, task.nextDueOn, "OPEN", order, "");
            dao.insertOccurrence(occurrence);
            List<OccurrenceStepEntity> copied = new ArrayList<>();
            for (TaskStepEntity step : dao.templates(task.id)) copied.add(new OccurrenceStepEntity(UUID.randomUUID().toString(), occurrence.id, step.position, step.text, false));
            if (!copied.isEmpty()) dao.insertOccurrenceSteps(copied);
        }
    }

    private void completeOccurrence(OccurrenceEntity occurrence, TaskEntity task, LocalDate completedOn) {
        occurrence.state = "COMPLETED"; occurrence.completedOn = completedOn.toString(); dao.updateOccurrence(occurrence);
        awardXp();
        if (!"ONCE".equals(task.recurrence)) updateRoutineProgress(task, occurrence.scheduledOn, completedOn);
        task.lastScheduledOn = occurrence.scheduledOn; task.lastCompletedOn = completedOn.toString(); task.hasCompletedOccurrence = true;
        LocalDate next = ScheduleCalculator.nextDue(task, completedOn);
        task.nextDueOn = next == null ? "" : next.toString();
        if (!task.ongoing && "ONCE".equals(task.recurrence)) task.archived = true;
        dao.updateTask(task);
    }

    private void updateRoutineProgress(TaskEntity task, String scheduledOn, LocalDate completedOn) {
        boolean currentOnTime = ScheduleCalculator.completedOnTime(scheduledOn, completedOn);
        boolean previousOnTime = task.hasCompletedOccurrence && !task.lastScheduledOn.isEmpty() && !task.lastCompletedOn.isEmpty()
                && task.lastScheduledOn.equals(task.lastCompletedOn);
        task.routineStreak = currentOnTime && previousOnTime ? task.routineStreak + 1 : 1;
        task.routineLevel = Math.max(task.routineLevel, 1 + task.routineStreak / 5);
    }

    private void awardXp() { StatsEntity stats = dao.stats(); if (stats == null) stats = new StatsEntity(0); stats.xp += 10; dao.putStats(stats); }

    private DashboardState buildDashboard() {
        Map<String, TaskEntity> tasks = new HashMap<>(); for (TaskEntity task : dao.activeTasks()) tasks.put(task.id, task);
        List<TaskSnapshot> result = new ArrayList<>();
        for (OccurrenceEntity occurrence : dao.openOccurrences()) {
            TaskEntity task = tasks.get(occurrence.taskId); if (task == null) continue;
            List<OccurrenceStepEntity> steps = dao.occurrenceSteps(occurrence.id); int remaining = 0; String next = "Als erledigt markieren";
            for (OccurrenceStepEntity step : steps) if (!step.done) { remaining++; if (remaining == 1) next = step.text; }
            result.add(new TaskSnapshot(task.id, occurrence.id, task.title, task.slot, next, remaining, !task.conditionText.isEmpty()));
        }
        for (TaskEntity task : tasks.values()) if (task.ongoing && !task.conditionText.isEmpty() && dao.openForTask(task.id) == null)
            result.add(new TaskSnapshot(task.id, "", task.title, task.slot, task.conditionText, 0, true));
        Collections.sort(result, new Comparator<TaskSnapshot>() {
            @Override public int compare(TaskSnapshot a, TaskSnapshot b) {
                int slots = Integer.compare(TaskSlots.rank(a.slot), TaskSlots.rank(b.slot)); if (slots != 0) return slots;
                if (a.occurrenceId.isEmpty() || b.occurrenceId.isEmpty()) return a.title.compareToIgnoreCase(b.title);
                OccurrenceEntity ao = dao.occurrence(a.occurrenceId), bo = dao.occurrence(b.occurrenceId);
                return Integer.compare(ao.sortOrder, bo.sortOrder);
            }
        });
        StatsEntity stats = dao.stats(); return new DashboardState(stats == null ? 0 : stats.xp, result);
    }
}
