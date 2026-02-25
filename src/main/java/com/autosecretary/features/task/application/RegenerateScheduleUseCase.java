package com.autosecretary.features.task.application;

import com.autosecretary.config.Preferences;
import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskDAO;
import com.autosecretary.features.task.data.TaskSeedDataFactory;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.task.domain.MultiDayState;
import com.autosecretary.features.task.domain.SlotGenerator;
import com.autosecretary.features.task.domain.TaskTreeOperations;
import com.autosecretary.features.task.domain.TimeWindow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Entry point for schedule generation. Reads all tasks from the database, generates
 * a 7-day schedule (today + 6 days) using {@link SlotGenerator} with cross-day state
 * tracking, and writes scheduled results back. Seeds default tasks on first run when
 * the DB is empty.
 */
public class RegenerateScheduleUseCase {
    private static final int PLANNING_DAYS = 7;

    private final TaskDAO taskDao;
    private final SlotGenerator generator;
    private final Preferences preferences;
    private final ExecutorService executor;

    public RegenerateScheduleUseCase(TaskDAO taskDao,
                                     SlotGenerator generator,
                                     Preferences preferences,
                                     ExecutorService executor) {
        this.taskDao = taskDao;
        this.generator = generator;
        this.preferences = preferences;
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
            MultiDayState state = new MultiDayState();
            for (Task task : tasks) {
                for (TaskSlot slot : task.slots) {
                    if (slot.day != null
                            && !slot.day.isBefore(today)
                            && slot.day.isBefore(windowEnd)
                            && (slot.completed || slot.realStart != null)) {
                        state.recordScheduled(task.core.id, slot.day);
                    }
                }
            }

            // 3) Flatten once, then schedule day by day
            List<Task> flatTasks = TaskTreeOperations.flatten(
                    TaskTreeOperations.buildTree(tasks));

            for (int i = 0; i < PLANNING_DAYS; i++) {
                LocalDate day = today.plusDays(i);
                TimeWindow window = new TimeWindow(
                        LocalDateTime.of(day, preferences.readPrefTime(day, true)),
                        LocalDateTime.of(day, preferences.readPrefTime(day, false)));

                generator.generateSlotsForDay(flatTasks, window, state);

                // Record newly assigned slots into cross-day state
                for (Task t : flatTasks) {
                    for (TaskSlot slot : t.slots) {
                        if (slot.day.equals(day) && slot.scheduled
                                && !state.getScheduledDays(t.core.id).contains(day)) {
                            state.recordScheduled(t.core.id, day);
                        }
                    }
                }
            }

            taskDao.writeList(flatTasks);
            onDone.run();
        });
    }
}
