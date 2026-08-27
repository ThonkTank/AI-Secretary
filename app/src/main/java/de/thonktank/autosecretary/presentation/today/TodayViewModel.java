package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.RepetitionInputReducer;
import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.RewardEffect;
import de.thonktank.autosecretary.RewardEffectQueue;
import de.thonktank.autosecretary.UiCommand;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.calendar.CalendarDataSource;
import de.thonktank.autosecretary.calendar.CalendarResult;
import de.thonktank.autosecretary.data.preferences.DisplayPreferences;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
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
import de.thonktank.autosecretary.presentation.navigation.AppDestination;
import de.thonktank.autosecretary.presentation.navigation.AppNavigator;
import de.thonktank.autosecretary.presentation.observable.DashboardInvalidationRouting;
import de.thonktank.autosecretary.presentation.observable.LatestReadPipeline;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationCause;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.data.observable.ClockSnapshot;
import de.thonktank.autosecretary.timer.TimerManager;
import de.thonktank.autosecretary.timer.TimerSession;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public final class TodayViewModel extends ViewModel implements TodayCommandDispatcher.Handlers {
    private static final String SAVED_REQUESTS = "today_requests";
    private static final String SAVED_REQUEST_SEQUENCE = "today_request_sequence";
    private static final String SAVED_TIMER_PERMISSION_WARNED = "today_timer_permission_warned";
    private final TaskUseCases tasks;
    private final DashboardPresenter dashboard;
    private final CalendarDataSource calendar;
    private final UiPreferences preferences;
    private final Clock clock;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final AppNavigator navigator;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final LatestReadPipeline<PresentationInvalidation, Content> contentReads;
    private final LatestReadPipeline<PresentationInvalidation, FocusStepLimit> appearanceReads;
    private final RewardEffectQueue rewardQueue = new RewardEffectQueue();
    private final RepetitionInputReducer repetitionInputReducer = new RepetitionInputReducer();
    private final TodayRequestSavedStateAdapter requestState =
            new TodayRequestSavedStateAdapter();
    private final MutableStateFlow<TodayScreenState> state;
    private final Object stateLock = new Object();
    private final Object actionLock = new Object();
    private final TodayCoordinator todayCoordinator;
    @Nullable private final TimerManager timers;
    private final TimerManager.Listener timerListener = this::publishTimers;
    private boolean timerPermissionWarningShown;
    private TodayScreenState current;
    private LocalDate loadedDate;
    private List<CalendarEventSnapshot> calendarEvents = Collections.emptyList();
    private long requestSequence;

    public TodayViewModel(AppContainer container, AppNavigator navigator,
                   SavedStateHandle savedState, ExecutorService worker) {
        this(container.tasks, container.dashboardPresenter, container.calendar,
                container.uiPreferences, container.clock, container.logger, container.texts,
                container.presentationInvalidations, container.timers, navigator,
                savedState, worker, null);
    }

    public TodayViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, PresentationInvalidationSource invalidations,
                  SavedStateHandle savedState, ExecutorService worker,
                  @Nullable Executor collectionExecutor) {
        this(tasks, dashboard, calendar, preferences, clock, logger, texts, invalidations,
                null, destination -> { }, savedState, worker, collectionExecutor);
    }

    public TodayViewModel(TaskUseCases tasks, DashboardPresenter dashboard, CalendarDataSource calendar,
                  UiPreferences preferences, Clock clock, AppLogger logger,
                  UiTextProvider texts, PresentationInvalidationSource invalidations,
                  @Nullable TimerManager timers, AppNavigator navigator,
                  SavedStateHandle savedState,
                  ExecutorService worker, @Nullable Executor collectionExecutor) {
        this.tasks = tasks;
        this.dashboard = dashboard;
        this.calendar = calendar;
        this.preferences = preferences;
        this.clock = clock;
        this.logger = logger;
        this.texts = texts;
        this.navigator = navigator;
        this.savedState = savedState;
        this.worker = worker;
        this.timers = timers;
        DisplayPreferences display = preferences.displayPreferences();
        List<TodayRequest> restored = requestState.decode(savedState.get(SAVED_REQUESTS));
        Long sequence = savedState.get(SAVED_REQUEST_SEQUENCE);
        requestSequence = sequence == null ? 0L : sequence;
        for (TodayRequest request : restored)
            requestSequence = Math.max(requestSequence, sequenceOf(request.id));
        Boolean warned = savedState.get(SAVED_TIMER_PERMISSION_WARNED);
        timerPermissionWarningShown = Boolean.TRUE.equals(warned);
        TodayUiModel initial = TodayUiModel.empty();
        current = new TodayScreenState(TodayFeatureState.idle(initial), true,
                Collections.emptySet(), RepetitionInputState.idle(), display.focusStepLimit,
                timers == null ? TimerManager.Snapshot.empty() : timers.snapshot(),
                rewardQueue.snapshot(), restored);
        state = StateFlowKt.MutableStateFlow(current);
        todayCoordinator = new TodayCoordinator(initial,
                new TodayCommandDispatcher(this), this::publishTodayFeatureState);
        if (timers != null) timers.addListener(timerListener);
        DashboardInvalidationRouting routing = new DashboardInvalidationRouting(
                invalidations, this::loadedDashboardDate);
        if (collectionExecutor == null) {
            contentReads = LatestReadPipeline.prepared(routing.getContentChanges(), worker,
                    ignored -> prepareContent(), this::loadContent, this::publishContent,
                    this::contentReadFailed);
            appearanceReads = LatestReadPipeline.reading(routing.getTodayPreferenceChanges(), worker,
                    this::loadAppearance, this::publishAppearance,
                    this::appearanceReadFailed);
        } else {
            contentReads = LatestReadPipeline.prepared(routing.getContentChanges(), worker,
                    collectionExecutor, ignored -> prepareContent(), this::loadContent,
                    this::publishContent, this::contentReadFailed);
            appearanceReads = LatestReadPipeline.reading(routing.getTodayPreferenceChanges(), worker,
                    collectionExecutor, this::loadAppearance, this::publishAppearance,
                    this::appearanceReadFailed);
        }
    }

    public StateFlow<TodayScreenState> state() { return state; }

    /** The only Today input; host, view and worker races reduce serially. */
    public void dispatch(TodayAction action) {
        if (action == null) throw new IllegalArgumentException("Today action is required");
        synchronized (actionLock) { reduce(action); }
    }

    private void reduce(TodayAction action) {
        switch (action.kind) {
            case ADD_TASK:
                navigator.navigate(AppDestination.newTask());
                return;
            case OPEN_TASK_MENU:
                enqueue(TodayRequest.task(nextRequestId(), TodayRequest.Kind.TASK_MENU,
                        action.target));
                return;
            case EDIT_TASK: {
                TodayRequest request = consume(action.id, TodayRequest.Kind.TASK_MENU);
                if (request != null)
                    navigator.navigate(AppDestination.editTask(TaskId.of(request.taskId)));
                return;
            }
            case REQUEST_MOVE_TASK:
                replaceTaskRequest(action.id, TodayRequest.Kind.TASK_MENU,
                        TodayRequest.Kind.CHOOSE_MOVE);
                return;
            case MOVE_TASK: {
                TodayRequest request = consume(action.id, TodayRequest.Kind.CHOOSE_MOVE);
                if (request != null)
                    move(request.taskId, request.target.slot, action.slot);
                return;
            }
            case REQUEST_DELETE_TASK: {
                replaceTaskRequest(action.id, TodayRequest.Kind.TASK_MENU,
                        TodayRequest.Kind.CONFIRM_DELETE);
                return;
            }
            case CONFIRM_DELETE_TASK: {
                TodayRequest request = consume(action.id, TodayRequest.Kind.CONFIRM_DELETE);
                if (request != null) delete(request.taskId);
                return;
            }
            case CONFIRM_CLOSE_TASK: {
                TodayRequest request = consume(action.id, TodayRequest.Kind.CONFIRM_CLOSE);
                if (request != null) close(request.taskId);
                return;
            }
            case ACKNOWLEDGE_REQUEST:
                acknowledgeRequest(action.id);
                return;
            case ACKNOWLEDGE_REWARD:
                publishRewards(rewardQueue.acknowledge(action.id));
                return;
            default:
                todayCoordinator.emit(action);
        }
    }

    private void enqueue(TodayRequest request) {
        synchronized (stateLock) { publish(current.enqueue(request)); }
    }

    @Nullable private TodayRequest consume(String id, TodayRequest.Kind kind) {
        synchronized (stateLock) {
            TodayRequest request = current.request(id);
            if (request == null || request.kind != kind) return null;
            publish(current.acknowledge(id));
            return request;
        }
    }

    private void acknowledgeRequest(String id) {
        synchronized (stateLock) { publish(current.acknowledge(id)); }
    }

    private void replaceTaskRequest(String id, TodayRequest.Kind currentKind,
                                    TodayRequest.Kind nextKind) {
        synchronized (stateLock) {
            TodayRequest request = current.request(id);
            if (request == null || request.kind != currentKind) return;
            TodayRequest confirmation = TodayRequest.task(nextRequestId(),
                    nextKind, request.target);
            publish(current.replace(id, confirmation));
        }
    }

    private String nextRequestId() {
        synchronized (stateLock) {
            requestSequence++;
            savedState.set(SAVED_REQUEST_SEQUENCE, requestSequence);
            return "today-request-" + requestSequence;
        }
    }

    private static long sequenceOf(String id) {
        if (id == null || !id.startsWith("today-request-")) return 0L;
        try {
            return Long.parseLong(id.substring("today-request-".length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void reduceRepetitionInput(TodayAction action) {
        if (action.kind == TodayAction.Kind.SUBMIT_REPETITION
                && restTimerBlocks(action.id)) {
            enqueue(TodayRequest.feedback(nextRequestId(), TodayRequest.Kind.INFO,
                    texts.text(R.string.rest_timer_still_running)));
            return;
        }
        RepetitionInputReducer.Submission submission;
        synchronized (stateLock) {
            RepetitionInputReducer.Result result = repetitionInputReducer.reduce(
                    current.repetitionInput, current.today(), action);
            submission = result.submission;
            if (result.state != current.repetitionInput) {
                publish(current.withRepetitionInput(result.state));
            }
        }
        if (submission == null) return;
        if (submission.correction())
            correctRepetitionResult(submission.stepId, submission.editingIndex,
                    submission.value);
        else recordRepetitionResult(submission.stepId, submission.value);
    }

    private void recordRepetitionResult(String stepId, int repetitions) {
        runTodayStepResult(command(UiCommand.Kind.RECORD_REPETITION_RESULT, stepId),
                () -> tasks.recordRepetitionResult.execute(stepId, repetitions));
    }
    private void correctRepetitionResult(String stepId, int index, int repetitions) {
        runTodayStepResult(command(UiCommand.Kind.CORRECT_REPETITION_RESULT, stepId),
                () -> tasks.correctRepetitionResult.execute(stepId, index, repetitions));
    }
    private void close(String taskId) {
        runTodayReward(command(UiCommand.Kind.CLOSE, taskId),
                () -> tasks.closeOngoing.execute(TaskId.of(taskId)));
    }
    private void move(String taskId, @Nullable TaskSlot sourceSlot, TaskSlot targetSlot) {
        run(command(UiCommand.Kind.MOVE, taskId), () -> {
            ScheduleMoveResult result = tasks.moveTaskPlacement.execute(
                    TaskId.of(taskId), sourceSlot, targetSlot);
            if (result != ScheduleMoveResult.MOVED)
                throw new IllegalArgumentException(scheduleMoveMessage(result));
        });
    }
    private void delete(String taskId) {
        run(command(UiCommand.Kind.DELETE, taskId),
                () -> tasks.delete.execute(TaskId.of(taskId)));
    }

    @Override public void handleCompleteOccurrence(String occurrenceId) {
        runTodayReward(command(UiCommand.Kind.COMPLETE, occurrenceId),
                () -> tasks.complete.execute(occurrenceId));
    }

    @Override public void handleRequestClose(String taskId, String title) {
        enqueue(TodayRequest.close(nextRequestId(), taskId, title));
    }

    @Override public void handleCompleteRemaining(String occurrenceId) {
        Set<String> timerStepIds = focusStepIds();
        runTodayReward(command(UiCommand.Kind.COMPLETE_REMAINING, occurrenceId),
                () -> {
                    RewardReceipt receipt = tasks.completeRemainingSteps.execute(occurrenceId);
                    if (timers != null)
                        for (String stepId : timerStepIds) timers.resetForStep(stepId);
                    return receipt;
                });
    }

    @Override public void handleHarvest(String occurrenceId) {
        runTodayReward(command(UiCommand.Kind.HARVEST, occurrenceId),
                () -> tasks.harvest.execute(occurrenceId));
    }

    @Override public void handleDefer(String occurrenceId) {
        run(command(UiCommand.Kind.DEFER, occurrenceId),
                () -> tasks.defer.execute(occurrenceId));
    }

    @Override public void handleToggleStep(String stepId) {
        runTodayReward(command(UiCommand.Kind.TOGGLE_STEP, stepId),
                () -> {
                    RewardReceipt receipt = tasks.toggleStep.execute(stepId);
                    if (timers != null) timers.resetForStep(stepId);
                    return receipt;
                });
    }

    @Override public void handleToggleStepWithDelay(String stepId, long chosenDelayMillis) {
        runTodayReward(command(UiCommand.Kind.TOGGLE_STEP, stepId),
                () -> {
                    RewardReceipt receipt = tasks.toggleStep.execute(stepId, chosenDelayMillis);
                    if (timers != null) timers.resetForStep(stepId);
                    return receipt;
                });
    }

    @Override public void handleFinishStep(String stepId) {
        runTodayReward(command(UiCommand.Kind.FINISH_STEP, stepId),
                () -> {
                    RewardReceipt receipt = tasks.finishStepForToday.execute(stepId);
                    if (timers != null) timers.resetForStep(stepId);
                    return receipt;
                });
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

    @Override public void handleStartDurationTimer(String stepId, String title, int seconds) {
        if (timers == null) return;
        timers.start(stepId, title, TimerSession.Kind.DURATION, seconds);
        warnAboutTimerPermissions();
    }

    @Override public void handlePauseTimer(String timerId) {
        if (timers != null) timers.pause(timerId);
    }

    @Override public void handleResumeTimer(String timerId) {
        if (timers != null) {
            timers.resume(timerId);
            warnAboutTimerPermissions();
        }
    }

    @Override public void handleResetTimer(String timerId) {
        if (timers != null) timers.reset(timerId);
    }

    @Override public void handleObserveTimer(String timerId) {
        if (timers != null) timers.observeCompletion(timerId);
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

    private void runTodayReward(UiCommand key, RewardAction action) {
        if (!begin(key, false)) return;
        worker.execute(() -> {
            try {
                RewardReceipt receipt = action.run();
                finishCommand(key);
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
                finishCommand(key);
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
                startRestTimerAfterRecordedSet(result);
                finishCommand(key);
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
                    finishCommand(key);
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

    private void finishCommand(UiCommand key) {
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            publish(current.withRunningActions(actions).withLoading(false));
        }
    }

    private void enqueueReward(RewardReceipt receipt, UiCommand key) {
        RewardEffect effect = RewardEffect.from(receipt, key);
        if (effect != null) publishRewards(rewardQueue.enqueue(effect));
    }

    private void publishRewards(RewardEffectQueue.Snapshot rewards) {
        synchronized (stateLock) { publish(current.withRewards(rewards)); }
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

    private FocusStepLimit loadAppearance(PresentationInvalidation invalidation) {
        DisplayPreferences display = invalidation.getDisplayPreferences();
        return (display == null ? preferences.displayPreferences() : display).focusStepLimit;
    }

    private LocalDate loadedDashboardDate() {
        synchronized (stateLock) { return loadedDate; }
    }

    private boolean begin(UiCommand key, boolean loading) {
        synchronized (stateLock) {
            if (current.runningActions.contains(key)) return false;
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.add(key);
            TodayScreenState next = current.withRunningActions(actions);
            if (loading) next = next.withLoading(true);
            publish(next);
            return true;
        }
    }

    private void publishContent(Content content) {
        List<CalendarEventSnapshot> eventsSnapshot;
        synchronized (stateLock) {
            eventsSnapshot = content.calendar == null
                    ? calendarEvents : content.calendar.events();
            if (content.calendar != null)
                calendarEvents = Collections.unmodifiableList(
                        new java.util.ArrayList<>(eventsSnapshot));
        }
        TodayUiModel composed = content.dashboard.withCalendar(eventsSnapshot);
        synchronized (stateLock) { loadedDate = content.date; }
        todayCoordinator.rebind(composed);
    }

    private void fail(UiCommand key, String message, RuntimeException error) {
        logger.error("TodayViewModel", "Presentation operation failed: " + key, error);
        String feedback = message == null || message.trim().isEmpty()
                ? texts.text(R.string.error_change_save) : message;
        synchronized (stateLock) {
            Set<UiCommand> actions = new LinkedHashSet<>(current.runningActions);
            actions.remove(key);
            TodayRequest request = TodayRequest.feedback(nextRequestId(),
                    TodayRequest.Kind.ERROR, feedback);
            publish(current.withRunningActions(actions).withLoading(false).enqueue(request));
        }
    }

    private void contentReadFailed(Throwable error) {
        logger.error("TodayViewModel", "Today projection failed", error);
        synchronized (stateLock) {
            TodayRequest request = TodayRequest.feedback(nextRequestId(),
                    TodayRequest.Kind.ERROR, texts.text(R.string.error_dashboard_load));
            publish(current.withLoading(false).enqueue(request));
        }
    }

    private void appearanceReadFailed(Throwable error) {
        logger.error("TodayViewModel", "Today preference projection failed", error);
    }

    private void publishTodayFeatureState(TodayFeatureState feature) {
        synchronized (stateLock) { publish(current.withFeature(feature)); }
    }

    private void publishAppearance(FocusStepLimit focusStepLimit) {
        synchronized (stateLock) { publish(current.withFocusStepLimit(focusStepLimit)); }
    }

    private void publishTimers(TimerManager.Snapshot snapshot) {
        synchronized (stateLock) { publish(current.withTimers(snapshot)); }
    }

    private Set<String> focusStepIds() {
        Set<String> stepIds = new LinkedHashSet<>();
        TodayUiModel today;
        synchronized (stateLock) { today = current.today(); }
        if (today.focus != null)
            for (de.thonktank.autosecretary.presentation.today.FocusStepUiModel step
                    : today.focus.steps) stepIds.add(step.id);
        return stepIds;
    }

    private void startRestTimerAfterRecordedSet(StepExecutionResult result) {
        if (timers == null || result.status != StepExecutionResult.Status.RECORDED
                || result.step == null) return;
        int seconds = result.step.restTimerPolicy.effectiveSeconds(
                preferences.restTimerDefaultSeconds());
        if (seconds < 1) return;
        timers.start(result.step.id, result.step.text, TimerSession.Kind.REST, seconds);
        warnAboutTimerPermissions();
    }

    private boolean restTimerBlocks(String stepId) {
        if (timers == null) return false;
        TimerSession session = timers.snapshot().forStep(stepId);
        return session != null && session.kind == TimerSession.Kind.REST
                && (session.state == TimerSession.State.RUNNING
                || session.state == TimerSession.State.PAUSED);
    }

    private void warnAboutTimerPermissions() {
        if (timers == null || timerPermissionWarningShown || !timers.snapshot().degraded()) return;
        timerPermissionWarningShown = true;
        savedState.set(SAVED_TIMER_PERMISSION_WARNED, true);
        enqueue(TodayRequest.timerPermissions(nextRequestId()));
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

    @Override public void onCleared() {
        contentReads.close();
        appearanceReads.close();
        if (timers != null) timers.removeListener(timerListener);
        worker.shutdown();
    }

    interface Action { void run(); }
    interface RewardAction { RewardReceipt run(); }
    interface StepResultAction { StepExecutionResult run(); }

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

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;
        private final AppNavigator navigator;
        private final Supplier<ExecutorService> workers;

        public Factory(AppContainer container, AppNavigator navigator) {
            this(container, navigator, Executors::newSingleThreadExecutor);
        }

        Factory(AppContainer container, AppNavigator navigator,
                Supplier<ExecutorService> workers) {
            this.container = container;
            this.navigator = navigator;
            this.workers = workers;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(TodayViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new TodayViewModel(container, navigator,
                    SavedStateHandleSupport.createSavedStateHandle(extras), workers.get());
        }
    }

    private void publish(TodayScreenState value) {
        current = value;
        savedState.set(SAVED_REQUESTS, requestState.encode(value.requests));
        state.setValue(value);
    }
}
