package com.autosecretary.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.PlanFocusUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.PlanningSettingsRepository;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.domain.WorkItem;
import com.autosecretary.ui.editor.ObligationEditorState;
import com.autosecretary.ui.editor.StepEditorState;
import com.autosecretary.ui.settings.PlanningSettingsEditorState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/** Owns all retained screen state and DB-bound user actions. */
public final class MainViewModel extends ViewModel {
    private static final String SURFACE = "surface";
    private static final String FILTER = "filter";
    private static final String EDITOR = "editor";
    private static final String PLANNING_EDITOR = "planningEditor";

    private final SavedStateHandle savedState;
    private final PlanFocusUseCase planFocus;
    private final WorkItemRepository repository;
    private final MoveWorkItemUseCase moveWorkItem;
    private final TimeProvider clock;
    private final PlanningSettingsRepository planningSettings;
    private final ExecutorService databaseExecutor;
    private final Executor uiExecutor;
    private final Runnable changeNotifier;
    private final MutableLiveData<MainUiState> state;
    private final MutableLiveData<MainUiEffect> effects = new MutableLiveData<>();
    private final List<Future<?>> running = new ArrayList<>();
    private volatile boolean cleared;
    private long effectSequence;

    public MainViewModel(
            SavedStateHandle savedState,
            PlanFocusUseCase planFocus,
            WorkItemRepository repository,
            MoveWorkItemUseCase moveWorkItem,
            TimeProvider clock,
            PlanningSettingsRepository planningSettings,
            ExecutorService databaseExecutor,
            Executor uiExecutor,
            Runnable changeNotifier) {
        this.savedState = savedState;
        this.planFocus = planFocus;
        this.repository = repository;
        this.moveWorkItem = moveWorkItem;
        this.clock = clock;
        this.planningSettings = planningSettings;
        this.databaseExecutor = databaseExecutor;
        this.uiExecutor = uiExecutor;
        this.changeNotifier = changeNotifier;
        String surface = savedState.get(SURFACE);
        String filter = savedState.get(FILTER);
        ObligationEditorState editor = savedState.get(EDITOR);
        PlanningSettingsEditorState planningEditor = savedState.get(PLANNING_EDITOR);
        state = new MutableLiveData<>(MainUiState.initial(
                Surface.fromSavedValue(surface),
                WorkItemFilter.fromSavedValue(filter), editor, planningEditor));
        reload();
    }

    public LiveData<MainUiState> state() { return state; }
    public LiveData<MainUiEffect> effects() { return effects; }
    public LocalDate today() { return clock.localNow().toLocalDate(); }

    public void selectSurface(Surface value) {
        savedState.set(SURFACE, value.savedValue());
        update(current().dashboard(), value, current().filter(), current().loading(), null,
                current().completionSignal());
    }

    public void selectFilter(WorkItemFilter value) {
        savedState.set(FILTER, value.savedValue());
        update(current().dashboard(), current().surface(), value, current().loading(), null,
                current().completionSignal());
    }

    public void reload() {
        update(current().dashboard(), current().surface(), current().filter(), true, null,
                current().completionSignal());
        submit(() -> planFocus.execute(Integer.MAX_VALUE), false);
    }

    public void save(WorkItem item) {
        mutate(() -> repository.save(item), false);
    }

    public void openEditor(boolean routine, String existingId) {
        WorkItem existing = null;
        if (existingId != null && current().dashboard() != null) {
            existing = current().dashboard().workItems().stream()
                    .filter(item -> existingId.equals(item.id())).findFirst().orElse(null);
        }
        setEditor(ObligationEditorState.initial(routine, existing, clock.localNow()));
    }

    public void editEditor(ObligationEditorState editor) { setEditor(editor); }

    public void addEditorStep() {
        if (current().editor() != null) setEditor(current().editor().addStep());
    }

    public void removeEditorStep(String stepId) {
        if (current().editor() != null) setEditor(current().editor().removeStep(stepId));
    }

    public void moveEditorStep(String stepId, int delta) {
        if (current().editor() != null) setEditor(current().editor().moveStep(stepId, delta));
    }

    public void submitEditor(ObligationEditorState form) {
        ObligationEditorState checked = form.validated(clock.localNow());
        setEditor(checked);
        if (!checked.valid()) return;
        mutate(() -> repository.save(checked.toWorkItem()), false, () -> setEditor(null));
    }

    public void deleteEditor() {
        ObligationEditorState editor = current().editor();
        if (editor == null || editor.existingId() == null) return;
        mutate(() -> repository.delete(editor.existingId()), false, () -> setEditor(null));
    }

    public void closeEditor() { setEditor(null); }

    public void openPlanningSettings() {
        setPlanningEditor(PlanningSettingsEditorState.from(planningSettings.load()));
    }

    public void editPlanningSettings(PlanningSettingsEditorState value) {
        setPlanningEditor(value);
    }

    public void submitPlanningSettings(PlanningSettingsEditorState form) {
        PlanningSettingsEditorState checked = form.validated();
        setPlanningEditor(checked);
        if (!checked.valid()) return;
        mutate(() -> planningSettings.save(checked.toSettings()), false, () -> {
            MainUiState current = current();
            savedState.set(PLANNING_EDITOR, null);
            state.setValue(new MainUiState(current.dashboard(), current.surface(), current.filter(),
                    current.loading(), current.error(), current.completionSignal(), current.editor(),
                    null));
        });
    }

    public void closePlanningSettings() { setPlanningEditor(null); }

