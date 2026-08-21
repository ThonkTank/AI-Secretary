package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.presentation.today.CalendarEventSnapshot;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayCommandDispatcher;
import de.thonktank.autosecretary.presentation.today.TodayCoordinator;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;

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
import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.RewardReceipt;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.today.AdvanceTodayStepResult;
import de.thonktank.autosecretary.domain.today.StepExecutionResult;
import de.thonktank.autosecretary.domain.today.TodayStepMoveResult;
import de.thonktank.autosecretary.domain.usecase.TaskUseCases;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveResult;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.DashboardPresenter;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class TaskViewModel extends ViewModel implements TodayCommandDispatcher.Handlers {
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
    private final WidgetInvalidator widgets;
    private final SavedStateHandle savedState;
    private final CalendarDataSource.Subscription calendarSubscription;
    private final UiPreferences.Subscription displayPreferencesSubscription;
    private final ExecutorService worker;
    private final MutableLiveData<DashboardUiState> state = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private final MutableLiveData<Long> catalogChanges = new MutableLiveData<>();
    private final RewardEffectQueue rewardQueue = new RewardEffectQueue();
    private final RepetitionInputReducer repetitionInputReducer = new RepetitionInputReducer();
    private final MutableLiveData<RewardEffectQueue.Snapshot> rewardEffects =
            new MutableLiveData<>(rewardQueue.snapshot());
    private final Object stateLock = new Object();
    private final DashboardRefreshPolicy refreshPolicy = new DashboardRefreshPolicy();
    private final TodayCoordinator todayCoordinator;
    private DashboardUiState current;
    private LocalDate loadedDate;
    private long catalogChangeVersion;

    TaskViewModel(AppContainer container, SavedStateHandle savedState, ExecutorService worker) {
        this(container.tasks, container.dashboardPresenter, container.calendar,
                container.uiPreferences, container.clock, container.logger, container.texts,
                savedState, worker, container.widgetUpdates::updateAll);
    }

    TaskViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, SavedStateHandle savedState, ExecutorService worker) {
        this(tasks, dashboard, calendar, preferences, clock, logger, texts, savedState,
                worker, () -> { });
    }

    TaskViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, SavedStateHandle savedState, ExecutorService worker,
                  WidgetInvalidator widgets) {
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.preferences = preferences;
        this.clock = clock;
        this.logger = logger;
        this.texts = texts;
        this.widgets = widgets;
        this.savedState = savedState;
        this.worker = worker;
        NavigationDestination navigation = restoredNavigation(savedState.get(NAVIGATION));
        EditorUiState editor = EditorUiState.fromBundle(savedState.get(EDITOR));
        DisplayPreferences display = preferences.displayPreferences();
        current = new DashboardUiState(navigation, TodayUiModel.empty(),
                CalendarUiState.empty(), palette(display.themeMode),
                CalendarPermissionStatus.UNKNOWN, false, Collections.emptySet(), editor,
                RepetitionInputState.idle(), display.themeMode, display.focusStepLimit,
                UpdateUiState.idle());
        todayCoordinator = new TodayCoordinator(current.dashboard,
                new TodayCommandDispatcher(this), this::publishTodayFeatureState);
        state.setValue(current);
        displayPreferencesSubscription = preferences.observeDisplayPreferences(
                this::onDisplayPreferences);
        calendarSubscription = calendar.observeChanges(this::calendarChanged);
        refresh(DashboardRefreshReason.INITIAL);
        if (editor.open && editor.loading && editor.taskId != null) openEditor(editor.taskId);
    }

    LiveData<DashboardUiState> state() { return state; }
    LiveData<UiEvent> events() { return events; }
    LiveData<Long> catalogChanges() { return catalogChanges; }
    LiveData<RewardEffectQueue.Snapshot> rewardEffects() { return rewardEffects; }

    void acknowledgeRewardEffect(String id) {
        rewardEffects.setValue(rewardQueue.acknowledge(id));
    }

    void load() {
        refresh(DashboardRefreshReason.PERSISTED_CHANGE);
    }

    void refresh(DashboardRefreshReason reason) {
        LocalDate today = clock.today();
        synchronized (stateLock) {
            if (!refreshPolicy.requiresLoad(reason, loadedDate, today)) return;
        }
        if (!begin(REFRESH, true)) return;
        worker.execute(() -> {
            try {
                publishContent(REFRESH, loadContent(), false);
            } catch (RuntimeException error) {
                fail(REFRESH, texts.text(R.string.error_dashboard_load), error);
            }
        });
    }

    void minuteChanged() {
        update(value -> value.withPalette(palette(value.themeMode)));
        refresh(DashboardRefreshReason.DATE_CHANGED);
    }

    void updateUpdateState(UpdateUiState updateState) {
        update(value -> value.withUpdate(updateState));
    }

    private void reduceRepetitionInput(TodayAction action) {
        RepetitionInputReducer.Submission submission;
        synchronized (stateLock) {
            RepetitionInputReducer.Result result = repetitionInputReducer.reduce(
                    current.repetitionInput, current.dashboard, action);
            submission = result.submission;
            if (result.state != current.repetitionInput) {
                current = current.withRepetitionInput(result.state);
                state.postValue(current);
            }
        }
        if (submission == null) return;
        if (submission.correction())
            correctRepetitionResult(submission.stepId, submission.editingIndex,
                    submission.value);
        else recordRepetitionResult(submission.stepId, submission.value);
    }

    void navigate(NavigationDestination destination) {
        savedState.set(NAVIGATION, destination.name());
        update(value -> value.withNavigation(destination));
    }

    void openEditor(@Nullable String taskId) {
        openEditor(taskId, null, false);
    }

    void openEditorForStep(String taskId, @Nullable String stepId, boolean addStep) {
        openEditor(taskId, stepId, addStep);
    }

    private void openEditor(@Nullable String taskId, @Nullable String stepId, boolean addStep) {
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
                if (addStep) loaded = TaskEditorStateReducer.addStep(loaded);
                else if (stepId != null)
                    loaded = TaskEditorStateReducer.expandStep(loaded, stepId);
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
                catalogChanges.postValue(++catalogChangeVersion);
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
                invalidateWidgets();
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
                catalogChanges.postValue(++catalogChangeVersion);
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
                invalidateWidgets();
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
        if (changed) {
            refresh(DashboardRefreshReason.EXTERNAL_DATA);
            invalidateWidgets();
        }
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

    void requestDelete(String taskId, String title) {
        events.setValue(UiEvent.confirmDelete(taskId, title));
    }

    void requestClose(String taskId, String title) {
        events.setValue(UiEvent.confirmClose(taskId, title));
    }

    void dispatchToday(TodayAction action) { todayCoordinator.emit(action); }

    void complete(String occurrenceId) {
        dispatchToday(TodayAction.completeOccurrence(occurrenceId));
    }
    void completeRemaining(String occurrenceId) {
        dispatchToday(TodayAction.completeRemaining(occurrenceId));
    }
    void harvest(String occurrenceId) {
        dispatchToday(TodayAction.harvest(occurrenceId));
    }
    void undoOccurrence(String occurrenceId) {
        dispatchToday(TodayAction.undoOccurrence(occurrenceId));
    }
    void toggleStep(String stepId) { dispatchToday(TodayAction.toggleStep(stepId)); }
    void advanceTodayStep(String stepId) {
        dispatchToday(TodayAction.advanceStep(stepId));
    }
    void moveTodayStep(String stepId, @Nullable String beforeStepId) {
        dispatchToday(TodayAction.moveStep(stepId, beforeStepId));
    }
    void recordRepetitionResult(String stepId, int repetitions) {
        runTodayStepResult(command(UiCommand.Kind.RECORD_REPETITION_RESULT, stepId),
                () -> tasks.recordRepetitionResult.execute(stepId, repetitions));
    }
    void correctRepetitionResult(String stepId, int index, int repetitions) {
        runTodayStepResult(command(UiCommand.Kind.CORRECT_REPETITION_RESULT, stepId),
                () -> tasks.correctRepetitionResult.execute(stepId, index, repetitions));
    }
    void defer(String occurrenceId) { dispatchToday(TodayAction.defer(occurrenceId)); }
    void close(String taskId) {
        runTodayReward(command(UiCommand.Kind.CLOSE, taskId),
                () -> tasks.closeOngoing.execute(TaskId.of(taskId)));
    }
    void move(String taskId, @Nullable TaskSlot sourceSlot, TaskSlot targetSlot) {
        run(command(UiCommand.Kind.MOVE, taskId), () -> {
            ScheduleMoveResult result = tasks.moveTaskPlacement.execute(
                    TaskId.of(taskId), sourceSlot, targetSlot);
            if (result != ScheduleMoveResult.MOVED)
                throw new IllegalArgumentException(scheduleMoveMessage(result));
        });
    }
    void delete(String taskId) { run(command(UiCommand.Kind.DELETE, taskId), () -> tasks.delete.execute(TaskId.of(taskId))); }

    @Override public void handleCompleteOccurrence(String occurrenceId) {
        runTodayReward(command(UiCommand.Kind.COMPLETE, occurrenceId),
                () -> tasks.complete.execute(occurrenceId));
    }

    @Override public void handleRequestClose(String taskId, String title) {
        requestClose(taskId, title);
    }

    @Override public void handleCompleteRemaining(String occurrenceId) {
        runTodayReward(command(UiCommand.Kind.COMPLETE_REMAINING, occurrenceId),
                () -> tasks.completeRemainingSteps.execute(occurrenceId));
    }

    @Override public void handleHarvest(String occurrenceId) {
        runTodayReward(command(UiCommand.Kind.HARVEST, occurrenceId),
                () -> tasks.harvest.execute(occurrenceId));
    }

    @Override public void handleDefer(String occurrenceId) {
        runToday(command(UiCommand.Kind.DEFER, occurrenceId),
                () -> tasks.defer.execute(occurrenceId));
    }

    @Override public void handleToggleStep(String stepId) {
        runTodayReward(command(UiCommand.Kind.TOGGLE_STEP, stepId),
                () -> tasks.toggleStep.execute(stepId));
    }

    @Override public void handleAdvanceStep(String stepId) {
        runTodayAdvance(command(UiCommand.Kind.ADVANCE_TODAY_STEP, stepId), stepId);
    }

    @Override public void handleUndoOccurrence(String occurrenceId) {
        runTodayReward(command(UiCommand.Kind.UNDO, occurrenceId),
                () -> tasks.undoOccurrence.execute(occurrenceId));
    }

    @Override public void handleAdjustRepetition(String stepId, int delta) {
        reduceRepetitionInput(TodayAction.adjustRepetition(stepId, delta));
    }

    @Override public void handleEditRepetition(String stepId, int index) {
        reduceRepetitionInput(TodayAction.editRepetition(stepId, index));
    }

    @Override public void handleSubmitRepetition(String stepId) {
        reduceRepetitionInput(TodayAction.submitRepetition(stepId));
    }

    @Override public void handlePersistReorder(String commandId, String stepId,
                                         @Nullable String beforeStepId) {
        persistTodayReorder(commandId, stepId, beforeStepId);
    }

    static UiCommand command(UiCommand.Kind kind, String id) {
        return new UiCommand(kind, id);
    }

    private void run(UiCommand key, Action action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                action.run();
                publishContent(key, loadContent(), true);
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
                publishContent(key, loadContent(), true);
                RewardEffect effect = RewardEffect.from(result, key);
                if (effect != null) rewardEffects.postValue(rewardQueue.enqueue(effect));
            } catch (IllegalArgumentException error) {
                fail(key, error.getMessage(), error);
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private void runToday(UiCommand key, Action action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                action.run();
                publishToday(key, loadTodayProjection(), true);
            } catch (IllegalArgumentException error) {
                fail(key, error.getMessage(), error);
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private void runTodayReward(UiCommand key, RewardAction action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                RewardReceipt receipt = action.run();
                publishToday(key, loadTodayProjection(), true);
                enqueueReward(receipt, key);
            } catch (IllegalArgumentException error) {
                fail(key, error.getMessage(), error);
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private void runTodayAdvance(UiCommand key, String stepId) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                AdvanceTodayStepResult result = tasks.advanceTodayStep.execute(stepId);
                boolean changed = result.status == AdvanceTodayStepResult.Status.PROGRESS_RECORDED
                        || result.status == AdvanceTodayStepResult.Status.STEP_COMPLETED;
                publishToday(key, loadTodayProjection(), changed);
                enqueueReward(result.rewardReceipt, key);
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private void runTodayStepResult(UiCommand key, StepResultAction action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                StepExecutionResult result = action.run();
                publishToday(key, loadTodayProjection(), result.changed());
                enqueueReward(result.rewardReceipt, key);
            } catch (IllegalArgumentException error) {
                fail(key, error.getMessage(), error);
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private void persistTodayReorder(String commandId, String stepId,
                                     @Nullable String beforeStepId) {
        UiCommand key = command(UiCommand.Kind.MOVE_TODAY_STEP, commandId);
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                TodayStepMoveResult result = tasks.moveTodayStep.execute(
                        stepId, beforeStepId);
                if (result.status == TodayStepMoveResult.Status.MOVED
                        || result.status == TodayStepMoveResult.Status.NO_CHANGE) {
                    todayCoordinator.reorderSucceeded(commandId, result);
                    finishTodayCommand(key);
                    if (result.moved()) invalidateWidgets();
                } else {
                    todayCoordinator.reorderFailed(commandId);
                    fail(key, texts.text(R.string.error_change_save),
                            new IllegalArgumentException("Today reorder rejected: "
                                    + result.status));
                }
            } catch (RuntimeException error) {
                todayCoordinator.reorderFailed(commandId);
                fail(key, texts.text(R.string.error_change_save), error);
            }
        });
    }

    private TodayProjection loadTodayProjection() {
        DashboardPresenter.Refresh refresh = dashboard.refreshWithChanges();
        List<CalendarEventSnapshot> eventsSnapshot;
        synchronized (stateLock) {
            eventsSnapshot = current.calendar.events;
        }
        return new TodayProjection(refresh.dashboard.withCalendar(eventsSnapshot),
                refresh.persistedChanges, clock.today());
    }

    private void publishToday(UiCommand key, TodayProjection projection,
                              boolean commandPersisted) {
        todayCoordinator.rebind(projection.today);
        finishTodayCommand(key);
        loadedDate = projection.date;
        if (commandPersisted || projection.persistedChanges) invalidateWidgets();
    }

    private void finishTodayCommand(UiCommand key) {
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            current = current.withRunningActions(actions).withLoading(false);
            state.postValue(current);
        }
    }

    private void enqueueReward(RewardReceipt receipt, UiCommand key) {
        RewardEffect effect = RewardEffect.from(receipt, key);
        if (effect != null) rewardEffects.postValue(rewardQueue.enqueue(effect));
    }

    private Content loadContent() {
        LocalDate today = clock.today();
        DashboardPresenter.Refresh refresh = dashboard.refreshWithChanges();
        return new Content(refresh.dashboard, calendar.loadToday(), refresh.persistedChanges, today);
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

    private void publishContent(UiCommand key, Content content, boolean commandPersisted) {
        TodayUiModel composed = content.dashboard.withCalendar(content.calendar.events());
        todayCoordinator.rebind(composed);
        TodayFeatureState todayFeature = todayCoordinator.state();
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            CalendarUiState calendarState = CalendarUiState.from(content.calendar);
            current = current.withContent(composed, calendarState)
                    .withTodayFeature(todayFeature)
                    .withRunningActions(actions);
            loadedDate = content.date;
            state.postValue(current);
        }
        if (commandPersisted || content.persistedChanges)
            invalidateWidgets();
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

    private void publishTodayFeatureState(TodayFeatureState feature) {
        synchronized (stateLock) {
            current = current.withTodayFeature(feature);
            state.postValue(current);
        }
    }

    private void calendarChanged() {
        refresh(DashboardRefreshReason.EXTERNAL_DATA);
        invalidateWidgets();
    }

    private void invalidateWidgets() {
        try {
            widgets.invalidate();
        } catch (RuntimeException error) {
            logger.error("TaskViewModel", "Could not invalidate widgets", error);
        }
    }

    private void onDisplayPreferences(DisplayPreferences value) {
        boolean themeChanged;
        synchronized (stateLock) {
            themeChanged = current.themeMode != value.themeMode;
            current = current.withDisplayPreferences(value, palette(value.themeMode));
            state.postValue(current);
        }
        if (themeChanged) invalidateWidgets();
    }

    private DayPalette palette(UiThemeMode mode) {
        return DayPalette.at(clock.time(), DayPalette.Mode.valueOf(mode.name()));
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

    private String scheduleMoveMessage(ScheduleMoveResult result) {
        switch (result) {
            case REJECTED_INACTIVE_TASK: return texts.text(R.string.error_management_inactive);
            case REJECTED_DUPLICATE_SLOT: return texts.text(R.string.error_schedule_duplicate);
            case REJECTED_TODAY_SLOT_OCCUPIED:
                return texts.text(R.string.error_schedule_today_occupied);
            default: return texts.text(R.string.error_task_missing);
        }
    }

    @Override protected void onCleared() {
        calendarSubscription.close();
        displayPreferencesSubscription.close();
        worker.shutdownNow();
    }

    interface Action { void run(); }
    interface RewardAction { RewardReceipt run(); }
    interface StepResultAction { StepExecutionResult run(); }
    interface StateChange { DashboardUiState apply(DashboardUiState state); }

    private static final class Content {
        final TodayUiModel dashboard;
        final CalendarResult calendar;
        final boolean persistedChanges;
        final LocalDate date;
        Content(TodayUiModel dashboard, CalendarResult calendar, boolean persistedChanges,
                LocalDate date) {
            this.dashboard = dashboard;
            this.calendar = calendar;
            this.persistedChanges = persistedChanges;
            this.date = date;
        }
    }

    private static final class TodayProjection {
        final TodayUiModel today;
        final boolean persistedChanges;
        final LocalDate date;

        TodayProjection(TodayUiModel today, boolean persistedChanges, LocalDate date) {
            this.today = today;
            this.persistedChanges = persistedChanges;
            this.date = date;
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
