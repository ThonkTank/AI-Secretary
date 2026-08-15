package de.thonktank.autosecretary;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskViewModel extends ViewModel {
    private static final String NAVIGATION = "navigation";
    private static final String EDITOR = "editor";
    private static final String CREATE_EDITOR = "__create__";
    private static final String REFRESH = "refresh";

    private final TaskUseCases tasks;
    private final DashboardPresenter dashboard;
    private final CalendarDataSource calendar;
    private final UiPreferences preferences;
    private final Clock clock;
    private final AppLogger logger;
    private final SavedStateHandle savedState;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<DashboardUiState> state = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private final Object stateLock = new Object();
    private DashboardUiState current;

    TaskViewModel(AppContainer container, SavedStateHandle savedState) {
        this(container.tasks, container.dashboardPresenter, container.calendar,
                container.uiPreferences, container.clock, container.logger, savedState);
    }

    TaskViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  SavedStateHandle savedState) {
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.preferences = preferences;
        this.clock = clock;
        this.logger = logger;
        this.savedState = savedState;
        NavigationDestination navigation = restoredNavigation(savedState.get(NAVIGATION));
        EditorUiState editor = restoredEditor(savedState.get(EDITOR));
        current = new DashboardUiState(navigation, DashboardUiModel.empty(),
                CalendarUiState.empty(), palette(), CalendarPermissionStatus.UNKNOWN,
                false, Collections.emptySet(), editor);
        state.setValue(current);
        load();
    }

    LiveData<DashboardUiState> state() { return state; }
    LiveData<UiEvent> events() { return events; }

    void load() {
        if (!begin(REFRESH, true)) return;
        worker.execute(() -> {
            try {
                publishContent(REFRESH, loadContent());
            } catch (RuntimeException error) {
                fail(REFRESH, "Das Dashboard konnte nicht geladen werden.", error);
            }
        });
    }

    void minuteChanged() {
        update(value -> value.withPalette(palette()));
    }

    void navigate(NavigationDestination destination) {
        savedState.set(NAVIGATION, destination.name());
        update(value -> value.withNavigation(destination));
    }

    void openEditor(@Nullable String taskId) {
        savedState.set(EDITOR, taskId == null ? CREATE_EDITOR : taskId);
        update(value -> value.withEditor(taskId == null
                ? EditorUiState.create() : EditorUiState.edit(taskId)));
    }

    void dismissEditor() {
        savedState.set(EDITOR, null);
        update(value -> value.withEditor(EditorUiState.closed()));
    }

    void updateCalendarPermission(boolean granted, boolean showRationale) {
        CalendarPermissionStatus permission;
        if (granted) permission = CalendarPermissionStatus.GRANTED;
        else if (!preferences.calendarPermissionAsked() || showRationale)
            permission = CalendarPermissionStatus.REQUESTABLE;
        else permission = CalendarPermissionStatus.DENIED_TO_SETTINGS;
        boolean changed;
        synchronized (stateLock) {
            changed = current.calendarPermission != permission;
        }
        update(value -> value.withPermission(permission));
        if (changed) load();
    }

    void onCalendarPermissionAction() {
        CalendarPermissionStatus permission;
        synchronized (stateLock) { permission = current.calendarPermission; }
        if (permission == CalendarPermissionStatus.GRANTED
                || permission == CalendarPermissionStatus.DENIED_TO_SETTINGS) {
            events.setValue(UiEvent.action(UiEvent.Type.OPEN_APP_SETTINGS));
        } else {
            preferences.markCalendarPermissionAsked();
            events.setValue(UiEvent.action(UiEvent.Type.REQUEST_CALENDAR_PERMISSION));
        }
    }

    void openReleases() {
        events.setValue(UiEvent.action(UiEvent.Type.OPEN_RELEASES));
    }

    void requestDelete(TaskSnapshot task) {
        events.setValue(UiEvent.confirmDelete(task));
    }

    void requestClose(String taskId, String title, int ringWeeks) {
        events.setValue(UiEvent.confirmClose(taskId, title, ringWeeks));
    }

    void create(String title, TaskSlot slot, Recurrence recurrence, int interval, int weekdays,
                List<String> steps, boolean ongoing, String condition) {
        run("create", () -> {
            validate(title, recurrence, weekdays, ongoing, condition);
            tasks.create.execute(title, slot, recurrence, interval, weekdays, steps, ongoing, condition);
        });
    }

    void complete(String occurrenceId) { run(actionKey("complete", occurrenceId), () -> tasks.complete.execute(occurrenceId)); }
    void toggleStep(String stepId) { run(actionKey("step", stepId), () -> tasks.toggleStep.execute(stepId)); }
    void defer(String occurrenceId) { run(actionKey("defer", occurrenceId), () -> tasks.defer.execute(occurrenceId)); }
    void close(String taskId) { run(actionKey("close", taskId), () -> tasks.closeOngoing.execute(TaskId.of(taskId))); }
    void update(String taskId, String title, TaskSlot slot) {
        run(actionKey("update", taskId), () -> {
            if (title == null || title.trim().isEmpty())
                throw new IllegalArgumentException("geht so nicht: Ein kurzer Name reicht.");
            tasks.update.execute(TaskId.of(taskId), title, slot);
        });
    }
    void move(String taskId, TaskSlot slot) { run(actionKey("move", taskId), () -> tasks.move.execute(TaskId.of(taskId), slot)); }
    void delete(String taskId) { run(actionKey("delete", taskId), () -> tasks.delete.execute(TaskId.of(taskId))); }

    static String actionKey(String action, String id) {
        return action + ":" + id;
    }

    private void run(String key, Action action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                action.run();
                publishContent(key, loadContent());
            } catch (IllegalArgumentException error) {
                fail(key, error.getMessage(), error);
            } catch (RuntimeException error) {
                fail(key, "Die Änderung konnte nicht gespeichert werden. Bitte versuche es erneut.", error);
            }
        });
    }

    private Content loadContent() {
        DashboardState loadedDashboard = dashboard.refresh();
        CalendarPermissionStatus permission;
        synchronized (stateLock) { permission = current.calendarPermission; }
        List<CalendarEventSnapshot> events = permission == CalendarPermissionStatus.GRANTED
                ? calendar.today() : Collections.emptyList();
        return new Content(loadedDashboard, events);
    }

    private boolean begin(String key, boolean loading) {
        synchronized (stateLock) {
            if (current.runningActions.contains(key)) return false;
            Set<String> actions = new LinkedHashSet<>(current.runningActions);
            actions.add(key);
            current = current.withRunningActions(actions);
            if (loading) current = current.withLoading(true);
            state.postValue(current);
            return true;
        }
    }

    private void publishContent(String key, Content content) {
        synchronized (stateLock) {
            Set<String> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            CalendarUiState calendarState = new CalendarUiState(false, content.events);
            current = current.withContent(DashboardUiModel.compose(content.dashboard, content.events),
                    calendarState).withRunningActions(actions);
            state.postValue(current);
        }
    }

    private void fail(String key, String message, RuntimeException error) {
        logger.error("TaskViewModel", "Presentation operation failed: " + key, error);
        synchronized (stateLock) {
            Set<String> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            current = current.withRunningActions(actions).withLoading(false);
            state.postValue(current);
        }
        events.postValue(UiEvent.error(message));
    }

    private void update(StateChange change) {
        synchronized (stateLock) {
            current = change.apply(current);
            state.postValue(current);
        }
    }

    private DayPalette palette() {
        return DayPalette.at(clock.time(), DayPalette.Mode.valueOf(preferences.themeMode().name()));
    }

    private static NavigationDestination restoredNavigation(String stored) {
        if (stored == null) return NavigationDestination.TODAY;
        try {
            return NavigationDestination.valueOf(stored);
        } catch (IllegalArgumentException error) {
            return NavigationDestination.TODAY;
        }
    }

    private static EditorUiState restoredEditor(String stored) {
        if (stored == null) return EditorUiState.closed();
        return CREATE_EDITOR.equals(stored) ? EditorUiState.create() : EditorUiState.edit(stored);
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
    interface StateChange { DashboardUiState apply(DashboardUiState state); }

    private static final class Content {
        final DashboardState dashboard;
        final List<CalendarEventSnapshot> events;
        Content(DashboardState dashboard, List<CalendarEventSnapshot> events) {
            this.dashboard = dashboard;
            this.events = new ArrayList<>(events);
        }
    }

    public static final class Factory extends AbstractSavedStateViewModelFactory {
        private final AppContainer container;

        public Factory(SavedStateRegistryOwner owner, @Nullable Bundle defaultArgs,
                       AppContainer container) {
            super(owner, defaultArgs);
            this.container = container;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        protected <T extends ViewModel> T create(@NonNull String key,
                                                  @NonNull Class<T> modelClass,
                                                  @NonNull SavedStateHandle handle) {
            return (T) new TaskViewModel(container, handle);
        }
    }
}
