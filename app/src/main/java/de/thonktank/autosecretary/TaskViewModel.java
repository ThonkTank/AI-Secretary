package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.UiTextProvider;

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
    private static final String REFRESH = "refresh";

    private final TaskUseCases tasks;
    private final DashboardPresenter dashboard;
    private final CalendarDataSource calendar;
    private final UiPreferences preferences;
    private final Clock clock;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final SavedStateHandle savedState;
    private final CalendarDataSource.Subscription calendarSubscription;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<DashboardUiState> state = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private final Object stateLock = new Object();
    private DashboardUiState current;

    TaskViewModel(AppContainer container, SavedStateHandle savedState) {
        this(container.tasks, container.dashboardPresenter, container.calendar,
                container.uiPreferences, container.clock, container.logger, container.texts,
                savedState);
    }

    TaskViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, SavedStateHandle savedState) {
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.preferences = preferences;
        this.clock = clock;
        this.logger = logger;
        this.texts = texts;
        this.savedState = savedState;
        NavigationDestination navigation = restoredNavigation(savedState.get(NAVIGATION));
        EditorUiState editor = EditorUiState.fromBundle(savedState.get(EDITOR));
        current = new DashboardUiState(navigation, DashboardUiModel.empty(),
                CalendarUiState.empty(), palette(), CalendarPermissionStatus.UNKNOWN,
                false, Collections.emptySet(), editor);
        state.setValue(current);
        calendarSubscription = calendar.observeChanges(this::load);
        load();
        if (editor.open && editor.loading && editor.taskId != null) openEditor(editor.taskId);
    }

    LiveData<DashboardUiState> state() { return state; }
    LiveData<UiEvent> events() { return events; }

    void load() {
        if (!begin(REFRESH, true)) return;
        worker.execute(() -> {
            try {
                publishContent(REFRESH, loadContent());
            } catch (RuntimeException error) {
                fail(REFRESH, texts.text(R.string.error_dashboard_load), error);
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
        if (taskId == null) {
            setEditor(EditorUiState.create());
            return;
        }
        String key = actionKey("load-editor", taskId);
        setEditor(EditorUiState.loading(taskId));
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                de.thonktank.autosecretary.domain.model.TaskDetails details =
                        tasks.loadTaskDetails.execute(TaskId.of(taskId));
                if (details == null) {
                    setEditor(EditorUiState.closed());
                    fail(key, texts.text(R.string.error_task_missing),
                            new IllegalArgumentException("Missing task " + taskId));
                    return;
                }
                EditorUiState loaded = EditorUiState.edit(details);
                savedState.set(EDITOR, loaded.toBundle());
                synchronized (stateLock) {
                    Set<String> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    current = current.withEditor(loaded).withRunningActions(actions);
                    state.postValue(current);
                }
            } catch (RuntimeException error) {
                setEditor(EditorUiState.closed());
                fail(key, texts.text(R.string.error_editor_load), error);
            }
        });
    }

    void dismissEditor() {
        savedState.set(EDITOR, null);
        update(value -> value.withEditor(EditorUiState.closed()));
    }

    void updateEditorDraft(EditorUiState draft) {
        if (!draft.open || draft.loading) return;
        setEditor(draft);
    }

    void saveEditor(EditorUiState draft) {
        try {
            validate(draft.title, draft.recurrence, draft.weekdayMask,
                    draft.ongoing, draft.condition);
        } catch (IllegalArgumentException error) {
            events.setValue(UiEvent.error(error.getMessage()));
            return;
        }
        if (draft.taskId == null) {
            create(draft.title, draft.slot, draft.recurrence, draft.intervalDays,
                    draft.weekdayMask, draft.steps, draft.ongoing, draft.condition);
        } else {
            run(actionKey("update", draft.taskId), () -> tasks.update.execute(
                    TaskId.of(draft.taskId), draft.title, draft.slot, draft.recurrence,
                    draft.intervalDays, draft.weekdayMask, draft.steps, draft.ongoing,
                    draft.condition));
        }
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
                throw new IllegalArgumentException(texts.text(R.string.error_name));
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
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private Content loadContent() {
        DashboardState loadedDashboard = dashboard.refresh();
        return new Content(loadedDashboard, calendar.loadToday());
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
            CalendarUiState calendarState = CalendarUiState.from(content.calendar);
            current = current.withContent(DashboardUiModel.compose(content.dashboard,
                            content.calendar.events()),
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

    private void setEditor(EditorUiState editor) {
        savedState.set(EDITOR, editor.open ? editor.toBundle() : null);
        update(value -> value.withEditor(editor));
    }

    private void validate(String title, Recurrence recurrence, int weekdayMask,
                          boolean ongoing, String condition) {
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException(texts.text(R.string.error_name));
        if (recurrence == Recurrence.WEEKDAYS && !ScheduleCalculator.hasWeekday(weekdayMask))
            throw new IllegalArgumentException(texts.text(R.string.error_weekdays));
        if (ongoing && (condition == null || condition.trim().isEmpty()))
            throw new IllegalArgumentException(texts.text(R.string.error_condition));
    }

    @Override protected void onCleared() {
        calendarSubscription.close();
        worker.shutdownNow();
    }

    interface Action { void run(); }
    interface StateChange { DashboardUiState apply(DashboardUiState state); }

    private static final class Content {
        final DashboardState dashboard;
        final CalendarResult calendar;
        Content(DashboardState dashboard, CalendarResult calendar) {
            this.dashboard = dashboard;
            this.calendar = calendar;
        }
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;

        public Factory(AppContainer container) {
            this.container = container;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(TaskViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new TaskViewModel(container,
                    SavedStateHandleSupport.createSavedStateHandle(extras));
        }
    }
}
