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
import de.thonktank.autosecretary.presentation.observable.DashboardInvalidationRouting;
import de.thonktank.autosecretary.presentation.observable.LatestReadPipeline;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationCause;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.data.observable.ClockSnapshot;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;

import java.time.LocalTime;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class TaskViewModel extends ViewModel implements TodayCommandDispatcher.Handlers {
    private static final String NAVIGATION = "navigation";
    private static final String EDITOR = "editor";
    private final TaskUseCases tasks;
    private final DashboardPresenter dashboard;
    private final CalendarDataSource calendar;
    private final UiPreferences preferences;
    private final Clock clock;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final LatestReadPipeline<PresentationInvalidation, Content> contentReads;
    private final LatestReadPipeline<PresentationInvalidation, Appearance> appearanceReads;
    private final MutableLiveData<DashboardUiState> state = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private final RewardEffectQueue rewardQueue = new RewardEffectQueue();
    private final RepetitionInputReducer repetitionInputReducer = new RepetitionInputReducer();
    private final MutableLiveData<RewardEffectQueue.Snapshot> rewardEffects =
            new MutableLiveData<>(rewardQueue.snapshot());
    private final Object stateLock = new Object();
    private final TodayCoordinator todayCoordinator;
    private DashboardUiState current;
    private LocalDate loadedDate;

    TaskViewModel(AppContainer container, SavedStateHandle savedState, ExecutorService worker) {
        this(container.tasks, container.dashboardPresenter, container.calendar,
                container.uiPreferences, container.clock, container.logger, container.texts,
                container.presentationInvalidations, savedState, worker, null);
    }

    TaskViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, PresentationInvalidationSource invalidations,
                  SavedStateHandle savedState, ExecutorService worker,
                  @Nullable Executor collectionExecutor) {
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
        DisplayPreferences display = preferences.displayPreferences();
        current = new DashboardUiState(navigation, TodayUiModel.empty(),
                CalendarUiState.empty(), palette(display.themeMode),
                CalendarPermissionStatus.UNKNOWN, true, Collections.emptySet(), editor,
                RepetitionInputState.idle(), display.themeMode, display.focusStepLimit,
                UpdateUiState.idle());
        todayCoordinator = new TodayCoordinator(current.dashboard,
                new TodayCommandDispatcher(this), this::publishTodayFeatureState);
        state.setValue(current);
        DashboardInvalidationRouting routing = new DashboardInvalidationRouting(
                invalidations, this::loadedDashboardDate);
        if (collectionExecutor == null) {
            contentReads = LatestReadPipeline.prepared(routing.getContentChanges(), worker,
                    ignored -> prepareContent(), this::loadContent, this::publishContent,
                    this::contentReadFailed);
            appearanceReads = LatestReadPipeline.reading(routing.getAppearanceChanges(), worker,
                    this::loadAppearance, this::publishAppearance,
                    this::appearanceReadFailed);
        } else {
            contentReads = LatestReadPipeline.prepared(routing.getContentChanges(), worker,
                    collectionExecutor, ignored -> prepareContent(), this::loadContent,
                    this::publishContent, this::contentReadFailed);
            appearanceReads = LatestReadPipeline.reading(routing.getAppearanceChanges(), worker,
                    collectionExecutor, this::loadAppearance, this::publishAppearance,
                    this::appearanceReadFailed);
        }
        if (editor.open && editor.loading && editor.taskId != null) openEditor(editor.taskId);
    }

    LiveData<DashboardUiState> state() { return state; }
    LiveData<UiEvent> events() { return events; }
    LiveData<RewardEffectQueue.Snapshot> rewardEffects() { return rewardEffects; }

    void acknowledgeRewardEffect(String id) {
        rewardEffects.setValue(rewardQueue.acknowledge(id));
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
        Set<ValidationIssue> issues = new TaskEditorValidator().issues(draft, clock.today());
        if (!issues.isEmpty()) {
            setEditor(TaskEditorStateReducer.allValidationAttempted(draft, issues));
            return;
        }
        UiCommand key = command(draft.taskId == null ? UiCommand.Kind.CREATE
                : UiCommand.Kind.UPDATE, draft.taskId == null ? "new" : draft.taskId);
        if (!begin(key, false)) return;
        setEditor(TaskEditorStateReducer.saving(draft, true));
        worker.execute(() -> {
            try {
                if (draft.taskId == null) tasks.create.execute(draft.definition());
                else tasks.update.execute(TaskId.of(draft.taskId), draft.definition());
                savedState.set(EDITOR, null);
                synchronized (stateLock) {
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    current = current.withRunningActions(actions).withEditor(EditorUiState.closed());
                    state.postValue(current);
                }
            } catch (RuntimeException error) {
                logger.error("TaskViewModel", "Editor save failed", error);
                synchronized (stateLock) {
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    EditorUiState failed = TaskEditorStateReducer.feedback(
                            TaskEditorStateReducer.saving(draft, false), Collections.emptySet(),
                            EditorUiState.Prompt.NONE, texts.text(R.string.error_change_save));
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
        setEditor(TaskEditorStateReducer.saving(draft, true));
        worker.execute(() -> {
            try {
                tasks.delete.execute(TaskId.of(taskId));
                savedState.set(EDITOR, null);
                synchronized (stateLock) {
                    Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
                    actions.remove(key);
                    current = current.withRunningActions(actions).withEditor(EditorUiState.closed());
                    state.postValue(current);
                }
            } catch (RuntimeException error) {
                logger.error("TaskViewModel", "Editor delete failed", error);
                setEditor(TaskEditorStateReducer.feedback(
                        TaskEditorStateReducer.saving(draft, false), Collections.emptySet(),
                        EditorUiState.Prompt.NONE, texts.text(R.string.error_change_save)));
            }
        });
    }

    boolean updateCalendarPermission(boolean granted, boolean showRationale) {
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
        return changed;
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
                finishCommand(key);
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
                finishCommand(key);
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
                finishTodayCommand(key);
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
                finishTodayCommand(key);
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
                finishTodayCommand(key);
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
                finishTodayCommand(key);
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

    private void finishTodayCommand(UiCommand key) {
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            current = current.withRunningActions(actions).withLoading(false);
            state.postValue(current);
        }
    }

    private void finishCommand(UiCommand key) {
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

    private Content loadContent(PresentationInvalidation invalidation) {
        ClockSnapshot snapshot = invalidation.getClock();
        LocalDate today = snapshot == null ? clock.now().toLocalDate() : snapshot.getDate();
        boolean dashboardOnly = invalidation.getCause() == PresentationInvalidationCause.DATABASE
                && loadedDashboardDate() != null;
        return new Content(dashboard.load(today), dashboardOnly ? null : calendar.loadToday(), today);
    }

    private void prepareContent() {
        dashboard.prepare();
    }

    private Appearance loadAppearance(PresentationInvalidation invalidation) {
        ClockSnapshot snapshot = invalidation.getClock();
        LocalTime time = snapshot == null ? clock.now().toLocalTime() : snapshot.getTime();
        return new Appearance(preferences.displayPreferences(), time);
    }

    private LocalDate loadedDashboardDate() {
        synchronized (stateLock) { return loadedDate; }
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

    private void publishContent(Content content) {
        List<CalendarEventSnapshot> eventsSnapshot;
        synchronized (stateLock) {
            eventsSnapshot = content.calendar == null
                    ? current.calendar.events : content.calendar.events();
        }
        TodayUiModel composed = content.dashboard.withCalendar(eventsSnapshot);
        todayCoordinator.rebind(composed);
        TodayFeatureState todayFeature = todayCoordinator.state();
        synchronized (stateLock) {
            if (content.calendar == null)
                current = current.withToday(composed).withTodayFeature(todayFeature);
            else
                current = current.withContent(composed, CalendarUiState.from(content.calendar))
                        .withTodayFeature(todayFeature);
            loadedDate = content.date;
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

    private void contentReadFailed(Throwable error) {
        logger.error("TaskViewModel", "Dashboard projection failed", error);
        update(value -> value.withLoading(false));
        events.postValue(UiEvent.error(texts.text(R.string.error_dashboard_load)));
    }

    private void appearanceReadFailed(Throwable error) {
        logger.error("TaskViewModel", "Dashboard appearance projection failed", error);
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

    private void publishAppearance(Appearance appearance) {
        synchronized (stateLock) {
            current = current.withDisplayPreferences(appearance.preferences,
                    palette(appearance.preferences.themeMode, appearance.time));
            state.postValue(current);
        }
    }

    private DayPalette palette(UiThemeMode mode) {
        return palette(mode, clock.time());
    }

    private static DayPalette palette(UiThemeMode mode, LocalTime time) {
        return DayPalette.at(time, DayPalette.Mode.valueOf(mode.name()));
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
        contentReads.close();
        appearanceReads.close();
        worker.shutdown();
    }

    interface Action { void run(); }
    interface RewardAction { RewardReceipt run(); }
    interface StepResultAction { StepExecutionResult run(); }
    interface StateChange { DashboardUiState apply(DashboardUiState state); }

    private static final class Content {
        final TodayUiModel dashboard;
        final CalendarResult calendar;
        final LocalDate date;
        Content(TodayUiModel dashboard, CalendarResult calendar, LocalDate date) {
            this.dashboard = dashboard;
            this.calendar = calendar;
            this.date = date;
        }
    }

    private static final class Appearance {
        final DisplayPreferences preferences;
        final LocalTime time;

        Appearance(DisplayPreferences preferences, LocalTime time) {
            this.preferences = preferences;
            this.time = time;
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
