package de.thonktank.autosecretary.presentation.alltasks;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiCommand;

import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.repository.TaskCatalogQuery;
import de.thonktank.autosecretary.domain.usecase.DeleteTask;
import de.thonktank.autosecretary.domain.schedule.MoveScheduleEntry;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveRequest;
import de.thonktank.autosecretary.domain.schedule.ScheduleMoveResult;
import de.thonktank.autosecretary.domain.steps.MoveTaskStep;
import de.thonktank.autosecretary.domain.steps.StepMoveRequest;
import de.thonktank.autosecretary.domain.steps.StepTransferResult;
import de.thonktank.autosecretary.domain.steps.StepSwapRequest;
import de.thonktank.autosecretary.domain.steps.SwapTaskSteps;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.presentation.observable.LatestReadPipeline;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;

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

/** Independent state owner and command boundary for the management tab. */
public final class AllTasksViewModel extends ViewModel {
    /** Keep the registry key stable; the stored value is now the complete presentation state. */
    private static final String SAVED_PRESENTATION = "all_tasks_filter";
    private static final String SAVED_REQUESTS = "all_tasks_requests";
    private static final String SAVED_REQUEST_SEQUENCE = "all_tasks_request_sequence";

    private final TaskCatalogQuery catalog;
    private final MoveScheduleEntry moveSchedule;
    private final MoveTaskStep moveStep;
    private final SwapTaskSteps swapSteps;
    private final DeleteTask deleteTask;
    private final UiTextProvider texts;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final LatestReadPipeline<PresentationInvalidation, TaskCatalog> reads;
    private final AllTasksSavedStateAdapter savedStateAdapter = new AllTasksSavedStateAdapter();
    private final AllTasksRequestSavedStateAdapter requestStateAdapter =
            new AllTasksRequestSavedStateAdapter();
    private final MutableStateFlow<AllTasksScreenState> state;
    private final Set<UiCommand> running = new LinkedHashSet<>();
    private final Object lock = new Object();
    private final Object actionLock = new Object();
    private AllTasksScreenState current;
    private long requestSequence;

    public AllTasksViewModel(TaskCatalogQuery catalog, MoveScheduleEntry moveSchedule,
                      MoveTaskStep moveStep, SwapTaskSteps swapSteps, DeleteTask deleteTask,
                      UiTextProvider texts, SavedStateHandle savedState,
                      ExecutorService worker, PresentationInvalidationSource invalidations,
                      Executor collectionExecutor) {
        this.catalog = catalog;
        this.moveSchedule = moveSchedule;
        this.moveStep = moveStep;
        this.swapSteps = swapSteps;
        this.deleteTask = deleteTask;
        this.texts = texts;
        this.savedState = savedState;
        this.worker = worker;
        AllTasksPresentationState presentation = savedStateAdapter.decode(
                savedState.get(SAVED_PRESENTATION));
        List<AllTasksRequest> requests = requestStateAdapter.decode(
                savedState.get(SAVED_REQUESTS));
        Long restoredSequence = savedState.get(SAVED_REQUEST_SEQUENCE);
        requestSequence = restoredSequence == null ? 0L : restoredSequence;
        for (AllTasksRequest request : requests)
            requestSequence = Math.max(requestSequence, sequenceOf(request.id));
        current = new AllTasksScreenState(AllTasksUiState.from(null, presentation), requests);
        state = StateFlowKt.MutableStateFlow(current);
        if (collectionExecutor == null)
            reads = LatestReadPipeline.reading(invalidations.getCatalogChanges(), worker,
                    ignored -> catalog.execute(), this::publishCatalog,
                    ignored -> enqueueMessage(AllTasksRequest.Kind.ERROR,
                            texts.text(R.string.error_catalog_load)));
        else
            reads = LatestReadPipeline.reading(invalidations.getCatalogChanges(), worker,
                    collectionExecutor, ignored -> catalog.execute(), this::publishCatalog,
                    ignored -> enqueueMessage(AllTasksRequest.Kind.ERROR,
                            texts.text(R.string.error_catalog_load)));
    }

    public StateFlow<AllTasksScreenState> state() { return state; }

    /** The only screen input. Synchronization makes reduction serial across all callers. */
    public void dispatch(AllTasksAction action) {
        if (action == null) throw new IllegalArgumentException("Action is required");
        synchronized (actionLock) { reduce(action); }
    }

