package com.autosecretary.features.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.autosecretary.features.task.domain.TaskLifecycleManager;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGeneratorFactory;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrerequisite;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.features.task.domain.scheduling.CalendarBlockedIntervalProvider;
import com.autosecretary.features.task.domain.scheduling.CategoryWindowProvider;
import com.autosecretary.features.task.domain.scheduling.SchedulingTuning;
import com.autosecretary.features.task.domain.scheduling.SchedulingWindowProvider;
import com.autosecretary.features.task.domain.scheduling.TaskPlanningState;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerationResult;
import com.autosecretary.features.task.domain.scheduling.TaskSlotGenerator;
import com.autosecretary.shared.Period;
import com.autosecretary.testing.AutoSecretaryRobolectricTest;
import com.autosecretary.testing.TaskFixtures;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Protects the scheduling buffer invariants (pause between movable slots, lead time before
 * appointments): buffers shape the placement math only, never fixed (TERMIN) pinning, and a
 * zero tuning reproduces the pre-buffer back-to-back behaviour exactly.
 */
public final class TaskSchedulingBufferCharacterizationTest extends AutoSecretaryRobolectricTest {

    private static TaskSlotGenerator generator(SchedulingWindowProvider windowProvider,
                                               CalendarBlockedIntervalProvider blockedIntervalProvider,
                                               SchedulingTuning tuning) {
        return DefaultTaskSlotGeneratorFactory.create(
                new TaskLifecycleManager(),
                ignored -> { },
                windowProvider,
                blockedIntervalProvider,
                CategoryWindowProvider.NONE,
                List::of,
                candidate -> true,
                () -> tuning);
    }

    private static SchedulingWindowProvider window(LocalTime start, LocalTime end) {
        return d -> new SchedulingWindowProvider.SchedulingWindow(
                LocalDateTime.of(d, start), LocalDateTime.of(d, end));
    }

