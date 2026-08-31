package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.domain.model.StepActivationKind;
import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.StepTransition;
import de.thonktank.autosecretary.domain.model.TaskCatalog;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.usecase.CatalogUseCases;
import de.thonktank.autosecretary.domain.usecase.FlowUseCases;
import de.thonktank.autosecretary.domain.usecase.TodayUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.UiTextProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Sole state and persistence owner for editing flow definitions. */
public final class FlowSetupViewModel extends ViewModel {
    private static final String SELECTED_TASK = "flow_setup_selected_task";

    private final CatalogUseCases catalogUseCases;
    private final FlowUseCases flows;
    private final TodayUseCases today;
    private final FlowWakeScheduler wakeScheduler;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final MutableStateFlow<FlowSetupScreenState> state;
    private final Map<String, FlowSetupDraft> drafts = new HashMap<>();
    private final Object actionLock = new Object();
    private volatile FlowSetupScreenState current = FlowSetupScreenState.loading();
    private long loadGeneration;
    private long feedbackSequence;
    private volatile boolean cleared;

    FlowSetupViewModel(CatalogUseCases catalogUseCases, FlowUseCases flows,
                       TodayUseCases today, FlowWakeScheduler wakeScheduler,
                       AppLogger logger, UiTextProvider texts, SavedStateHandle savedState,
                       ExecutorService worker) {
        this.catalogUseCases = catalogUseCases;
        this.flows = flows;
        this.today = today;
        this.wakeScheduler = wakeScheduler;
        this.logger = logger;
        this.texts = texts;
        this.savedState = savedState;
        this.worker = worker;
        state = StateFlowKt.MutableStateFlow(current);
        loadCatalog();
    }

    public StateFlow<FlowSetupScreenState> state() { return state; }

    public void dispatch(FlowSetupAction action) {
        if (action == null) throw new IllegalArgumentException("Flow-setup action is required");
        synchronized (actionLock) {
            switch (action.kind) {
                case SELECT_TASK:
                    selectTask(action.taskIndex);
                    return;
                case UPDATE_DRAFT:
                    updateDraft(action.draft);
                    return;
                case SAVE_RESOURCE:
                    saveResource(action.id, action.name, action.capacity);
                    return;
                case SAVE:
                    save();
                    return;
                case ACKNOWLEDGE_FEEDBACK:
                    publish(current.acknowledgeFeedback(action.feedbackId));
            }
        }
    }

    private void loadCatalog() {
        long generation = ++loadGeneration;
        worker.execute(() -> {
            try {
                TaskCatalog catalog = catalogUseCases.loadTaskCatalog.execute();
                int index = restoredIndex(catalog, savedState.get(SELECTED_TASK));
                StepFlowSetup setup = index < 0 ? null
                        : flows.loadStepFlowSetup.execute(catalog.items.get(index).task.id);
                if (cleared || generation != loadGeneration) return;
                FlowSetupDraft draft = draftFor(setup);
                publish(current.loaded(catalog, index, setup, draft));
            } catch (RuntimeException error) {
                failed("Could not load flow setup", error);
            }
        });
    }

    private void selectTask(int index) {
        FlowSetupScreenState snapshot = current;
        if (snapshot.saving || index < 0 || index >= snapshot.catalog.items.size()
                || index == snapshot.selectedTaskIndex) return;
        TaskId taskId = snapshot.catalog.items.get(index).task.id;
        savedState.set(SELECTED_TASK, taskId.value);
        long generation = ++loadGeneration;
        publish(snapshot.loadingTask(index));
        worker.execute(() -> {
            try {
                StepFlowSetup setup = flows.loadStepFlowSetup.execute(taskId);
                if (cleared || generation != loadGeneration) return;
                publish(current.loaded(snapshot.catalog, index, setup, draftFor(setup)));
            } catch (RuntimeException error) {
                failed("Could not load selected flow setup", error);
            }
        });
    }

