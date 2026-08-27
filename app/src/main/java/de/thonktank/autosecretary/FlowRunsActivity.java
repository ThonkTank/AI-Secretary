package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;
import de.thonktank.autosecretary.presentation.legacy.LegacyStateFlowBinder;

import java.util.List;

/** Operational overview whose ViewModel owns all durable flow-run work. */
public final class FlowRunsActivity extends ComponentActivity {
    private UiStyle style;
    private DayPalette palette;
    private LinearLayout list;
    private FlowRunsViewModel viewModel;
    private long handledErrorId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppContainer container = AutoSecretaryApplication.from(this).container();
        style = new UiStyle(this);
        palette = DayPalette.at(container.clock.time(), DayPalette.Mode.AUTO);
        viewModel = new ViewModelProvider(this,
                new FlowRunsViewModel.Factory(container)).get(FlowRunsViewModel.class);
        build();
        LegacyStateFlowBinder.observe(this, viewModel.state(), this::render);
    }

    @Override protected void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.dispatch(FlowRunsAction.refresh());
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(style.dp(20), style.dp(24), style.dp(20), style.dp(32));
        page.setBackgroundColor(palette.background);
        TextView back = button(getString(R.string.flow_back));
        back.setOnClickListener(view -> finish());
        page.addView(back, params(-2, style.dp(48), 0, 0));
        page.addView(style.serif(getString(R.string.flow_runs_title), 30, palette.ink,
                false, 300), params(-1, -2, 12, 0));
        page.addView(banner(), params(-1, -2, 12, 0));
        page.addView(style.sans(getString(R.string.flow_runs_description), 16,
                palette.ink2, false), params(-1, -2, 12, 0));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list, params(-1, -2, 10, 0));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private TextView banner() {
        TextView banner = style.sans(getString(R.string.flow_functional_banner), 14,
                palette.ink2, true);
        banner.setPadding(style.dp(14), style.dp(10), style.dp(14), style.dp(10));
        banner.setBackground(style.pill(palette.leaf3, 12));
        return banner;
    }

    private void render(FlowRunsScreenState state) {
        if (state == null) return;
        list.removeAllViews();
        if (state.loading && state.runs.isEmpty()) {
            list.addView(style.sans(getString(R.string.flow_loading), 17,
                    palette.hint, false), params(-1, -2, 18, 0));
        } else if (state.runs.isEmpty()) {
            list.addView(style.sans(getString(R.string.flow_runs_empty), 17,
                    palette.hint, false), params(-1, -2, 18, 0));
        } else {
            for (int index = 0; index < state.runs.size(); index++)
                list.addView(runCard(state.runs, index, state.changing),
                        params(-1, -2, index == 0 ? 0 : 12, 0));
        }
        if (state.errorMessage != null && state.errorId != handledErrorId) {
            handledErrorId = state.errorId;
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show();
            viewModel.dispatch(FlowRunsAction.acknowledgeError(state.errorId));
        }
    }

    private View runCard(List<FlowRunSummary> values, int index, boolean busy) {
        FlowRunSummary run = values.get(index);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(style.dp(18), style.dp(16), style.dp(18), style.dp(16));
        card.setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 14, 38, 14, 38));
        card.addView(style.serif(run.seedTitle, 23, palette.ink, false, 300));
        card.addView(style.sans(getString(R.string.flow_run_task_and_progress, run.taskTitle,
                        run.currentStepTitle, run.currentPosition + 1, run.totalSteps),
                16, palette.ink2, false), params(-1, -2, 4, 0));
        card.addView(style.sans(status(run), 15, palette.hint, true),
                params(-1, -2, 5, 0));
        if (!run.resources.isEmpty())
            card.addView(style.sans(resources(run), 14, palette.muted, false),
                    params(-1, -2, 7, 0));

        EditorFlowLayout actions = new EditorFlowLayout(this);
        if (run.state == StepFlowRunState.OFFERED)
            actions.addView(action(R.string.flow_run_defer, busy,
                    () -> viewModel.dispatch(FlowRunsAction.defer(run.id))));
        if (run.state == StepFlowRunState.WAITING_TIME) {
            actions.addView(action(R.string.flow_run_ready_now, busy,
                    () -> viewModel.dispatch(FlowRunsAction.readyAt(
                            run.id, System.currentTimeMillis()))));
            actions.addView(action(R.string.flow_run_adjust_time, busy,
                    () -> adjustTime(run)));
        }
        if (index > 0) actions.addView(action(R.string.flow_run_move_up, busy,
                () -> viewModel.dispatch(FlowRunsAction.moveBefore(
                        run.id, values.get(index - 1).id))));
        if (index + 1 < values.size()) {
            String before = index + 2 < values.size() ? values.get(index + 2).id : null;
            actions.addView(action(R.string.flow_run_move_down, busy,
                    () -> viewModel.dispatch(FlowRunsAction.moveBefore(run.id, before))));
        }
        actions.addView(action(R.string.flow_run_cancel, busy, () -> confirmCancel(run)));
        card.addView(actions, params(-1, -2, 12, 0));
        return card;
    }

    private String status(FlowRunSummary run) {
        if (run.state == StepFlowRunState.OFFERED) return getString(R.string.flow_status_ready);
        if (run.state == StepFlowRunState.WAITING_TIME)
            return getString(R.string.flow_status_waiting_time,
                    FlowRunningStripView.remaining(run.readyAtEpochMillis));
        return getString(R.string.flow_status_waiting_capacity);
    }

    private String resources(FlowRunSummary run) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (FlowRunSummary.Resource resource : run.resources) {
            String state = resource.state == FlowResourceState.ACTIVE
                    ? getString(R.string.flow_resource_active)
                    : resource.state == FlowResourceState.RESERVED
                    ? getString(R.string.flow_resource_reserved)
                    : resource.state == FlowResourceState.RELEASED
                    ? getString(R.string.flow_resource_released)
                    : getString(R.string.flow_resource_planned);
            values.add(getString(R.string.flow_resource_summary, resource.name,
                    resource.units, state));
        }
        return android.text.TextUtils.join(" · ", values);
    }

    private void adjustTime(FlowRunSummary run) {
        long now = System.currentTimeMillis();
        long proposed = Math.max(0L, (run.readyAtEpochMillis == null
                ? now : run.readyAtEpochMillis) - now);
        FlowDurationDialog.show(this, getString(R.string.flow_adjust_prompt_title), proposed,
                delay -> viewModel.dispatch(FlowRunsAction.readyAt(
                        run.id, System.currentTimeMillis() + delay)));
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

    private TextView action(int label, boolean busy, Runnable click) {
        TextView view = button(getString(label));
        view.setEnabled(!busy);
        view.setAlpha(busy ? .5f : 1f);
        view.setOnClickListener(ignored -> click.run());
        return view;
    }

    private TextView button(String text) {
        TextView view = style.sans(text, 15, palette.ink2, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(style.dp(14), 0, style.dp(14), 0);
        view.setMinHeight(style.dp(48));
        view.setBackground(style.pill(palette.leaf1, 24));
        AccessibilityRoles.button(view);
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height, int top, int bottom) {
        LinearLayout.LayoutParams result = new LinearLayout.LayoutParams(width, height);
        result.setMargins(0, style.dp(top), 0, style.dp(bottom));
        return result;
    }
}
