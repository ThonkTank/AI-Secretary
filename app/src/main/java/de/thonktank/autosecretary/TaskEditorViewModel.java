package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.domain.model.TaskDetails;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.StepFlowSetup;
import de.thonktank.autosecretary.domain.model.TrainingContext;
import de.thonktank.autosecretary.domain.usecase.CatalogUseCases;
import de.thonktank.autosecretary.domain.usecase.FlowUseCases;
import de.thonktank.autosecretary.domain.usecase.TodayUseCases;
import de.thonktank.autosecretary.domain.usecase.TrainingUseCases;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.UiTextProvider;
import de.thonktank.autosecretary.presentation.editor.TrainingHistoryUiMapper;
import de.thonktank.autosecretary.presentation.editor.TrainingHistoryUiModel;

import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Sole state owner and command boundary for the task editor. */
public final class TaskEditorViewModel extends ViewModel {
    private static final String SAVED_EDITOR = "editor";
    private static final String SAVED_REQUESTS = "editor_requests";
    private static final String SAVED_REQUEST_SEQUENCE = "editor_request_sequence";

    private final CatalogUseCases catalog;
    private final FlowUseCases flows;
    private final TodayUseCases today;
    private final TrainingUseCases training;
    private final Clock clock;
    private final AppLogger logger;
    private final UiTextProvider texts;
    private final TrainingHistoryUiMapper trainingHistoryMapper;
    private final FlowWakeScheduler wakeScheduler;
    private final SavedStateHandle savedState;
    private final ExecutorService worker;
    private final TaskEditorRequestSavedStateAdapter requestAdapter =
            new TaskEditorRequestSavedStateAdapter();
    private final MutableStateFlow<TaskEditorScreenState> state;
    private final Set<UiCommand> running = new LinkedHashSet<>();
    private final Object lock = new Object();
    private final Object actionLock = new Object();
    private TaskEditorScreenState current;
    private long requestSequence;
    private long openGeneration;

    TaskEditorViewModel(CatalogUseCases catalog, FlowUseCases flows, TodayUseCases today,
                        TrainingUseCases training,
                        Clock clock, AppLogger logger, UiTextProvider texts,
                        SavedStateHandle savedState, ExecutorService worker) {
        this(catalog, flows, today, training, clock, logger, texts, savedState, worker, null);
    }

    TaskEditorViewModel(CatalogUseCases catalog, FlowUseCases flows, TodayUseCases today,
                        TrainingUseCases training,
                        Clock clock, AppLogger logger, UiTextProvider texts,
                        SavedStateHandle savedState, ExecutorService worker,
                        FlowWakeScheduler wakeScheduler) {
        this.catalog = catalog;
        this.flows = flows;
        this.today = today;
        this.training = training;
        this.clock = clock;
        this.logger = logger;
        this.texts = texts;
        trainingHistoryMapper = new TrainingHistoryUiMapper(texts);
        this.savedState = savedState;
        this.worker = worker;
        this.wakeScheduler = wakeScheduler;
        EditorUiState editor = EditorUiState.fromBundle(savedState.get(SAVED_EDITOR));
        if (editor.saving) {
            editor = TaskEditorStateReducer.feedback(
                    TaskEditorStateReducer.saving(editor, false), Collections.emptySet(),
                    EditorUiState.Prompt.NONE, texts.text(R.string.error_change_save));
        }
        List<TaskEditorRequest> requests = requestAdapter.decode(savedState.get(SAVED_REQUESTS));
        Long restoredSequence = savedState.get(SAVED_REQUEST_SEQUENCE);
        requestSequence = restoredSequence == null ? 0L : restoredSequence;
        for (TaskEditorRequest request : requests)
            requestSequence = Math.max(requestSequence, sequenceOf(request.id));
        current = new TaskEditorScreenState(editor, Collections.emptyMap(), requests);
        state = StateFlowKt.MutableStateFlow(current);
        if (editor.open && editor.loading)
            open(editor.taskId, null, false);
    }

    public StateFlow<TaskEditorScreenState> state() { return state; }

    public void dispatch(TaskEditorAction action) {
        if (action == null) throw new IllegalArgumentException("Action is required");
        synchronized (actionLock) { reduce(action); }
    }