    private void updateDraft(FlowSetupDraft draft) {
        if (draft == null || current.setup == null || current.saving) return;
        drafts.put(current.setup.task.id.value, draft);
        publish(current.withDraft(draft));
    }

    private void saveResource(String id, String name, int capacity) {
        FlowSetupScreenState snapshot = current;
        if (snapshot.setup == null || snapshot.loading || snapshot.saving) return;
        TaskId taskId = snapshot.setup.task.id;
        publish(snapshot.withSaving());
        worker.execute(() -> {
            try {
                flows.saveCapacityResource.execute(id, name, capacity);
                StepFlowSetup refreshed = flows.loadStepFlowSetup.execute(taskId);
                if (cleared || current.setup == null || !taskId.equals(current.setup.task.id))
                    return;
                FlowSetupDraft draft = drafts.getOrDefault(taskId.value, snapshot.draft);
                publish(current.withSetup(refreshed, draft));
            } catch (RuntimeException error) {
                failed("Could not save capacity resource", error);
            }
        });
    }

    private void save() {
        FlowSetupScreenState snapshot = current;
        if (snapshot.setup == null || snapshot.loading || snapshot.saving) return;
        publish(snapshot.withSaving());
        worker.execute(() -> {
            try {
                Map<String, StepActivationKind> activations = activations(
                        snapshot.setup, snapshot.draft);
                flows.saveStepFlowSetup.execute(snapshot.setup.task.id, activations,
                        snapshot.draft.transitions,
                        snapshot.draft.domainLeases(snapshot.setup.task.id));
                today.materializeDue.execute();
                flows.activateReadyFlows.execute();
                wakeScheduler.reschedule();
                drafts.remove(snapshot.setup.task.id.value);
                feedback(texts.text(R.string.flow_setup_saved), true);
            } catch (RuntimeException error) {
                failed("Could not save flow setup", error);
            }
        });
    }

    private static Map<String, StepActivationKind> activations(
            StepFlowSetup setup, FlowSetupDraft draft) {
        Set<String> targets = new HashSet<>();
        for (StepTransition transition : draft.transitions) targets.add(transition.targetStepId);
        Map<String, StepActivationKind> values = new HashMap<>();
        for (TaskStepTemplate step : setup.steps)
            values.put(step.id, targets.contains(step.id)
                    ? StepActivationKind.FOLLOW_UP : StepActivationKind.SCHEDULED);
        return values;
    }

    private FlowSetupDraft draftFor(StepFlowSetup setup) {
        if (setup == null) return FlowSetupDraft.empty();
        return drafts.computeIfAbsent(setup.task.id.value, ignored -> FlowSetupDraft.from(setup));
    }

    private void failed(String operation, RuntimeException error) {
        logger.error("FlowSetup", operation, error);
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty())
            message = texts.text(R.string.error_change_save);
        feedback(message, false);
    }

    private void feedback(String message, boolean saved) {
        if (!cleared) publish(current.withFeedback(
                new FlowSetupScreenState.Feedback(++feedbackSequence, message, saved)));
    }

    private void publish(FlowSetupScreenState value) {
        current = value;
        state.setValue(value);
    }

    private static int restoredIndex(TaskCatalog catalog, String selectedId) {
        if (catalog.items.isEmpty()) return -1;
        if (selectedId != null)
            for (int index = 0; index < catalog.items.size(); index++)
                if (selectedId.equals(catalog.items.get(index).task.id.value)) return index;
        return 0;
    }

    @Override protected void onCleared() {
        cleared = true;
        loadGeneration++;
        worker.shutdown();
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;

        public Factory(AppContainer container) { this.container = container; }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(FlowSetupViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new FlowSetupViewModel(container.catalog, container.flows,
                    container.today, container.flowWakeScheduler,
                    container.logger, container.texts,
                    SavedStateHandleSupport.createSavedStateHandle(extras),
                    Executors.newSingleThreadExecutor());
        }
    }
}
