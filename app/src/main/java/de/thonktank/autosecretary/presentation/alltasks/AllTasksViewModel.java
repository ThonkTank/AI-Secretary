package de.thonktank.autosecretary.presentation.alltasks;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiCommand;
import de.thonktank.autosecretary.UiEvent;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Independent state owner and command boundary for the management tab. */
public final class AllTasksViewModel extends ViewModel {
    /** Keep the registry key stable; the stored value is now the complete presentation state. */
    private static final String SAVED_PRESENTATION = "all_tasks_filter";

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
    private final MutableLiveData<AllTasksUiState> state = new MutableLiveData<>();
    private final MutableLiveData<UiEvent> events = new MutableLiveData<>();
    private final Set<UiCommand> running = new LinkedHashSet<>();
    private final Object lock = new Object();
    private AllTasksUiState current;

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
        current = AllTasksUiState.from(null, presentation);
        state.setValue(current);
        if (collectionExecutor == null)
            reads = LatestReadPipeline.reading(invalidations.getCatalogChanges(), worker,
                    ignored -> catalog.execute(), this::publishCatalog,
                    ignored -> events.postValue(UiEvent.error(
                            texts.text(R.string.error_catalog_load))));
        else
            reads = LatestReadPipeline.reading(invalidations.getCatalogChanges(), worker,
                    collectionExecutor, ignored -> catalog.execute(), this::publishCatalog,
                    ignored -> events.postValue(UiEvent.error(
                            texts.text(R.string.error_catalog_load))));
    }

    public LiveData<AllTasksUiState> state() { return state; }
    public LiveData<UiEvent> events() { return events; }

    public void updateQuery(String value) {
        updateFilter(filter -> filter.withQuery(value));
    }
    public void updateStatus(AllTasksUiState.Status value) {
        updateFilter(filter -> filter.withStatus(value));
    }
    public void updateSlots(Set<TaskSlot> value) { updateFilter(filter -> filter.withSlots(value)); }
    public void updateRecurrences(Set<Recurrence> value) {
        updateFilter(filter -> filter.withRecurrences(value));
    }
    public void updateWeekday(int value) { updateFilter(filter -> filter.withWeekday(value)); }
    public void updateMode(AllTasksUiState.Mode value) {
        updatePresentation(presentation -> presentation.withMode(value));
    }
    public void resetVisibleFilters() {
        updateFilter(AllTasksFilter::resetVisibleFilters);
    }
    public void toggleCard(String cardKey) {
        updatePresentation(presentation -> presentation.toggleExpanded(cardKey));
    }
    public void updateFiltersExpanded(boolean value) {
        updatePresentation(presentation -> presentation.withFiltersExpanded(value));
    }

    public void moveSchedule(ScheduleMoveRequest request) {
        UiCommand key = new UiCommand(UiCommand.Kind.ORGANIZE,
                "schedule:" + request.entryId.value);
        run(key, () -> scheduleError(moveSchedule.execute(request)));
    }

    public void moveStep(StepMoveRequest request) {
        UiCommand key = new UiCommand(UiCommand.Kind.ORGANIZE, "step:" + request.stepId.value);
        run(key, () -> stepError(moveStep.execute(request)));
    }

    public void swapSteps(StepSwapRequest request) {
        UiCommand key = new UiCommand(UiCommand.Kind.ORGANIZE, "step:" + request.stepId.value);
        run(key, () -> stepError(swapSteps.execute(request)));
    }

    public void delete(TaskId taskId) {
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
                events.postValue(UiEvent.info(texts.text(R.string.step_effective_next_occurrence)));
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
        events.postValue(UiEvent.error(message));
    }

    private void publishCatalog(TaskCatalog value) {
        synchronized (lock) {
            current = current.withCatalog(value);
            state.postValue(current);
        }
    }

    private void updateFilter(FilterChange change) {
        updatePresentation(presentation -> presentation.withFilter(
                change.apply(presentation.filter)));
    }

    private void updatePresentation(PresentationChange change) {
        synchronized (lock) {
            AllTasksPresentationState presentation = change.apply(current.presentation);
            current = AllTasksUiState.from(current.catalog, presentation);
            savedState.set(SAVED_PRESENTATION, savedStateAdapter.encode(presentation));
            state.setValue(current);
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