    private static Task dailyTask(String title, LocalDate day, int durationMinutes) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.repetition.reps = 1;
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = day;
        task.core.minDuration = durationMinutes;
        task.core.maxDuration = durationMinutes;
        TaskFixtures.prefSlot(task, day, LocalTime.of(9, 0));
        return task;
    }

    private static Task termin(String title, LocalDate day, LocalTime start, LocalTime end) {
        Task task = new Task();
        task.core.title = title;
        task.core.created = day;
        task.core.schedulingType = TaskCore.SchedulingType.TERMIN;
        task.core.fixedDate = day;
        task.core.fixedStart = start;
        task.core.fixedEnd = end;
        // The scorer touches the repetition when the TERMIN is evaluated via the chain path;
        // give it the same one-off defaults the task editor would persist.
        task.core.repetition.perPeriod = 1;
        task.core.repetition.periodUnit = Period.DAY;
        task.core.repetition.periodStart = day;
        return task;
    }

    private static List<TaskSlot> scheduledSlotsSortedByStart(List<Task> tasks) {
        List<TaskSlot> slots = new ArrayList<>();
        for (Task task : tasks) {
            for (TaskSlot slot : task.slots) {
                if (slot.scheduled) {
                    slots.add(slot);
                }
            }
        }
        slots.sort(Comparator.comparing(slot -> slot.start));
        return slots;
    }

    @Test
    public void consecutiveScheduledSlotsKeepConfiguredPauseInvariant() {
        LocalDate today = LocalDate.now();
        Task first = dailyTask("Erste", today, 30);
        Task second = dailyTask("Zweite", today, 30);

        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                CalendarBlockedIntervalProvider.NONE,
                new SchedulingTuning(15, 0, 0, 0));
        generator.generateSlotsForWindow(List.of(first, second), today, 1, new TaskPlanningState());

        List<TaskSlot> slots = scheduledSlotsSortedByStart(List.of(first, second));
        assertEquals("both tasks fit the window despite the pause", 2, slots.size());
        for (int i = 1; i < slots.size(); i++) {
            LocalTime previousEnd = slots.get(i - 1).end;
            LocalTime nextStart = slots.get(i).start;
            assertTrue("pause of 15 minutes between consecutive slots (was "
                            + previousEnd + " -> " + nextStart + ")",
                    !nextStart.isBefore(previousEnd.plusMinutes(15)));
        }
    }

    @Test
    public void prerequisiteMinGapSubsumesPauseNotAdditiveInvariant() {
        LocalDate today = LocalDate.now();
        Task before = dailyTask("Voraussetzung", today, 30);
        Task after = dailyTask("Abhängig", today, 30);
        after.prerequisites.add(new TaskPrerequisite(after.core.id, before.core.id, 30));

        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                CalendarBlockedIntervalProvider.NONE,
                new SchedulingTuning(10, 0, 0, 0));
        generator.generateSlotsForWindow(List.of(before, after), today, 1, new TaskPlanningState());

        TaskSlot beforeSlot = before.slots.stream().filter(s -> s.scheduled).findFirst().orElse(null);
        TaskSlot afterSlot = after.slots.stream().filter(s -> s.scheduled).findFirst().orElse(null);
        assertNotNull("prerequisite task is scheduled", beforeSlot);
        assertNotNull("dependent task is scheduled", afterSlot);
        assertEquals("the 30-minute minGap subsumes the 10-minute pause (not additive)",
                beforeSlot.end.plusMinutes(30), afterSlot.start);
    }

    @Test
    public void noSlotEndsInsideLeadZoneBeforeTerminInvariant() {
        LocalDate today = LocalDate.now();
        Task appointment = termin("Arzttermin", today, LocalTime.of(14, 0), LocalTime.of(15, 0));
        List<Task> fillers = List.of(
                dailyTask("Füller 1", today, 60),
                dailyTask("Füller 2", today, 60),
                dailyTask("Füller 3", today, 60),
                dailyTask("Füller 4", today, 60),
                dailyTask("Füller 5", today, 60));

        List<Task> all = new ArrayList<>(fillers);
        all.add(appointment);
        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(16, 0)),
                CalendarBlockedIntervalProvider.NONE,
                new SchedulingTuning(0, 30, 0, 0));
        generator.generateSlotsForWindow(all, today, 1, new TaskPlanningState());

        LocalTime leadZoneStart = LocalTime.of(13, 30);
        for (Task filler : fillers) {
            for (TaskSlot slot : filler.slots) {
                if (!slot.scheduled) {
                    continue;
                }
                boolean clearOfAppointment = !slot.end.isAfter(leadZoneStart)
                        || !slot.start.isBefore(LocalTime.of(15, 0));
                assertTrue("movable slot " + slot.start + "-" + slot.end
                        + " stays clear of the 30-minute lead zone before 14:00", clearOfAppointment);
            }
        }
    }

    @Test
    public void leadZoneAppliesBeforeDeviceCalendarEventsInvariant() {
        LocalDate today = LocalDate.now();
        List<Task> fillers = List.of(
                dailyTask("Füller 1", today, 60),
                dailyTask("Füller 2", today, 60),
                dailyTask("Füller 3", today, 60),
                dailyTask("Füller 4", today, 60),
                dailyTask("Füller 5", today, 60));

        CalendarBlockedIntervalProvider calendarEvent = (day, windowStart, windowEnd) ->
                List.of(new CalendarBlockedIntervalProvider.BlockedInterval(
                        LocalDateTime.of(day, LocalTime.of(14, 0)),
                        LocalDateTime.of(day, LocalTime.of(15, 0))));

        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(16, 0)),
                calendarEvent,
                new SchedulingTuning(0, 30, 0, 0));
        generator.generateSlotsForWindow(new ArrayList<>(fillers), today, 1, new TaskPlanningState());

        LocalTime leadZoneStart = LocalTime.of(13, 30);
        for (Task filler : fillers) {
            for (TaskSlot slot : filler.slots) {
                if (!slot.scheduled) {
                    continue;
                }
                boolean clearOfEvent = !slot.end.isAfter(leadZoneStart)
                        || !slot.start.isBefore(LocalTime.of(15, 0));
                assertTrue("movable slot " + slot.start + "-" + slot.end
                        + " stays clear of the 30-minute lead zone before the calendar event", clearOfEvent);
            }
        }
    }

    @Test
    public void backToBackTerminsStillPinWithoutConflictInvariant() {
        LocalDate today = LocalDate.now();
        Task firstAppointment = termin("Termin 1", today, LocalTime.of(10, 0), LocalTime.of(11, 0));
        Task secondAppointment = termin("Termin 2", today, LocalTime.of(11, 0), LocalTime.of(12, 0));

        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(16, 0)),
                CalendarBlockedIntervalProvider.NONE,
                new SchedulingTuning(10, 30, 0, 0));
        TaskSlotGenerationResult result = generator.generateSlotsForWindow(
                List.of(firstAppointment, secondAppointment), today, 1, new TaskPlanningState());

        assertTrue("first appointment is pinned",
                firstAppointment.slots.stream().anyMatch(slot -> slot.scheduled));
        assertTrue("second appointment is pinned back-to-back without a lead-time conflict",
                secondAppointment.slots.stream().anyMatch(slot -> slot.scheduled));
        assertTrue("no conflicts for back-to-back appointments", result.conflicts().isEmpty());
    }

    @Test
    public void zeroTuningPreservesBackToBackPlacementInvariant() {
        LocalDate today = LocalDate.now();
        Task first = dailyTask("Erste", today, 30);
        Task second = dailyTask("Zweite", today, 30);

        // Window fits exactly two 30-minute slots only if they are placed back-to-back.
        TaskSlotGenerator generator = generator(
                window(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                CalendarBlockedIntervalProvider.NONE,
                SchedulingTuning.NONE);
        generator.generateSlotsForWindow(List.of(first, second), today, 1, new TaskPlanningState());

        List<TaskSlot> slots = scheduledSlotsSortedByStart(List.of(first, second));
        assertEquals("zero tuning packs both slots into the tight window", 2, slots.size());
        assertEquals("second slot starts exactly where the first ends",
                slots.get(0).end, slots.get(1).start);
    }
}
