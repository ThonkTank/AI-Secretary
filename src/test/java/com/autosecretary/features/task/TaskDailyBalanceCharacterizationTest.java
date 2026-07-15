package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Protects the soft daily balance budget invariants: work minutes beyond the daily budget spill
 * to other days when possible; the budget is soft (critical work can still exceed it); an unmet
 * leisure quota boosts leisure placements; zero budgets change nothing.
 */
public final class TaskDailyBalanceCharacterizationTest extends AutoSecretaryRobolectricTest {

    private static TaskSlotGenerator generator(SchedulingWindowProvider windowProvider,
                                               SchedulingTuning tuning) {
        return DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                windowProvider,
                CalendarBlockedIntervalProvider.NONE,
                CategoryWindowProvider.NONE,
                List::of,
                candidate -> true,
                () -> tuning);
    }

    private static SchedulingWindowProvider window(LocalTime start, LocalTime end) {
        return d -> new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(d, start), LocalDateTime.of(d, end));
    }

    /** Weekly one-rep task without preferred slots — free to land on any day of the window. */
    private static Task weeklyTask(String title, LocalDate day, Priority priority,
                                   int durationMinutes, boolean leisure) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.priority = priority;
        task.core.leisure = leisure;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.WEEK;
        task.core.repetition.periodStart = day;
        task.core.minDuration = durationMinutes;
        task.core.maxDuration = durationMinutes;
        return task;
    }

    private static Map<LocalDate, Integer> scheduledMinutesPerDay(List<Task> tasks) {
        Map<LocalDate, Integer> minutesByDay = new HashMap<>();
        for (Task task : tasks) {
            for (TaskSlot slot : task.slots) {
                if (slot.scheduled) {
                    minutesByDay.merge(slot.day,
                            (int) ChronoUnit.MINUTES.between(slot.start, slot.end), Integer::sum);
                }
            }
        }
        return minutesByDay;
    }

    private static long scheduledSlotCount(List<Task> tasks) {
        return tasks.stream().flatMap(task -> task.slots.stream()).filter(slot -> slot.scheduled).count();
    }

    @Test
    public void workMinutesBeyondDailyBudgetSpillToOtherDaysWhenPossibleInvariant() {
        LocalDate today = LocalDate.now();
        List<Task> tasks = List.of(
                weeklyTask("Arbeit 1", today, Priority.MEDIUM, 60, false),
                weeklyTask("Arbeit 2", today, Priority.MEDIUM, 60, false),
                weeklyTask("Arbeit 3", today, Priority.MEDIUM, 60, false),
                weeklyTask("Arbeit 4", today, Priority.MEDIUM, 60, false));

        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(17, 0)),
                new SchedulingTuning(0, 0, 120, 0));
        generator.generateSlotsForWindow(tasks, today, 7, new TaskPlanningState());

        assertEquals("every task is still placed somewhere in the window",
                4, scheduledSlotCount(tasks));
        for (Map.Entry<LocalDate, Integer> entry : scheduledMinutesPerDay(tasks).entrySet()) {
            assertTrue("no day exceeds the 120-minute work budget (day " + entry.getKey()
                    + " has " + entry.getValue() + ")", entry.getValue() <= 120);
        }
    }

    @Test
    public void withoutBudgetTheSameWorkClustersOnOneDayInvariant() {
        LocalDate today = LocalDate.now();
        List<Task> tasks = List.of(
                weeklyTask("Arbeit 1", today, Priority.MEDIUM, 60, false),
                weeklyTask("Arbeit 2", today, Priority.MEDIUM, 60, false),
                weeklyTask("Arbeit 3", today, Priority.MEDIUM, 60, false),
                weeklyTask("Arbeit 4", today, Priority.MEDIUM, 60, false));

        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(17, 0)),
                SchedulingTuning.NONE);
        generator.generateSlotsForWindow(tasks, today, 7, new TaskPlanningState());

        boolean anyDayOverBudget = scheduledMinutesPerDay(tasks).values().stream()
                .anyMatch(minutes -> minutes > 120);
        assertTrue("without a budget the same workload concentrates beyond 120 minutes on a day"
                + " (regression anchor: the budget is what causes the spill)", anyDayOverBudget);
    }

    @Test
    public void budgetIsSoftCriticalWorkStillExceedsItInvariant() {
        LocalDate today = LocalDate.now();
        Task critical = weeklyTask("Kritisch lang", today, Priority.CRITICAL, 120, false);

        // Single day, budget 60: the 120-minute task exceeds it from the start —
        // a hard constraint would block it, the soft penalty must not.
        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new SchedulingTuning(0, 0, 60, 0));
        generator.generateSlotsForWindow(List.of(critical), today, 1, new TaskPlanningState());

        assertTrue("the budget is soft: over-budget critical work is still placed",
                critical.slots.stream().anyMatch(slot -> slot.scheduled));
    }

    @Test
    public void leisureTaskWinsGapWhileLeisureQuotaUnmetInvariant() {
        LocalDate today = LocalDate.now();

        // One 30-minute gap, quota unmet: the leisure task must win it.
        Task leisure = weeklyTask("Freizeit", today, Priority.MEDIUM, 30, true);
        Task work = weeklyTask("Arbeit", today, Priority.MEDIUM, 30, false);
        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                new SchedulingTuning(0, 0, 0, 60));
        generator.generateSlotsForWindow(List.of(leisure, work), today, 1, new TaskPlanningState());

        assertTrue("leisure wins the gap while the quota is unmet",
                leisure.slots.stream().anyMatch(slot -> slot.scheduled));
        assertFalse("the work task loses the single gap",
                work.slots.stream().anyMatch(slot -> slot.scheduled));

        // Same duel without a quota: the work task (urgency + aging pressure) wins.
        Task leisure2 = weeklyTask("Freizeit 2", today, Priority.MEDIUM, 30, true);
        Task work2 = weeklyTask("Arbeit 2", today, Priority.MEDIUM, 30, false);
        TaskSlotGenerator noQuota = generator(
                window(LocalTime.of(9, 0), LocalTime.of(9, 30)),
                SchedulingTuning.NONE);
        noQuota.generateSlotsForWindow(List.of(leisure2, work2), today, 1, new TaskPlanningState());

        assertTrue("without a quota the work task wins the gap",
                work2.slots.stream().anyMatch(slot -> slot.scheduled));
        assertFalse("the leisure task loses without quota pressure",
                leisure2.slots.stream().anyMatch(slot -> slot.scheduled));
    }
}
