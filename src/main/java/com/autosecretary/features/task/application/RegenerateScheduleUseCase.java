package com.autosecretary.features.task.application;

import android.content.Context;

import com.autosecretary.app.Preferences;
import com.autosecretary.features.task.application.internal.calendar.CalendarEvent;
import com.autosecretary.features.task.application.internal.calendar.CalendarReader;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSeedDataFactory;
import com.autosecretary.features.task.domain.TaskPlanningState;
import com.autosecretary.features.task.domain.TaskTreeOperations;
import com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Entry point for schedule generation. Reads all tasks from the database, generates
 * a 7-day schedule (today + 6 days) using {@link DefaultTaskSlotGenerator} with cross-day state
 * tracking, and writes scheduled results back. Seeds default tasks on first run when
 * the DB is empty.
 */
public class RegenerateScheduleUseCase {
    private static final int PLANNING_DAYS = 7;

    private final TaskDAO taskDao;
    private final DefaultTaskSlotGenerator generator;
    private final Preferences preferences;
    private final CalendarReader calendarReader;
    private final Context context;
    private final ExecutorService executor;

    public RegenerateScheduleUseCase(TaskDAO taskDao,
                                     DefaultTaskSlotGenerator generator,
                                     Preferences preferences,
                                     CalendarReader calendarReader,
                                     Context context,
                                     ExecutorService executor) {
        this.taskDao = taskDao;
        this.generator = generator;
        this.preferences = preferences;
        this.calendarReader = calendarReader;
        this.context = context;
        this.executor = executor;
    }

    public void execute(Runnable onDone) {
        executor.execute(() -> {
            List<Task> tasks = taskDao.readAll();
            // First run: seed default tasks when DB is empty. Flatten tree before
            // writing (Room needs a flat list), then re-read to get proper @Relation assembly.
            if (tasks.isEmpty()) {
                List<Task> seedTasks = TaskSeedDataFactory.createDefaultTasks();
                taskDao.writeList(TaskTreeOperations.flatten(seedTasks));
                tasks = taskDao.readAll();
            }

            LocalDate today = LocalDate.now();
            LocalDate windowEnd = today.plusDays(PLANNING_DAYS);

            // 1) Remove unstarted scheduled slots in the 7-day planning window
            for (Task task : tasks) {
                task.slots.removeIf(slot ->
                        !slot.completed
                                && slot.realStart == null
                                && slot.scheduled
                                && slot.day != null
                                && !slot.day.isBefore(today)
                                && slot.day.isBefore(windowEnd));
            }

            // 2) Initialize cross-day state from preserved slots (completed/in-progress)
            TaskPlanningState state = generator.createPlanningState();
            generator.recordPreservedSlots(tasks, today, windowEnd, state);

            // 3) Flatten once, then schedule day by day
            List<Task> flatTasks = TaskTreeOperations.flatten(
                    TaskTreeOperations.buildTree(tasks));

            for (int i = 0; i < PLANNING_DAYS; i++) {
                LocalDate day = today.plusDays(i);
                LocalTime windowStart = preferences.readPrefTime(day, true);
                LocalTime windowEndTime = preferences.readPrefTime(day, false);
                List<CalendarEvent> calendarEvents = calendarReader.getEventsForDay(
                        context,
                        day,
                        windowStart,
                        windowEndTime
                );

                generator.generateSlotsForDay(
                        flatTasks,
                        LocalDateTime.of(day, windowStart),
                        LocalDateTime.of(day, windowEndTime),
                        state,
                        calendarEvents
                );

                // Record newly assigned slots into cross-day state
                generator.recordScheduledSlotsForDay(flatTasks, day, state);
            }

            taskDao.writeList(flatTasks);
            onDone.run();
        });
    }
}