    public void delete(String id) {
        mutate(() -> repository.delete(id), false);
    }

    public void deleteAll(List<String> ids) {
        if (ids.isEmpty()) return;
        mutate(() -> repository.deleteAll(List.copyOf(ids)), false);
    }

    public void complete(String id) {
        mutate(() -> repository.complete(id, clock.localNow()), true);
    }

    public void setStepCompleted(String itemId, String stepId, boolean completed) {
        mutate(() -> repository.setStepCompleted(itemId, stepId, completed, clock.localNow()),
                completed);
    }

    public void move(String id, MoveWorkItemUseCase.Direction direction) {
        MainUiState current = current();
        if (current.dashboard() == null) return;
        LocalDate day = clock.localNow().toLocalDate();
        List<WorkItem> visible = current.surface() == Surface.TODAY
                ? current.dashboard().focus().stream().map(value -> value.workItem())
                        .collect(java.util.stream.Collectors.toList())
                : current.dashboard().workItems().stream()
                        .filter(item -> switch (current.filter()) {
                            case ROUTINES -> item instanceof com.autosecretary.domain.Routine;
                            case DONE -> item instanceof com.autosecretary.domain.Task task
                                    && task.completed();
                            case OPEN -> item.isOpenOn(day);
                        })
                        .collect(java.util.stream.Collectors.toList());
        List<String> order = visible.stream().map(WorkItem::id)
                .collect(java.util.stream.Collectors.toList());
        mutate(() -> moveWorkItem.execute(id, direction, day, order), false);
    }

    public void omitToday(String id) {
        mutate(() -> moveWorkItem.omitToday(id), false);
    }

    public void undo() {
        mutate(() -> repository.undoLatest(clock.localNow()), false);
    }

    public void consumeError() {
        MainUiState value = current();
        if (value.error() == null) return;
        state.setValue(new MainUiState(value.dashboard(), value.surface(), value.filter(),
                value.loading(), null, value.completionSignal(), value.editor(),
                value.planningEditor()));
    }

    public void applyChangeSet(List<BulkChange> changes, String undoLabel) {
        mutate(() -> repository.applyChangeSet(changes, undoLabel, clock.localNow()), false);
    }

    private void mutate(Runnable action, boolean completion) {
        mutate(action, completion, null);
    }

    private void mutate(Runnable action, boolean completion, Runnable onSuccess) {
        synchronized (running) {
            running.removeIf(Future::isDone);
            running.add(databaseExecutor.submit(() -> {
                try {
                    action.run();
                    changeNotifier.run();
                    var dashboard = planFocus.execute(Integer.MAX_VALUE);
                    dispatch(() -> {
                        long completionSignal = current().completionSignal()
                                + (completion ? 1 : 0);
                        update(dashboard, current().surface(), current().filter(), false,
                                null, completionSignal);
                        if (completion) emit(new MainUiEffect.Completion(nextEffectId()));
                        if (onSuccess != null) onSuccess.run();
                    });
                } catch (Throwable error) {
                    postError(error);
                }
            }));
        }
    }

    private void submit(java.util.concurrent.Callable<com.autosecretary.application.DashboardData> action,
                        boolean completion) {
        synchronized (running) {
            running.removeIf(Future::isDone);
            running.add(databaseExecutor.submit(() -> {
                try {
                    var dashboard = action.call();
                    dispatch(() -> update(dashboard, current().surface(), current().filter(), false,
                            null, current().completionSignal() + (completion ? 1 : 0)));
                } catch (Throwable error) {
                    postError(error);
                }
            }));
        }
    }

    private void postError(Throwable error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        dispatch(() -> {
            update(current().dashboard(), current().surface(), current().filter(), false,
                    message, current().completionSignal());
            emit(new MainUiEffect.Error(nextEffectId(), message));
        });
    }

    public void consumeEffect(long id) {
        MainUiEffect effect = effects.getValue();
        if (effect != null && effect.id() == id) effects.setValue(null);
    }

    private void emit(MainUiEffect effect) { effects.setValue(effect); }

    private long nextEffectId() { return ++effectSequence; }

    private void dispatch(Runnable action) {
        uiExecutor.execute(() -> {
            if (!cleared) action.run();
        });
    }

    private MainUiState current() {
        MainUiState value = state.getValue();
        return value == null ? MainUiState.initial(
                Surface.TODAY, WorkItemFilter.OPEN, null, null) : value;
    }

    private void update(
            com.autosecretary.application.DashboardData dashboard,
            Surface surface,
            WorkItemFilter filter,
            boolean loading,
            String error,
            long completionSignal) {
        state.setValue(new MainUiState(dashboard, surface, filter, loading, error,
                completionSignal, current().editor(), current().planningEditor()));
    }

    private void setEditor(ObligationEditorState editor) {
        savedState.set(EDITOR, editor);
        MainUiState current = current();
        state.setValue(new MainUiState(current.dashboard(), current.surface(), current.filter(),
                current.loading(), current.error(), current.completionSignal(), editor,
                current.planningEditor()));
    }

    private void setPlanningEditor(PlanningSettingsEditorState editor) {
        savedState.set(PLANNING_EDITOR, editor);
        MainUiState current = current();
        state.setValue(new MainUiState(current.dashboard(), current.surface(), current.filter(),
                current.loading(), current.error(), current.completionSignal(), current.editor(),
                editor));
    }

    @Override
    protected void onCleared() {
        cleared = true;
        synchronized (running) {
            for (Future<?> future : running) future.cancel(true);
            running.clear();
        }
    }
}
