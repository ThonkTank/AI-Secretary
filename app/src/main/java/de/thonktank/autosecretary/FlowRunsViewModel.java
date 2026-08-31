package de.thonktank.autosecretary;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.usecase.FlowUseCases;
import de.thonktank.autosecretary.infrastructure.AppLogger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Sole state and side-effect owner for the active-flow overview. */
public final class FlowRunsViewModel extends ViewModel {
    private final FlowUseCases flows;
    private final FlowWakeScheduler wakeScheduler;
    private final AppLogger logger;
    private final ExecutorService worker;
    private final MutableStateFlow<FlowRunsScreenState> state;
    private final Object actionLock = new Object();
    private FlowRunsScreenState current = FlowRunsScreenState.idle();
    private long errorSequence;
    private boolean cleared;

    FlowRunsViewModel(FlowUseCases flows, FlowWakeScheduler wakeScheduler,
                      AppLogger logger, ExecutorService worker) {
        this.flows = flows;
        this.wakeScheduler = wakeScheduler;
        this.logger = logger;
        this.worker = worker;
        state = StateFlowKt.MutableStateFlow(current);
        load(true);
    }

    public StateFlow<FlowRunsScreenState> state() { return state; }

    public void dispatch(FlowRunsAction action) {
        if (action == null) throw new IllegalArgumentException("Flow-runs action is required");
        synchronized (actionLock) {
            switch (action.kind) {
                case REFRESH:
                    load(true);
                    return;
                case DEFER:
                    change(() -> flows.deferFlowRun.execute(action.runId));
                    return;
                case READY_AT:
                    change(() -> flows.adjustFlowRunReadyAt.execute(
                            action.runId, action.epochMillis));
                    return;
                case MOVE_BEFORE:
                    change(() -> flows.reorderFlowRun.execute(
                            action.runId, action.beforeRunId));
                    return;
                case CANCEL:
                    change(() -> flows.cancelFlowRun.execute(action.runId));
                    return;
                case ACKNOWLEDGE_ERROR:
                    publish(current.acknowledgeError(action.errorId));
            }
        }
    }

    private void load(boolean showLoading) {
        if (cleared || current.loading || current.changing) return;
        if (showLoading) publish(current.withLoading());
        worker.execute(() -> {
            try {
                List<FlowRunSummary> values = flows.loadFlowRuns.execute();
                if (!cleared) publish(current.withRuns(values));
            } catch (RuntimeException error) {
                failed("Could not load flow runs", error);
            }
        });
    }

    private void change(Change operation) {
        if (cleared || current.loading || current.changing) return;
        publish(current.withChanging());
        worker.execute(() -> {
            try {
                operation.run();
                wakeScheduler.reschedule();
                List<FlowRunSummary> values = flows.loadFlowRuns.execute();
                if (!cleared) publish(current.withRuns(values));
            } catch (RuntimeException error) {
                failed("Could not change flow run", error);
            }
        });
    }

    private void failed(String operation, RuntimeException error) {
        logger.error("FlowRuns", operation, error);
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = operation;
        if (!cleared) publish(current.withError(++errorSequence, message));
    }

    private void publish(FlowRunsScreenState value) {
        current = value;
        state.setValue(value);
    }

    @Override protected void onCleared() {
        cleared = true;
        worker.shutdown();
    }

    private interface Change { boolean run(); }

    public static final class Factory implements ViewModelProvider.Factory {
        private final AppContainer container;

        public Factory(AppContainer container) { this.container = container; }

        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!modelClass.isAssignableFrom(FlowRunsViewModel.class))
                throw new IllegalArgumentException("Unsupported ViewModel " + modelClass);
            return (T) new FlowRunsViewModel(container.flows, container.flowWakeScheduler,
                    container.logger, Executors.newSingleThreadExecutor());
        }
    }
}
