package de.thonktank.autosecretary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

public final class TaskViewModel extends ViewModel {
    private final TaskService service;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<DashboardState> state = new MutableLiveData<>();
    private final MutableLiveData<String> errors = new MutableLiveData<>();
    TaskViewModel(Context context) { service = new TaskService(DatabaseProvider.get(context)); load(); }
    LiveData<DashboardState> state() { return state; }
    LiveData<String> errors() { return errors; }
    void load() { worker.execute(() -> state.postValue(service.refreshDashboard())); }
    void create(String title, TaskSlot slot, Recurrence recurrence, int interval, int weekdays, List<String> steps, boolean ongoing, String condition) { run(() -> service.create(title, slot, recurrence, interval, weekdays, steps, ongoing, condition)); }
    void complete(String occurrenceId) { run(() -> service.complete(occurrenceId)); }
    void toggleStep(String stepId) { run(() -> service.toggleStep(stepId)); }
    void defer(String occurrenceId) { run(() -> service.defer(occurrenceId)); }
    void close(String taskId) { run(() -> service.closeOngoingTask(taskId)); }
    void update(String taskId, String title, TaskSlot slot) { run(() -> service.update(taskId, title, slot)); }
    void move(String taskId, TaskSlot slot) { run(() -> service.move(taskId, slot)); }
    void delete(String taskId) { run(() -> service.delete(taskId)); }
    private void run(Action action) { worker.execute(() -> { try { action.run(); state.postValue(service.refreshDashboard()); } catch (IllegalArgumentException e) { errors.postValue(e.getMessage()); } }); }
    @Override protected void onCleared() { worker.shutdownNow(); }
    interface Action { void run(); }
    public static final class Factory implements ViewModelProvider.Factory {
        private final Context context; public Factory(Context context) { this.context = context.getApplicationContext(); }
        @NonNull @Override @SuppressWarnings("unchecked") public <T extends ViewModel> T create(@NonNull Class<T> modelClass) { return (T) new TaskViewModel(context); }
    }
}
