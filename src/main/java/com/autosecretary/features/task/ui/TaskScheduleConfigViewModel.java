package com.autosecretary.features.task.ui;

import androidx.lifecycle.ViewModel;

import com.autosecretary.features.task.application.ScheduleReplanCoordinator;
import com.autosecretary.features.task.application.config.TaskScheduleConfigRepository;
import com.autosecretary.features.task.ui.state.DayScheduleRow;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * ViewModel owner for the schedule-config dialog.
 */
public class TaskScheduleConfigViewModel extends ViewModel {

    private final TaskScheduleConfigRepository repository;
    private final ScheduleReplanCoordinator scheduleReplanCoordinator;
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskScheduleConfigViewModel(
            TaskScheduleConfigRepository repository,
            ScheduleReplanCoordinator scheduleReplanCoordinator,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.repository = repository;
        this.scheduleReplanCoordinator = scheduleReplanCoordinator;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * Re-plans so a change to the global scheduling toggle takes effect at once (disabling clears
     * the checklist; enabling rebuilds it). The checklist UI refreshes through the coordinator's
     * listener path.
     */
    public void regenerateSchedule() {
        scheduleReplanCoordinator.requestReplan();
    }

    public void loadConfigs(Consumer<List<DayScheduleRow>> onLoaded) {
        workerExecutor.execute(() -> {
            List<DayScheduleRow> rows = repository.loadAllRows().stream()
                    .map(config -> new DayScheduleRow(config.dayOfWeek(), config.startTime(), config.endTime()))
                    .toList();
            callbackDispatcher.execute(() -> onLoaded.accept(rows));
        });
    }

    public void saveRows(
            List<DayScheduleRow> rows,
            Runnable onSaved,
            Runnable onInvalidRange) {
        boolean hasInvalidRange = rows.stream().anyMatch(row ->
                row.startTime() == null
                        || row.endTime() == null
                        || !row.endTime().isAfter(row.startTime()));
        if (hasInvalidRange) {
            callbackDispatcher.execute(onInvalidRange);
            return;
        }
        workerExecutor.execute(() -> {
            List<TaskScheduleConfigRepository.ScheduleWindowRow> configs = rows.stream()
                    .map(row -> new TaskScheduleConfigRepository.ScheduleWindowRow(
                            row.dayOfWeek(),
                            row.startTime(),
                            row.endTime()))
                    .toList();
            repository.saveAllRows(configs);
            // Per-weekday windows (and the buffer/tuning settings the dialog persists just before this
            // save) are scheduler inputs — re-plan so the new bounds take effect immediately.
            scheduleReplanCoordinator.requestReplan();
            callbackDispatcher.execute(onSaved);
        });
    }
}
