package com.autosecretary.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.autosecretary.application.DashboardData;
import com.autosecretary.application.MoveWorkItemUseCase;
import com.autosecretary.application.PlanFocusUseCase;
import com.autosecretary.application.TimeProvider;
import com.autosecretary.application.WorkItemRepository;
import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/** Dashboard state and shared work-item actions only. */
public final class MainViewModel extends ViewModel {
    private static final String SURFACE = "surface";
    private static final String FILTER = "filter";

    private final SavedStateHandle savedState;
    private final PlanFocusUseCase planFocus;
    private final WorkItemRepository repository;
    private final MoveWorkItemUseCase moveWorkItem;
    private final TimeProvider clock;
    private final ExecutorService databaseExecutor;
    private final Executor uiExecutor;
    private final Runnable changeNotifier;
    private final MutableLiveData<MainUiState> state;
    private final MutableLiveData<MainUiEffect> effects = new MutableLiveData<>();
    private final List<Future<?>> running = new ArrayList<>();
    private volatile boolean cleared;
    private DashboardData dashboard;
    private long effectSequence;

    public MainViewModel(
            SavedStateHandle savedState,
            PlanFocusUseCase planFocus,
            WorkItemRepository repository,
            MoveWorkItemUseCase moveWorkItem,
            TimeProvider clock,
            ExecutorService databaseExecutor,
            Executor uiExecutor,
            Runnable changeNotifier) {
        this.savedState = savedState;
        this.planFocus = planFocus;
        this.repository = repository;
        this.moveWorkItem = moveWorkItem;
        this.clock = clock;
        this.databaseExecutor = databaseExecutor;
        this.uiExecutor = uiExecutor;
        this.changeNotifier = changeNotifier;
        state = new MutableLiveData<>(new MainUiState.Loading(
                Surface.fromSavedValue(savedState.get(SURFACE)),
                WorkItemFilter.fromSavedValue(savedState.get(FILTER))));
        reload();
    }

    public LiveData<MainUiState> state() { return state; }
    public LiveData<MainUiEffect> effects() { return effects; }

    public void selectSurface(Surface value) {
        savedState.set(SURFACE, value.savedValue());
        setDisplay(value, filter());
    }

    public void selectFilter(WorkItemFilter value) {
        savedState.set(FILTER, value.savedValue());
        setDisplay(surface(), value);
    }

    public void reload() {
        state.setValue(new MainUiState.Loading(surface(), filter()));
        submit(() -> planFocus.execute(Integer.MAX_VALUE));
    }

    public WorkItem findWorkItem(String id) {
        if (dashboard == null || id == null) return null;
        return dashboard.workItems().stream()
                .filter(item -> id.equals(item.id())).findFirst().orElse(null);
    }

    public List<WorkItem> workItems() {
        return dashboard == null ? List.of() : dashboard.workItems();
    }

    public void save(WorkItem item) { mutate(() -> repository.save(item), false); }
    public void delete(String id) { mutate(() -> repository.delete(id), false); }

    public void deleteAll(List<String> ids) {
        if (!ids.isEmpty()) mutate(() -> repository.deleteAll(List.copyOf(ids)), false);
    }

    public void complete(String id) {
        LocalDateTime now = localNow();
        mutate(() -> repository.complete(id, now), true);
    }

    public void setStepCompleted(String itemId, String stepId, boolean completed) {
        LocalDateTime now = localNow();
        mutate(() -> repository.setStepCompleted(itemId, stepId, completed, now), completed);
    }

    public void move(String id, MoveWorkItemUseCase.Direction direction) {
        if (dashboard == null) return;
        LocalDate day = localNow().toLocalDate();
        List<WorkItem> visible = surface() == Surface.TODAY
                ? dashboard.focus().stream().map(value -> value.workItem()).collect(Collectors.toList())
                : dashboard.workItems().stream().filter(item -> switch (filter()) {
                    case ROUTINES -> item instanceof Routine;
                    case DONE -> item instanceof Task task && task.completed();
                    case OPEN -> item.isOpenOn(day);
                }).collect(Collectors.toList());
        List<String> order = visible.stream().map(WorkItem::id).collect(Collectors.toList());
        mutate(() -> moveWorkItem.execute(id, direction, day, order), false);
    }

    public void omitToday(String id) { mutate(() -> moveWorkItem.omitToday(id), false); }

    public void undo() {
        LocalDateTime now = localNow();
        mutate(() -> repository.undoLatest(now), false);
    }

    public void applyChangeSet(List<BulkChange> changes, String undoLabel) {
        LocalDateTime now = localNow();
        mutate(() -> repository.applyChangeSet(changes, undoLabel, now), false);
    }

    public void consumeEffect(long id) {
        MainUiEffect effect = effects.getValue();
        if (effect != null && effect.id() == id) effects.setValue(null);
    }

    private void mutate(Runnable action, boolean completion) {
        track(databaseExecutor.submit(() -> {
            try {
                action.run();
                changeNotifier.run();
                DashboardData loaded = planFocus.execute(Integer.MAX_VALUE);
                dispatch(() -> {
                    dashboard = loaded;
                    state.setValue(new MainUiState.Ready(loaded, surface(), filter()));
                    if (completion) emit(new MainUiEffect.Completion(nextEffectId()));
                });
            } catch (Throwable error) {
                postError(error);
            }
        }));
    }

    private void submit(java.util.concurrent.Callable<DashboardData> action) {
        track(databaseExecutor.submit(() -> {
            try {
                DashboardData loaded = action.call();
                dispatch(() -> {
                    dashboard = loaded;
                    state.setValue(new MainUiState.Ready(loaded, surface(), filter()));
                });
            } catch (Throwable error) {
                postError(error);
            }
        }));
    }

    private void track(Future<?> future) {
        synchronized (running) {
            running.removeIf(Future::isDone);
            running.add(future);
        }
    }

    private void postError(Throwable error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        dispatch(() -> {
            state.setValue(new MainUiState.Failed(surface(), filter(), message));
            emit(new MainUiEffect.Error(nextEffectId(), message));
        });
    }

    private void setDisplay(Surface surface, WorkItemFilter filter) {
        state.setValue(dashboard == null
                ? new MainUiState.Loading(surface, filter)
                : new MainUiState.Ready(dashboard, surface, filter));
    }

    private Surface surface() {
        MainUiState value = state.getValue();
        return value == null ? Surface.TODAY : value.surface();
    }

    private WorkItemFilter filter() {
        MainUiState value = state.getValue();
        return value == null ? WorkItemFilter.OPEN : value.filter();
    }

    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(clock.now(), clock.zone());
    }

    private void emit(MainUiEffect effect) { effects.setValue(effect); }
    private long nextEffectId() { return ++effectSequence; }

    private void dispatch(Runnable action) {
        uiExecutor.execute(() -> { if (!cleared) action.run(); });
    }

    @Override protected void onCleared() {
        cleared = true;
        synchronized (running) {
            for (Future<?> future : running) future.cancel(true);
            running.clear();
        }
    }
}
