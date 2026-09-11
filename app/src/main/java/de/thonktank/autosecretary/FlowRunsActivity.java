package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeCallbacks;
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeHostView;
import de.thonktank.autosecretary.presentation.legacy.LifecycleFlowBinder;
import de.thonktank.autosecretary.presentation.legacy.LegacyStateFlowBinder;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationCause;

/** Operational overview whose ViewModel owns all durable flow-run work. */
public final class FlowRunsActivity extends ComponentActivity {
    private DayPalette palette;
    private FlowRunsComposeHostView content;
    private FlowRunsViewModel viewModel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppContainer container = AutoSecretaryApplication.from(this).container();
        palette = DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO);
        viewModel = new ViewModelProvider(this,
                new FlowRunsViewModel.Factory(container)).get(FlowRunsViewModel.class);
        build();
        LegacyStateFlowBinder.observe(this, viewModel.state(), this::render);
        LifecycleFlowBinder.observe(this, container.presentationInvalidations.getFlowRunChanges(),
                invalidation -> {
                    if (invalidation.getCause() == PresentationInvalidationCause.CLOCK
                            && invalidation.getClock() != null) {
                        viewModel.dispatch(FlowRunsAction.presentAt(
                                invalidation.getClock().getEpochMillis()));
                    }
                });
    }

    @Override protected void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.dispatch(FlowRunsAction.refresh());
    }

    private void build() {
        content = new FlowRunsComposeHostView(this);
        setContentView(content);
    }

    private void render(FlowRunsScreenState state) {
        if (state == null) return;
        content.bind(state, palette, callbacks);
    }

    private final FlowRunsComposeCallbacks callbacks = new FlowRunsComposeCallbacks() {
        @Override public void onAdjustWait(String runId, String waitId, long readyAtEpochMillis) {
            FlowDurationDialog.show(FlowRunsActivity.this, getString(R.string.flow_adjust_prompt_title),
                    Math.max(0, readyAtEpochMillis - System.currentTimeMillis()),
                    delay -> viewModel.dispatch(FlowRunsAction.readyAt(runId, waitId, System.currentTimeMillis() + delay)));
        }
        @Override public void onBack() { finish(); }
        @Override public void onDefer(FlowRunSummary run) {
            viewModel.dispatch(FlowRunsAction.defer(run.id));
        }
        @Override public void onPostpone(FlowRunSummary run) { postpone(run); }
        @Override public void onReadyNow(FlowRunSummary run) {
            viewModel.dispatch(FlowRunsAction.readyAt(run.id, System.currentTimeMillis()));
        }
        @Override public void onAdjustTime(FlowRunSummary run) { adjustTime(run); }
        @Override public void onMoveBefore(String runId, String beforeRunId) {
            viewModel.dispatch(FlowRunsAction.moveBefore(runId, beforeRunId));
        }
        @Override public void onCancel(FlowRunSummary run) { confirmCancel(run); }
        @Override public void onDismissError(long errorId) {
            viewModel.dispatch(FlowRunsAction.acknowledgeError(errorId));
        }
    };

    private void adjustTime(FlowRunSummary run) {
        long now = System.currentTimeMillis();
        long proposed = Math.max(0L, (run.readyAtEpochMillis == null
                ? now : run.readyAtEpochMillis) - now);
        FlowDurationDialog.show(this, getString(R.string.flow_adjust_prompt_title), proposed,
                delay -> viewModel.dispatch(FlowRunsAction.readyAt(
                        run.id, System.currentTimeMillis() + delay)));
    }

    private void postpone(FlowRunSummary run) {
        FlowDurationDialog.show(this, getString(R.string.flow_postpone_prompt_title),
                run.arrivalDelayMillis == null ? 0L : run.arrivalDelayMillis,
                delay -> viewModel.dispatch(FlowRunsAction.postpone(run.id, delay)));
    }

    private void confirmCancel(FlowRunSummary run) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.flow_cancel_title, run.seedTitle))
                .setMessage(R.string.flow_cancel_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.flow_run_cancel,
                        (dialog, which) -> viewModel.dispatch(FlowRunsAction.cancel(run.id)))
                .show();
    }

    @Override protected void onDestroy() {
        if (content != null) content.dispose();
        super.onDestroy();
    }
}