    private void reduce(AllTasksAction action) {
        if (action instanceof AllTasksAction.QueryChanged)
            updateFilter(filter -> filter.withQuery(((AllTasksAction.QueryChanged) action).value));
        else if (action instanceof AllTasksAction.StatusChanged)
            updateFilter(filter -> filter.withStatus(((AllTasksAction.StatusChanged) action).value));
        else if (action instanceof AllTasksAction.SlotsChanged)
            updateFilter(filter -> filter.withSlots(((AllTasksAction.SlotsChanged) action).value));
        else if (action instanceof AllTasksAction.RecurrencesChanged)
            updateFilter(filter -> filter.withRecurrences(
                    ((AllTasksAction.RecurrencesChanged) action).value));
        else if (action instanceof AllTasksAction.WeekdayChanged)
            updateFilter(filter -> filter.withWeekday(((AllTasksAction.WeekdayChanged) action).value));
        else if (action instanceof AllTasksAction.ModeChanged)
            updatePresentation(presentation -> presentation.withMode(
                    ((AllTasksAction.ModeChanged) action).value));
        else if (action instanceof AllTasksAction.FiltersExpandedChanged)
            updatePresentation(presentation -> presentation.withFiltersExpanded(
                    ((AllTasksAction.FiltersExpandedChanged) action).value));
        else if (action instanceof AllTasksAction.ResetFilters)
            updateFilter(AllTasksFilter::resetVisibleFilters);
        else if (action instanceof AllTasksAction.CardToggled)
            updatePresentation(presentation -> presentation.toggleExpanded(
                    ((AllTasksAction.CardToggled) action).cardKey));
        else if (action instanceof AllTasksAction.EditTask) {
            AllTasksAction.EditTask edit = (AllTasksAction.EditTask) action;
            enqueueRequest(AllTasksRequest.openEditor(nextRequestId(), edit.taskId, null, false));
        } else if (action instanceof AllTasksAction.EditStep) {
            AllTasksAction.EditStep edit = (AllTasksAction.EditStep) action;
            enqueueRequest(AllTasksRequest.openEditor(nextRequestId(), edit.taskId,
                    edit.stepId, false));
        } else if (action instanceof AllTasksAction.AddStep) {
            AllTasksAction.AddStep edit = (AllTasksAction.AddStep) action;
            enqueueRequest(AllTasksRequest.openEditor(nextRequestId(), edit.taskId, null, true));
        } else if (action instanceof AllTasksAction.DeleteRequested) {
            AllTasksAction.DeleteRequested delete = (AllTasksAction.DeleteRequested) action;
            enqueueRequest(AllTasksRequest.confirmDelete(nextRequestId(), delete.taskId,
                    delete.title));
        } else if (action instanceof AllTasksAction.RequestAcknowledged)
            acknowledgeRequest(((AllTasksAction.RequestAcknowledged) action).requestId);
        else if (action instanceof AllTasksAction.DeleteConfirmed)
            confirmDelete(((AllTasksAction.DeleteConfirmed) action).requestId);
        else if (action instanceof AllTasksAction.ScheduleMoved)
            moveSchedule(((AllTasksAction.ScheduleMoved) action).request);
        else if (action instanceof AllTasksAction.StepMoved)
            moveStep(((AllTasksAction.StepMoved) action).request);
        else if (action instanceof AllTasksAction.StepsSwapped)
            swapSteps(((AllTasksAction.StepsSwapped) action).request);
        else throw new IllegalArgumentException("Unsupported action " + action.getClass());
    }

    private void moveSchedule(ScheduleMoveRequest request) {
        UiCommand key = new UiCommand(UiCommand.Kind.ORGANIZE,
                "schedule:" + request.entryId.value);
        run(key, () -> scheduleError(moveSchedule.execute(request)));
    }

    private void moveStep(StepMoveRequest request) {
        UiCommand key = new UiCommand(UiCommand.Kind.ORGANIZE, "step:" + request.stepId.value);
        run(key, () -> stepError(moveStep.execute(request)));
    }

    private void swapSteps(StepSwapRequest request) {
        UiCommand key = new UiCommand(UiCommand.Kind.ORGANIZE, "step:" + request.stepId.value);
        run(key, () -> stepError(swapSteps.execute(request)));
    }

    private void delete(TaskId taskId) {
        UiCommand key = new UiCommand(UiCommand.Kind.DELETE, taskId.value);
        run(key, () -> {
            deleteTask.execute(taskId);
            return 0;
        });
    }

    private void run(UiCommand key, Command command) {
        if (!begin(key)) return;
        worker.execute(() -> {
            try {
                int error = command.execute();
                if (error != 0) {
                    fail(key, texts.text(error));
                    return;
                }
                synchronized (lock) {
                    running.remove(key);
                }
            } catch (RuntimeException error) {
                fail(key, texts.text(R.string.error_change_save));
            }
        });
    }

