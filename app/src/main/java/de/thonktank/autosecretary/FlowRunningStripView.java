package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.domain.model.FlowResourceState;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;
import de.thonktank.autosecretary.domain.model.StepFlowRunState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compact, non-blocking readout for work that is ready, waiting or capacity-blocked. */
@SuppressLint("ViewConstructor")
public final class FlowRunningStripView extends LinearLayout {
    private final UiStyle style;
    private final TextView heading;
    private final LinearLayout rows;
    private final TextView manage;
    private final Runnable openOverview;

    public FlowRunningStripView(Context context, Runnable openOverview) {
        super(context);
        this.openOverview = openOverview;
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        setPadding(style.dp(18), style.dp(15), style.dp(18), style.dp(14));
        heading = style.serif("", 19, 0, true, 300);
        addView(heading, new LayoutParams(-1, -2));
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        addView(rows, new LayoutParams(-1, -2));
        manage = style.sans(context.getString(R.string.flow_runs_manage), 15, 0, true);
        manage.setGravity(Gravity.CENTER_VERTICAL);
        manage.setMinHeight(style.dp(48));
        manage.setOnClickListener(view -> openOverview.run());
        AccessibilityRoles.button(manage);
        addView(manage, new LayoutParams(-1, style.dp(48)));
        setOnClickListener(view -> openOverview.run());
    }

    public void bind(List<FlowRunSummary> values, DayPalette palette) {
        setVisibility(values.isEmpty() ? View.GONE : View.VISIBLE);
        if (values.isEmpty()) return;
        setBackground(style.leaf(palette.leaf3, style.edge(palette, 3), 18, 42, 18, 42));
        heading.setText(getResources().getQuantityString(R.plurals.flow_runs_heading,
                values.size(), values.size()));
        heading.setTextColor(palette.ink);
        manage.setTextColor(palette.accent);
        rows.removeAllViews();
        int shown = Math.min(3, values.size());
        for (int index = 0; index < shown; index++) {
            FlowRunSummary run = values.get(index);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(VERTICAL);
            row.setPadding(0, style.dp(7), 0, style.dp(7));
            TextView title = style.sans(run.seedTitle, 16, palette.ink2, true);
            title.setSingleLine(true);
            row.addView(title, new LayoutParams(-1, -2));
            TextView status = style.sans(status(run), 14, palette.hint, false);
            status.setSingleLine(true);
            row.addView(status, new LayoutParams(-1, -2));
            rows.addView(row, new LayoutParams(-1, -2));
        }
        if (values.size() > shown) {
            TextView more = style.sans(getResources().getQuantityString(
                    R.plurals.flow_runs_more, values.size() - shown, values.size() - shown),
                    14, palette.muted, false);
            rows.addView(more, new LayoutParams(-1, -2));
        }
    }

    private String status(FlowRunSummary run) {
        String step = getContext().getString(R.string.flow_run_step_progress,
                run.currentStepTitle, run.currentPosition + 1, run.totalSteps);
        if (run.state == StepFlowRunState.OFFERED)
            return getContext().getString(R.string.flow_run_ready, step);
        if (run.state == StepFlowRunState.WAITING_TIME)
            return getContext().getString(R.string.flow_run_waiting_time, step,
                    remaining(run.readyAtEpochMillis));
        return getContext().getString(R.string.flow_run_waiting_resource, step,
                plannedResources(run));
    }

    private String plannedResources(FlowRunSummary run) {
        Set<String> names = new HashSet<>();
        for (FlowRunSummary.Resource resource : run.resources)
            if (resource.state == FlowResourceState.PLANNED
                    && resource.acquirePosition == run.currentPosition) names.add(resource.name);
        if (names.isEmpty()) return getContext().getString(R.string.flow_resource_capacity);
        return android.text.TextUtils.join(", ", names);
    }

    static String remaining(Long readyAtEpochMillis) {
        if (readyAtEpochMillis == null) return "";
        long millis = Math.max(0L, readyAtEpochMillis - System.currentTimeMillis());
        long minutes = (millis + 59_999L) / 60_000L;
        if (minutes < 60L) return minutes + " min";
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        if (hours < 24L) return rest == 0L ? hours + " h" : hours + " h " + rest + " min";
        long days = hours / 24L;
        long restHours = hours % 24L;
        return restHours == 0L ? days + " d" : days + " d " + restHours + " h";
    }
}
