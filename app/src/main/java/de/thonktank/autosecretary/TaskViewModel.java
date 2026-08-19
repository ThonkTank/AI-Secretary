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
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.UiTextProvider;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class TaskViewModel extends ViewModel {
    private static final String NAVIGATION = "navigation";
    private static final String EDITOR = "editor";
    private static final UiCommand REFRESH = new UiCommand(UiCommand.Kind.REFRESH, "today");

    private final TaskUseCases tasks;
    private final DashboardPresenter dashboard;
    private final CalendarDataSource calendar;
    private final UiPreferences preferences;
    private final Clock clock;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final SavedStateHandle savedState;
    private final CalendarDataSource.Subscription calendarSubscription;
    private final ExecutorService worker;
    private final MutableLiveData<DashboardUiState> state = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private final RewardEffectQueue rewardQueue = new RewardEffectQueue();
    private final MutableLiveData<RewardEffectQueue.Snapshot> rewardEffects =
            new MutableLiveData<>(rewardQueue.snapshot());
    private final Object stateLock = new Object();
    private DashboardUiState current;

    TaskViewModel(AppContainer container, SavedStateHandle savedState, ExecutorService worker) {
        this(container.tasks, container.dashboardPresenter, container.calendar,
                container.uiPreferences, container.clock, container.logger, container.texts,
                savedState, worker);
    }

    TaskViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, SavedStateHandle savedState, ExecutorService worker) {
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.preferences = preferences;
        this.clock = clock;
        this.logger = logger;
        this.texts = texts;
        this.savedState = savedState;
        this.worker = worker;
        NavigationDestination navigation = restoredNavigation(savedState.get(NAVIGATION));
        EditorUiState editor = EditorUiState.fromBundle(savedState.get(EDITOR));
        current = new DashboardUiState(navigation, TodayUiModel.empty(),
                CalendarUiState.empty(), palette(), CalendarPermissionStatus.UNKNOWN,
                false, Collections.emptySet(), editor);
        state.setValue(current);
        calendarSubscription = calendar.observeChanges(this::load);
        load();
        if (editor.open && editor.loading && editor.taskId != null) openEditor(editor.taskId);
    }

    LiveData<DashboardUiState> state() { return state; }
    LiveData<UiEvent> events() { return events; }
    LiveData<RewardEffectQueue.Snapshot> rewardEffects() { return rewardEffects; }

    void acknowledgeRewardEffect(String id) {
        rewardEffects.setValue(rewardQueue.acknowledge(id));
    }

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

    void displayPreferencesChanged() {
        update(value -> value.withPalette(palette()));
    }

    void updateRepetitionInput(RepetitionInputState input) {
        if (input != null) update(value -> value.withRepetitionInput(input));
    }

    void navigate(NavigationDestination destination) {
        savedState.set(NAVIGATION, destination.name());
        update(value -> value.withNavigation(destination));
    }

    void openEditor(@Nullable String taskId) {
        if (taskId == null) {
            setEditor(EditorUiState.create(defaultEditorSlot(clock.time())));
            return;
        }
        UiCommand key = command(UiCommand.Kind.LOAD_EDITOR, taskId);
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
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
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

    static TaskSlot defaultEditorSlot(LocalTime time) {
        if (time.isBefore(LocalTime.of(11, 0))) return TaskSlot.MORNING;
        if (time.isBefore(LocalTime.of(17, 0))) return TaskSlot.MIDDAY;
        if (time.isBefore(LocalTime.of(21, 0))) return TaskSlot.EVENING;
        return TaskSlot.LATER;
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
        Set<String> errors = new TaskEditorValidator().errors(draft, clock.today());
        if (!errors.isEmpty()) {
            setEditor(draft.withFeedback(errors, EditorUiState.Prompt.NONE, ""));
            return;
        }
        UiCommand key = command(draft.taskId == null ? UiCommand.Kind.CREATE
                : UiCommand.Kind.UPDATE, draft.taskId == null ? "new" : draft.taskId);
        if (!begin(key, false)) return;
        setEditor(draft.withSaving(true));
        worker.execute(() -> {
            try {
                if (draft.taskId == null) tasks.create.execute(draft.definition());
                else tasks.update.execute(TaskId.of(draft.taskId), draft.definition());
                Content content = loadContent();
                savedState.set(EDITOR, null);
                synchronized (stateLock) {
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    current = current.withContent(content.dashboard.withCalendar(
                                    content.calendar.events()), CalendarUiState.from(content.calendar))
                            .withRunningActions(actions).withEditor(EditorUiState.closed());
                    state.postValue(current);
                }
            } catch (RuntimeException error) {
                logger.error("TaskViewModel", "Editor save failed", error);
                synchronized (stateLock) {
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    EditorUiState failed = draft.withSaving(false).withFeedback(
                            Collections.emptySet(), EditorUiState.Prompt.NONE,
                            texts.text(R.string.error_change_save));
                    current = current.withRunningActions(actions).withEditor(failed);
                    savedState.set(EDITOR, failed.toBundle());
                    state.postValue(current);
                }
            }
        });
    }

    void deleteFromEditor(String taskId) {
        if (taskId == null) return;
        UiCommand key = command(UiCommand.Kind.DELETE, taskId);
        if (!begin(key, false)) return;
        EditorUiState draft;
        synchronized (stateLock) { draft = current.editor; }
        setEditor(draft.withSaving(true));
        worker.execute(() -> {
            try {
                tasks.delete.execute(TaskId.of(taskId));
                Content content = loadContent();
                savedState.set(EDITOR, null);
                synchronized (stateLock) {
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    current = current.withContent(content.dashboard.withCalendar(
                                    content.calendar.events()), CalendarUiState.from(content.calendar))
                            .withRunningActions(actions).withEditor(EditorUiState.closed());
                    state.postValue(current);
                }
            } catch (RuntimeException error) {
                logger.error("TaskViewModel", "Editor delete failed", error);
                setEditor(draft.withSaving(false).withFeedback(Collections.emptySet(),
                        EditorUiState.Prompt.NONE, texts.text(R.string.error_change_save)));
            }
        });
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

    void requestClose(String taskId, String title) {
        events.setValue(UiEvent.confirmClose(taskId, title));
    }

    void create(String title, TaskSlot slot, Recurrence recurrence, int interval, int weekdays,
                List<String> steps, boolean ongoing, String condition) {
        run(command(UiCommand.Kind.CREATE, "new"), () -> {
            validate(title, recurrence, weekdays, ongoing, condition);
            tasks.create.execute(title, slot, recurrence, interval, weekdays, steps, ongoing, condition);
        });
    }

    void complete(String occurrenceId) { runReward(command(UiCommand.Kind.COMPLETE, occurrenceId), () -> tasks.complete.execute(occurrenceId)); }
    void completeRemaining(String occurrenceId) {
        runReward(command(UiCommand.Kind.COMPLETE_REMAINING, occurrenceId),
                () -> tasks.completeRemainingSteps.execute(occurrenceId));
    }
    void harvest(String occurrenceId) {
        runReward(command(UiCommand.Kind.HARVEST, occurrenceId), () -> tasks.harvest.execute(occurrenceId));
    }
    void undoOccurrence(String occurrenceId) {
        runReward(command(UiCommand.Kind.UNDO, occurrenceId), () -> tasks.undoOccurrence.execute(occurrenceId));
    }
    void toggleStep(String stepId) { runReward(command(UiCommand.Kind.TOGGLE_STEP, stepId), () -> tasks.toggleStep.execute(stepId)); }
    void confirmSet(String stepId, int repetitions) {
        runReward(command(UiCommand.Kind.CONFIRM_SET, stepId),
                () -> tasks.confirmSet.execute(stepId, repetitions));
    }
    void finishExercise(String stepId) {
        runReward(command(UiCommand.Kind.FINISH_EXERCISE, stepId),
                () -> tasks.finishExercise.execute(stepId));
    }
    void reopenExercise(String stepId, List<Integer> repetitions) {
        runReward(command(UiCommand.Kind.REOPEN_EXERCISE, stepId),
                () -> tasks.reopenExercise.execute(stepId, repetitions));
    }
    void editStepRepetition(String stepId, int index, int repetitions) {
        runReward(command(UiCommand.Kind.EDIT_STEP_PROGRESS, stepId),
                () -> tasks.editStepProgress.execute(stepId, index, repetitions));
    }
    void defer(String occurrenceId) { run(command(UiCommand.Kind.DEFER, occurrenceId), () -> tasks.defer.execute(occurrenceId)); }
    void close(String taskId) {
        runReward(command(UiCommand.Kind.CLOSE, taskId),
                () -> tasks.closeOngoing.execute(TaskId.of(taskId)));
    }
    void update(String taskId, String title, TaskSlot slot) {
        run(command(UiCommand.Kind.UPDATE, taskId), () -> {
            if (title == null || title.trim().isEmpty())
                throw new IllegalArgumentException(texts.text(R.string.error_name));
            tasks.update.execute(TaskId.of(taskId), title, slot);
        });
    }
    void move(String taskId, TaskSlot slot) { run(command(UiCommand.Kind.MOVE, taskId), () -> tasks.move.execute(TaskId.of(taskId), slot)); }
    void delete(String taskId) { run(command(UiCommand.Kind.DELETE, taskId), () -> tasks.delete.execute(TaskId.of(taskId))); }

    static UiCommand command(UiCommand.Kind kind, String id) {
        return new UiCommand(kind, id);
    }

    private void run(UiCommand key, Action action) {
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

    private void runReward(UiCommand key, RewardAction action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                RewardReceipt result = action.run();
                publishContent(key, loadContent());
                RewardEffect effect = RewardEffect.from(result, key);
                if (effect != null) rewardEffects.postValue(rewardQueue.enqueue(effect));
            } catch (IllegalArgumentException error) {
                fail(key, error.getMessage(), error);
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private Content loadContent() {
        TodayUiModel loadedDashboard = dashboard.refresh();
        return new Content(loadedDashboard, calendar.loadToday());
    }

    private boolean begin(UiCommand key, boolean loading) {
        synchronized (stateLock) {
            if (current.runningActions.contains(key)) return false;
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.add(key);
            current = current.withRunningActions(actions);
            if (loading) current = current.withLoading(true);
            state.postValue(current);
            return true;
        }
    }

    private void publishContent(UiCommand key, Content content) {
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            CalendarUiState calendarState = CalendarUiState.from(content.calendar);
            current = current.withContent(content.dashboard.withCalendar(content.calendar.events()),
                    calendarState).withRunningActions(actions);
            state.postValue(current);
        }
    }

    private void fail(UiCommand key, String message, RuntimeException error) {
        logger.error("TaskViewModel", "Presentation operation failed: " + key, error);
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
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
    interface RewardAction { RewardReceipt run(); }
    interface StateChange { DashboardUiState apply(DashboardUiState state); }

    private static final class Content {
        final TodayUiModel dashboard;
        final CalendarResult calendar;
        Content(TodayUiModel dashboard, CalendarResult calendar) {
            this.dashboard = dashboard;
            this.calendar = calendar;
        }
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;
        private final Supplier<ExecutorService> workers;

        public Factory(AppContainer container) {
            this(container, Executors::newSingleThreadExecutor);
        }

        Factory(AppContainer container, Supplier<ExecutorService> workers) {
            this.container = container;
            this.workers = workers;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(TaskViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new TaskViewModel(container,
                    SavedStateHandleSupport.createSavedStateHandle(extras), workers.get());
        }
    }
}