    private int scheduleError(ScheduleMoveResult result) {
        switch (result) {
            case MOVED: return 0;
            case REJECTED_INACTIVE_TASK: return R.string.error_management_inactive;
            case REJECTED_DUPLICATE_SLOT: return R.string.error_schedule_duplicate;
            case REJECTED_TODAY_SLOT_OCCUPIED: return R.string.error_schedule_today_occupied;
            default: return R.string.error_task_missing;
        }
    }

    private int stepError(StepTransferResult result) {
        switch (result) {
            case DEFINITION_AND_TODAY_MOVED: case STEPS_SWAPPED: case UNCHANGED: return 0;
            case DEFINITION_ONLY_FOR_FUTURE:
                enqueueMessage(AllTasksRequest.Kind.INFO,
                        texts.text(R.string.step_effective_next_occurrence));
                return 0;
            case REJECTED_ARCHIVED_TASK: return R.string.error_management_inactive;
            case REJECTED_OCCUPIED_TARGET: return R.string.error_step_target_occupied;
            case REJECTED_INVALID_POSITION_SEQUENCE: return R.string.error_step_order_invalid;
            default: return R.string.error_task_missing;
        }
    }

    private boolean begin(UiCommand key) {
        synchronized (lock) {
            if (!running.add(key)) return false;
            return true;
        }
    }

    private void fail(UiCommand key, String message) {
        synchronized (lock) { running.remove(key); }
        enqueueMessage(AllTasksRequest.Kind.ERROR, message);
    }

    private void publishCatalog(TaskCatalog value) {
        synchronized (lock) {
            current = current.withContent(current.content.withCatalog(value));
            state.setValue(current);
        }
    }

    private void updateFilter(FilterChange change) {
        updatePresentation(presentation -> presentation.withFilter(
                change.apply(presentation.filter)));
    }

    private void updatePresentation(PresentationChange change) {
        synchronized (lock) {
            AllTasksPresentationState presentation = change.apply(current.content.presentation);
            current = current.withContent(AllTasksUiState.from(current.content.catalog,
                    presentation));
            savedState.set(SAVED_PRESENTATION, savedStateAdapter.encode(presentation));
            state.setValue(current);
        }
    }

    private String nextRequestId() {
        synchronized (lock) {
            requestSequence++;
            savedState.set(SAVED_REQUEST_SEQUENCE, requestSequence);
            return "all-tasks:" + requestSequence;
        }
    }

    private void enqueueMessage(AllTasksRequest.Kind kind, String message) {
        enqueueRequest(AllTasksRequest.message(nextRequestId(), kind, message));
    }

    private void enqueueRequest(AllTasksRequest request) {
        synchronized (lock) {
            AllTasksScreenState next = current.enqueue(request);
            if (next == current) return;
            current = next;
            persistRequests();
            state.setValue(current);
        }
    }

    private void acknowledgeRequest(String requestId) {
        synchronized (lock) {
            AllTasksScreenState next = current.acknowledge(requestId);
            if (next == current) return;
            current = next;
            persistRequests();
            state.setValue(current);
        }
    }

    private void confirmDelete(String requestId) {
        TaskId taskId;
        synchronized (lock) {
            AllTasksRequest request = current.request(requestId);
            if (request == null || request.kind != AllTasksRequest.Kind.CONFIRM_DELETE
                    || request.taskId == null) return;
            taskId = request.taskId;
            current = current.acknowledge(requestId);
            persistRequests();
            state.setValue(current);
        }
        delete(taskId);
    }

    private void persistRequests() {
        savedState.set(SAVED_REQUESTS, requestStateAdapter.encode(current.requests));
    }

    private static long sequenceOf(String requestId) {
        if (requestId == null || !requestId.startsWith("all-tasks:")) return 0L;
        try {
            return Long.parseLong(requestId.substring("all-tasks:".length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override public void onCleared() {
        reads.close();
        worker.shutdown();
    }

    private interface FilterChange { AllTasksFilter apply(AllTasksFilter filter); }
    private interface PresentationChange {
        AllTasksPresentationState apply(AllTasksPresentationState presentation);
    }
    private interface Command { int execute(); }

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
            if (!modelClass.isAssignableFrom(AllTasksViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new AllTasksViewModel(container.tasks.loadTaskCatalog,
                    container.tasks.moveScheduleEntry, container.tasks.moveTaskStep,
                    container.tasks.swapTaskSteps, container.tasks.delete, container.texts,
                    SavedStateHandleSupport.createSavedStateHandle(extras), workers.get(),
                    container.presentationInvalidations, null);
        }
    }
}