    private void reduce(TaskEditorAction action) {
        if (action instanceof TaskEditorAction.Open) {
            TaskEditorAction.Open open = (TaskEditorAction.Open) action;
            open(open.taskId, open.stepId, open.addStep);
        } else if (action instanceof TaskEditorAction.DraftChanged)
            changeDraft(((TaskEditorAction.DraftChanged) action).draft);
        else if (action instanceof TaskEditorAction.Save)
            save(((TaskEditorAction.Save) action).draft);
        else if (action instanceof TaskEditorAction.Delete)
            delete(((TaskEditorAction.Delete) action).taskId);
        else if (action instanceof TaskEditorAction.Dismiss)
            dismiss();
        else if (action instanceof TaskEditorAction.RequestAcknowledged)
            acknowledgeRequest(((TaskEditorAction.RequestAcknowledged) action).requestId);
        else if (action instanceof TaskEditorAction.UndoTrainingAdjustment)
            undoTrainingAdjustment(((TaskEditorAction.UndoTrainingAdjustment) action).stepId);
        else throw new IllegalArgumentException("Unsupported editor action " + action.getClass());
    }

    private void open(String taskId, String stepId, boolean addStep) {
        UiCommand key;
        long generation;
        if (taskId == null) {
            synchronized (lock) { generation = ++openGeneration; }
            key = new UiCommand(UiCommand.Kind.LOAD_EDITOR, "new:" + generation);
            begin(key);
        } else {
            key = new UiCommand(UiCommand.Kind.LOAD_EDITOR, taskId);
            if (!begin(key)) return;
            synchronized (lock) { generation = ++openGeneration; }
        }
        if (taskId == null) {
            setContent(EditorUiState.create(defaultSlot(clock.time())));
            worker.execute(() -> {
                try {
                    List<de.thonktank.autosecretary.domain.model.CapacityResource> catalog =
                            flows.loadCapacityResources.execute();
                    finish(key);
                    publishCatalogIfCurrent(generation, catalog);
                } catch (RuntimeException error) {
                    finish(key);
                    logger.error("TaskEditorViewModel", "Capacity catalog load failed", error);
                }
            });
            return;
        }
        setContent(EditorUiState.loading(taskId));
        worker.execute(() -> {
            try {
                TaskDetails details = catalog.loadTaskDetails.execute(TaskId.of(taskId));
                if (details == null) {
                    finish(key);
                    failLoad(generation, texts.text(R.string.error_task_missing),
                            new IllegalArgumentException("Missing task " + taskId));
                    return;
                }
                StepFlowSetup setup = flows.loadStepFlowSetup.execute(TaskId.of(taskId));
                EditorUiState loaded = EditorUiState.edit(details, TaskFlowDraft.from(setup));
                Map<String, TrainingHistoryUiModel> history = trainingHistory(details);
                if (addStep) loaded = TaskEditorStateReducer.addStep(loaded);
                else if (stepId != null) loaded = TaskEditorStateReducer.expandStep(loaded, stepId);
                finish(key);
                publishIfCurrent(generation, loaded, history);
            } catch (RuntimeException error) {
                finish(key);
                failLoad(generation, texts.text(R.string.error_editor_load), error);
            }
        });
    }

    private void publishCatalogIfCurrent(
            long generation,
            List<de.thonktank.autosecretary.domain.model.CapacityResource> catalog) {
        if (catalog == null || catalog.isEmpty()) return;
        synchronized (lock) {
            if (generation != openGeneration || !current.content.open
                    || current.content.taskId != null) return;
            EditorUiState merged = current.content.withCapacityCatalog(catalog);
            if (merged.flowDraft == current.content.flowDraft) return;
            current = current.withContent(merged);
            persistContent();
            state.setValue(current);
        }
    }

    private void dismiss() {
        synchronized (lock) { openGeneration++; }
        setContent(EditorUiState.closed());
    }

    private void changeDraft(EditorUiState draft) {
        if (draft.open && !draft.loading) setContent(draft);
    }

