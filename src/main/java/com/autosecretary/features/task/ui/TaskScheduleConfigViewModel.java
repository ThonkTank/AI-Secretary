package com.autosecretary.features.task.ui;

import androidx.lifecycle.ViewModel;

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
    private final ExecutorService workerExecutor;
    private final Executor callbackDispatcher;

    public TaskScheduleConfigViewModel(
            TaskScheduleConfigRepository repository,
            ExecutorService workerExecutor,
            Executor callbackDispatcher) {
        this.repository = repository;
        this.workerExecutor = workerExecutor;
        this.callbackDispatcher = callbackDispatcher;
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
            callbackDispatcher.execute(onSaved);
        });
    }
}
