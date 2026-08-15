package de.thonktank.autosecretary;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transactional domain layer shared by the activity and the home-screen widget. */
public final class TaskService {
    private final AppDatabase db;
    private final TaskDao dao;
    private final Clock clock;

    public TaskService(AppDatabase db) { this(db, new SystemClock()); }
    TaskService(AppDatabase db, Clock clock) { this.db = db; dao = db.tasks(); this.clock = clock; }

    public DashboardState dashboard() {
        db.runInTransaction(() -> materializeDueTasks(clock.today()));
        return buildDashboard(clock.today());
    }

    public void create(String title, String slot, String recurrence, int intervalDays, int weekdayMask,
                       List<String> steps, boolean ongoing, String condition) {
        validate(title, recurrence, weekdayMask); LocalDate today = clock.today();
        db.runInTransaction(() -> {
            String id = UUID.randomUUID().toString();
            TaskEntity task = new TaskEntity(id, title.trim(), slot, recurrence, Math.max(1, intervalDays), weekdayMask,
                    ongoing, condition.trim(), false, false, today.toString(), "", "", 1, 0,
                    0, "", nextOrder(slot), false);
            dao.insertTask(task); insertTemplates(task.id, steps); materializeDueTasks(today);
        });
    }

    public void update(String taskId, String title, String slot) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        db.runInTransaction(() -> { TaskEntity task = dao.task(taskId); if (task == null) return;
            task.title = title.trim(); if (!task.slot.equals(slot)) { task.slot = slot; task.displayOrder = nextOrder(slot); }
            dao.updateTask(task); });
    }

    public void move(String taskId, String slot) {
        db.runInTransaction(() -> { TaskEntity task = dao.task(taskId); if (task == null) return;
            task.slot = slot; task.displayOrder = nextOrder(slot); dao.updateTask(task); });
    }

    public void delete(String taskId) { db.runInTransaction(() -> dao.deleteTask(taskId)); }

    public void toggleStep(String stepId) {
        db.runInTransaction(() -> { OccurrenceStepEntity step = dao.occurrenceStep(stepId); if (step == null) return;
            OccurrenceEntity occurrence = dao.occurrence(step.occurrenceId);
            if (occurrence == null || !"OPEN".equals(occurrence.state)) return;
            step.done = !step.done; dao.updateOccurrenceStep(step); });
    }

    public void complete(String occurrenceId) {
        if (occurrenceId == null || occurrenceId.isEmpty()) return;
        db.runInTransaction(() -> { OccurrenceEntity occurrence = dao.occurrence(occurrenceId);
            if (occurrence == null || !"OPEN".equals(occurrence.state)) return;
            for (OccurrenceStepEntity step : dao.occurrenceSteps(occurrenceId)) if (!step.done) {
                step.done = true; dao.updateOccurrenceStep(step);
            }
            TaskEntity task = dao.task(occurrence.taskId); if (task != null) completeOccurrence(occurrence, task, clock.today());
        });
    }

    public void completeNextStep(String occurrenceId) { complete(occurrenceId); }

    public void defer(String occurrenceOrTaskId) {
        db.runInTransaction(() -> { List<TaskSnapshot> open = new ArrayList<>();
            for (TaskSnapshot item : buildDashboard(clock.today()).tasks) if (!item.done) open.add(item);
            int index = -1; for (int i = 0; i < open.size(); i++) if (occurrenceOrTaskId.equals(open.get(i).occurrenceId)
                    || occurrenceOrTaskId.equals(open.get(i).taskId)) { index = i; break; }
            if (index < 0 || index >= open.size() - 1) return;
            TaskEntity first = dao.task(open.get(index).taskId), second = dao.task(open.get(index + 1).taskId);
            if (first == null || second == null) return;
            long old = first.displayOrder; first.displayOrder = second.displayOrder; second.displayOrder = old;
            dao.updateTask(first); dao.updateTask(second);
        });
    }

    public void closeOngoingTask(String taskId) {
        db.runInTransaction(() -> { TaskEntity task = dao.task(taskId);
            if (task == null || !task.ongoing || task.conditionText.isEmpty()) return;
            OccurrenceEntity open = dao.openForTask(task.id);
            if (open != null && "OPEN".equals(open.state)) {
                for (OccurrenceStepEntity step : dao.occurrenceSteps(open.id)) if (!step.done) {
                    step.done = true; dao.updateOccurrenceStep(step);
                }
                completeOccurrence(open, task, clock.today());
            } else { awardXp(); task.lastCompletedOn = clock.today().toString(); }
            task.conditionDone = true; task.archived = true; dao.updateTask(task);
        });
    }

    private void validate(String title, String recurrence, int weekdayMask) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        if ("WEEKDAYS".equals(recurrence) && !ScheduleCalculator.hasWeekday(weekdayMask))
            throw new IllegalArgumentException("geht so nicht: Wähle mindestens einen Wochentag.");
    }

    private void insertTemplates(String taskId, List<String> steps) {
        List<TaskStepEntity> templates = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) if (!steps.get(i).trim().isEmpty())
            templates.add(new TaskStepEntity(UUID.randomUUID().toString(), taskId, i, steps.get(i).trim()));
        if (!templates.isEmpty()) dao.insertTemplates(templates);
    }

    private long nextOrder(String slot) {
        long floor = (TaskSlots.rank(slot) + 1L) * 1_000_000L; Long max = dao.maxTaskOrder(floor, floor + 1_000_000L);
        return max == null || max < floor || max >= floor + 1_000_000L ? floor + 1000L : max + 1000L;
    }

    private void materializeDueTasks(LocalDate today) {
        for (TaskEntity task : dao.activeTasks()) {
            if (task.displayOrder == 0) { task.displayOrder = nextOrder(task.slot); dao.updateTask(task); }
            if (dao.openForTask(task.id) != null || task.nextDueOn.isEmpty() || !ScheduleCalculator.isDue(task, today)) continue;
            Integer max = dao.maxOpenOrder(task.slot); int order = max == null ? 1000 : max + 1000;
            OccurrenceEntity occurrence = new OccurrenceEntity(UUID.randomUUID().toString(), task.id, task.nextDueOn, "OPEN", order, "");
            dao.insertOccurrence(occurrence); List<OccurrenceStepEntity> copied = new ArrayList<>();
            for (TaskStepEntity step : dao.templates(task.id)) copied.add(new OccurrenceStepEntity(
                    UUID.randomUUID().toString(), occurrence.id, step.position, step.text, false));
            if (!copied.isEmpty()) dao.insertOccurrenceSteps(copied);
        }
    }

    private void completeOccurrence(OccurrenceEntity occurrence, TaskEntity task, LocalDate completedOn) {
        occurrence.state = "COMPLETED"; occurrence.completedOn = completedOn.toString(); dao.updateOccurrence(occurrence); awardXp();
        if (!"ONCE".equals(task.recurrence)) updateRoutineProgress(task, occurrence.scheduledOn, completedOn);
        task.lastScheduledOn = occurrence.scheduledOn; task.lastCompletedOn = completedOn.toString(); task.hasCompletedOccurrence = true;
        LocalDate next = ScheduleCalculator.nextDue(task, completedOn); task.nextDueOn = next == null ? "" : next.toString();
        if (!task.ongoing && "ONCE".equals(task.recurrence)) task.archived = true; dao.updateTask(task);
    }

    private void updateRoutineProgress(TaskEntity task, String scheduledOn, LocalDate completedOn) {
        boolean currentOnTime = ScheduleCalculator.completedOnTime(scheduledOn, completedOn);
        boolean previousOnTime = task.hasCompletedOccurrence && !task.lastScheduledOn.isEmpty()
                && task.lastScheduledOn.equals(task.lastCompletedOn);
        task.routineStreak = currentOnTime && previousOnTime ? task.routineStreak + 1 : 1;
        task.routineLevel = Math.max(task.routineLevel, 1 + task.routineStreak / 5);
        LocalDate week = completedOn.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!currentOnTime || !previousOnTime || task.lastStreakWeek.isEmpty()) task.routineStreakWeeks = 1;
        else try {
            LocalDate previous = LocalDate.parse(task.lastStreakWeek).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            long elapsed = Math.max(0, ChronoUnit.WEEKS.between(previous, week));
            if (elapsed > 0) task.routineStreakWeeks += (int) elapsed;
        } catch (Exception ignored) { task.routineStreakWeeks = Math.max(1, task.routineStreakWeeks); }
        task.lastStreakWeek = week.toString();
    }

    private void awardXp() { StatsEntity stats = dao.stats(); if (stats == null) stats = new StatsEntity(0);
        stats.xp += 10; dao.putStats(stats); }

    private DashboardState buildDashboard(LocalDate today) {
        Map<String, TaskEntity> tasks = new HashMap<>(); for (TaskEntity task : dao.allTasks()) tasks.put(task.id, task);
        List<TaskSnapshot> result = new ArrayList<>(); Set<String> included = new HashSet<>();
        for (OccurrenceEntity occurrence : dao.openOccurrences()) {
            TaskEntity task = tasks.get(occurrence.taskId); if (task == null || task.archived || task.conditionDone) continue;
            result.add(snapshot(task, occurrence, false, today)); included.add(task.id);
        }
        for (TaskEntity task : tasks.values()) if (task.ongoing && !task.conditionText.isEmpty()
                && !task.archived && !task.conditionDone && !included.contains(task.id)) {
            result.add(snapshot(task, null, false, today)); included.add(task.id);
        }
        for (OccurrenceEntity occurrence : dao.completedOccurrences(today.toString())) {
            TaskEntity task = tasks.get(occurrence.taskId); if (task == null || included.contains(task.id)) continue;
            result.add(snapshot(task, occurrence, true, today)); included.add(task.id);
        }
        for (TaskEntity task : tasks.values()) if (task.archived && task.lastCompletedOn.equals(today.toString())
                && !included.contains(task.id)) result.add(snapshot(task, null, true, today));
        Collections.sort(result, Comparator.comparingLong(item -> item.displayOrder));
        StatsEntity stats = dao.stats(); return new DashboardState(stats == null ? 0 : stats.xp, result);
    }

    private TaskSnapshot snapshot(TaskEntity task, OccurrenceEntity occurrence, boolean done, LocalDate today) {
        List<TaskStepSnapshot> steps = new ArrayList<>(); int remaining = 0; String next = task.conditionText;
        if (occurrence != null) for (OccurrenceStepEntity step : dao.occurrenceSteps(occurrence.id)) {
            boolean stepDone = done || step.done; steps.add(new TaskStepSnapshot(step.id, step.text, stepDone));
            if (!stepDone) { remaining++; if (remaining == 1) next = step.text; }
        }
        if (next == null || next.isEmpty()) next = steps.isEmpty() ? "Als erledigt markieren" : "Alles erledigt";
        boolean overdue = occurrence != null && !done && LocalDate.parse(occurrence.scheduledOn).isBefore(today);
        return new TaskSnapshot(task.id, occurrence == null ? "" : occurrence.id, task.title, task.slot,
                softTime(task.slot, task.ongoing), next, task.recurrence, steps, remaining,
                !task.conditionText.isEmpty(), task.ongoing, done, overdue,
                "ONCE".equals(task.recurrence) ? 0 : task.routineStreakWeeks, task.displayOrder);
    }

    static String softTime(String slot, boolean ongoing) {
        if (ongoing) return "fortlaufend, bis die Bedingung erfüllt ist";
        if (TaskSlots.MORNING.equals(slot)) return "heute am Morgen";
        if (TaskSlots.MIDDAY.equals(slot)) return "um die Mittagszeit";
        if (TaskSlots.EVENING.equals(slot)) return "heute am Abend";
        return "später, sobald Platz ist";
    }
}