    private void save(EditorUiState draft) {
        Set<ValidationIssue> issues = new TaskEditorValidator().issues(draft, clock.today());
        if (!issues.isEmpty()) {
            setContent(TaskEditorStateReducer.allValidationAttempted(draft, issues));
            return;
        }
        UiCommand key = new UiCommand(draft.taskId == null ? UiCommand.Kind.CREATE
                : UiCommand.Kind.UPDATE, draft.taskId == null ? "new" : draft.taskId);
        if (!begin(key)) return;
        long generation;
        synchronized (lock) { generation = openGeneration; }
        setContent(TaskEditorStateReducer.saving(draft, true));
        worker.execute(() -> {
            try {
                catalog.saveTaskConfiguration.execute(
                        draft.taskId == null ? null : TaskId.of(draft.taskId),
                        draft.definition(), draft.flowConfiguration());
                try {
                    today.materializeDue.execute();
                    flows.activateReadyFlows.execute();
                    if (wakeScheduler != null) wakeScheduler.reschedule();
                } catch (RuntimeException runtimeError) {
                    logger.error("TaskEditorViewModel",
                            "Task saved but flow activation refresh failed", runtimeError);
                }
                finish(key);
                publishIfCurrent(generation, EditorUiState.closed());
            } catch (RuntimeException error) {
                logger.error("TaskEditorViewModel", "Editor save failed", error);
                finish(key);
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty())
                    message = texts.text(R.string.error_change_save);
                publishIfCurrent(generation, TaskEditorStateReducer.feedback(
                        TaskEditorStateReducer.saving(draft, false), Collections.emptySet(),
                        EditorUiState.Prompt.NONE, message));
            }
        });
    }

    private void delete(String taskId) {
        UiCommand key = new UiCommand(UiCommand.Kind.DELETE, taskId);
        if (!begin(key)) return;
        EditorUiState draft;
        long generation;
        synchronized (lock) {
            draft = current.content;
            generation = openGeneration;
        }
        setContent(TaskEditorStateReducer.saving(draft, true));
        worker.execute(() -> {
            try {
                catalog.delete.execute(TaskId.of(taskId));
                finish(key);
                publishIfCurrent(generation, EditorUiState.closed());
            } catch (RuntimeException error) {
                logger.error("TaskEditorViewModel", "Editor delete failed", error);
                finish(key);
                publishIfCurrent(generation, TaskEditorStateReducer.feedback(
                        TaskEditorStateReducer.saving(draft, false), Collections.emptySet(),
                        EditorUiState.Prompt.NONE, texts.text(R.string.error_change_save)));
            }
        });
    }

    private void undoTrainingAdjustment(String stepId) {
        EditorUiState editor;
        TrainingHistoryUiModel history;
        long generation;
        synchronized (lock) {
            editor = current.content;
            history = current.trainingHistoryByStepId.get(stepId);
            generation = openGeneration;
            if (editor.dirty) {
                enqueueLocked(new TaskEditorRequest(nextRequestIdLocked(),
                        texts.text(R.string.training_history_dirty_hint)));
                return;
            }
        }
        if (history == null || !history.canUndo) {
            synchronized (lock) {
                enqueueLocked(new TaskEditorRequest(nextRequestIdLocked(),
                        texts.text(R.string.training_undo_no_longer_available)));
            }
            return;
        }
        UiCommand key = new UiCommand(UiCommand.Kind.TRAINING_ASSISTANT, history.templateId);
        if (!begin(key)) return;
        worker.execute(() -> {
            try {
                if (!training.undoLatestTrainingAdjustment.execute(history.templateId)) {
                    finish(key);
                    rejectUndo(generation, R.string.training_undo_no_longer_available);
                    return;
                }
                TaskDetails details = catalog.loadTaskDetails.execute(TaskId.of(editor.taskId));
                StepFlowSetup setup = flows.loadStepFlowSetup.execute(TaskId.of(editor.taskId));
                EditorUiState refreshed = EditorUiState.edit(details, TaskFlowDraft.from(setup))
                        .withPage(editor.page, editor.returnToSummary)
                        .withExpandedStep(editor.expandedStepId);
                Map<String, TrainingHistoryUiModel> histories = trainingHistory(details);
                finish(key);
                publishIfCurrent(generation, refreshed, histories);
            } catch (RuntimeException error) {
                finish(key);
                logger.error("TaskEditorViewModel", "Training undo failed", error);
                rejectUndo(generation, R.string.training_undo_no_longer_available);
            }
        });
    }

    private Map<String, TrainingHistoryUiModel> trainingHistory(TaskDetails details) {
        Map<String, TrainingHistoryUiModel> result = new LinkedHashMap<>();
        for (TaskStepTemplate step : details.stepTemplates) {
            if (!step.assistantEnabled()) continue;
            TrainingContext context = training.loadTrainingContext.execute(step.id);
            TrainingHistoryUiModel mapped = trainingHistoryMapper.map(context);
            if (mapped != null) result.put(step.id, mapped);
        }
        return result;
    }

    private void rejectUndo(long generation, int message) {
        synchronized (lock) {
            if (generation != openGeneration) return;
            enqueueLocked(new TaskEditorRequest(nextRequestIdLocked(), texts.text(message)));
        }
    }

    private boolean begin(UiCommand key) {
        synchronized (lock) { return running.add(key); }
    }

    private void finish(UiCommand key) {
        synchronized (lock) { running.remove(key); }
    }

    private void failLoad(long generation, String message, RuntimeException error) {
        logger.error("TaskEditorViewModel", "Editor load failed", error);
        synchronized (lock) {
            if (generation != openGeneration) return;
            current = current.withContent(EditorUiState.closed());
            persistContent();
            enqueueLocked(new TaskEditorRequest(nextRequestIdLocked(), message));
        }
    }

    private void publishIfCurrent(long generation, EditorUiState value) {
        synchronized (lock) {
            if (generation != openGeneration) return;
            current = value.open ? current.withContent(value)
                    : current.withContentAndHistory(value, Collections.emptyMap());
            persistContent();
            state.setValue(current);
        }
    }

    private void publishIfCurrent(long generation, EditorUiState value,
                                  Map<String, TrainingHistoryUiModel> history) {
        synchronized (lock) {
            if (generation != openGeneration) return;
            current = current.withContentAndHistory(value, history);
            persistContent();
            state.setValue(current);
        }
    }

    private void setContent(EditorUiState value) {
        synchronized (lock) {
            boolean differentTask = !Objects.equals(current.content.taskId, value.taskId);
            current = !value.open || value.loading || differentTask
                    ? current.withContentAndHistory(value, Collections.emptyMap())
                    : current.withContent(value);
            persistContent();
            state.setValue(current);
        }
    }

    private void acknowledgeRequest(String requestId) {
        synchronized (lock) {
            TaskEditorScreenState next = current.acknowledge(requestId);
            if (next == current) return;
            current = next;
            persistRequests();
            state.setValue(current);
        }
    }

    private void enqueueLocked(TaskEditorRequest request) {
        TaskEditorScreenState next = current.enqueue(request);
        if (next == current) return;
        current = next;
        persistRequests();
        state.setValue(current);
    }

    private String nextRequestIdLocked() {
        requestSequence++;
        savedState.set(SAVED_REQUEST_SEQUENCE, requestSequence);
        return "task-editor:" + requestSequence;
    }

    private void persistContent() {
        savedState.set(SAVED_EDITOR, current.content.open ? current.content.toBundle() : null);
    }

    private void persistRequests() {
        savedState.set(SAVED_REQUESTS, requestAdapter.encode(current.requests));
    }

    private static long sequenceOf(String requestId) {
        if (requestId == null || !requestId.startsWith("task-editor:")) return 0L;
        try {
            return Long.parseLong(requestId.substring("task-editor:".length()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    static TaskSlot defaultSlot(LocalTime time) {
        if (time.isBefore(LocalTime.of(11, 0))) return TaskSlot.MORNING;
        if (time.isBefore(LocalTime.of(17, 0))) return TaskSlot.MIDDAY;
        if (time.isBefore(LocalTime.of(21, 0))) return TaskSlot.EVENING;
        return TaskSlot.LATER;
    }

    @Override protected void onCleared() { worker.shutdown(); }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;
        private final Supplier<ExecutorService> workers;

        public Factory(AppContainer container) { this(container, Executors::newSingleThreadExecutor); }

        Factory(AppContainer container, Supplier<ExecutorService> workers) {
            this.container = container;
            this.workers = workers;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(TaskEditorViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new TaskEditorViewModel(container.catalog, container.flows,
                    container.today, container.training, container.clock, container.logger,
                    container.texts, SavedStateHandleSupport.createSavedStateHandle(extras),
                    workers.get(), container.flowWakeScheduler);
        }
    }
}
