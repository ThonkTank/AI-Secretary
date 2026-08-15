package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskViewModel extends ViewModel {
    private final TaskUseCases tasks;
    private final DashboardPresenter dashboard;
    private final AppLogger logger;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<DashboardState> state = new MutableLiveData<>();
    private final MutableLiveData<String> errors = new MutableLiveData<>();

    TaskViewModel(AppContainer container) {
        tasks = container.tasks;
        dashboard = container.dashboardPresenter;
        logger = container.logger;
        load();
    }

    LiveData<DashboardState> state() { return state; }
    LiveData<String> errors() { return errors; }
    void load() { worker.execute(() -> state.postValue(dashboard.refresh())); }

    void create(String title, TaskSlot slot, Recurrence recurrence, int interval, int weekdays,
                List<String> steps, boolean ongoing, String condition) {
        run(() -> {
            validate(title, recurrence, weekdays, ongoing, condition);
            tasks.create.execute(title, slot, recurrence, interval, weekdays, steps, ongoing, condition);
        });
    }

    void complete(String occurrenceId) { run(() -> tasks.complete.execute(occurrenceId)); }
    void toggleStep(String stepId) { run(() -> tasks.toggleStep.execute(stepId)); }
    void defer(String occurrenceId) { run(() -> tasks.defer.execute(occurrenceId)); }
    void close(String taskId) { run(() -> tasks.closeOngoing.execute(TaskId.of(taskId))); }
    void update(String taskId, String title, TaskSlot slot) {
        run(() -> {
            if (title == null || title.trim().isEmpty())
                throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
            tasks.update.execute(TaskId.of(taskId), title, slot);
        });
    }
    void move(String taskId, TaskSlot slot) { run(() -> tasks.move.execute(TaskId.of(taskId), slot)); }
    void delete(String taskId) { run(() -> tasks.delete.execute(TaskId.of(taskId))); }

    private void run(Action action) {
        worker.execute(() -> {
            try {
                action.run();
                state.postValue(dashboard.refresh());
            } catch (IllegalArgumentException error) {
                errors.postValue(error.getMessage());
            } catch (RuntimeException error) {
                logger.error("TaskViewModel", "Task operation failed", error);
                errors.postValue("Die Änderung konnte nicht gespeichert werden. Bitte versuche es erneut.");
            }
        });
    }

    private static void validate(String title, Recurrence recurrence, int weekdayMask,
                                 boolean ongoing, String condition) {
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
        if (recurrence == Recurrence.WEEKDAYS && !ScheduleCalculator.hasWeekday(weekdayMask))
            throw new IllegalArgumentException("geht so nicht: Wähle mindestens einen Wochentag.");
        if (ongoing && (condition == null || condition.trim().isEmpty()))
            throw new IllegalArgumentException("geht so nicht: Ein fortlaufendes Vorhaben braucht eine Bedingung.");
    }

    @Override protected void onCleared() {
        worker.shutdownNow();
    }

    interface Action { void run(); }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;
        public Factory(AppContainer container) { this.container = container; }
        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new TaskViewModel(container);
        }
    }
}
