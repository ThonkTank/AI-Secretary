package de.thonktank.autosecretary.ui.today;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;
import de.thonktank.autosecretary.*;
import de.thonktank.autosecretary.presentation.today.*;
import de.thonktank.autosecretary.ui.leaf.*;

/** Compact native vessels; the scroll viewport belongs only to the title's trailing half. */
final class FlowChainStripView extends HorizontalScrollView {
    private final UiStyle style;
    private final java.util.function.LongSupplier currentTimeMillis;
    private final LinearLayout content;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private Runnable grainChanged = () -> { };

    FlowChainStripView(Context context) { this(context, System::currentTimeMillis); }

    FlowChainStripView(Context context, java.util.function.LongSupplier currentTimeMillis) {
        super(context); style = new UiStyle(context);
        this.currentTimeMillis = currentTimeMillis;
        setHorizontalScrollBarEnabled(false);
        content = new LinearLayout(context); content.setGravity(Gravity.CENTER_VERTICAL);
        addView(content, new LayoutParams(-2, -2));
    }

    void setGrainChanged(Runnable callback) { grainChanged = callback; }

    void bind(List<FlowChainUiModel> chains, DayPalette palette, TodayActionSink actions) {
        List<String> ids = new ArrayList<>();
        for (FlowChainUiModel chain : chains) ids.add(chain.runId);
        if (!ids.equals(new ArrayList<>(groups.keySet()))) {
            Map<String, Group> previous = new LinkedHashMap<>(groups);
            groups.clear(); content.removeAllViews();
            for (String id : ids) {
                Group group = previous.get(id);
                if (group == null) group = new Group(getContext());
                groups.put(id, group);
                int width = style.dp(80 * Math.max(1f, getResources().getConfiguration().fontScale));
                content.addView(group, new LinearLayout.LayoutParams(width, -2));
            }
        }
        for (FlowChainUiModel chain : chains) groups.get(chain.runId).bind(chain, palette, actions);
        setVisibility(chains.isEmpty() ? GONE : VISIBLE);
    }

    @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (groups != null) for (Group group : groups.values()) group.vessel.refreshClockVisibility();
        if (grainChanged != null) grainChanged.run();
    }

    List<GrainSpec.Anchor> anchors() {
        List<GrainSpec.Anchor> result = new ArrayList<>();
        for (Group group : groups.values()) result.add(GrainSpec.visibleAnchor(group.vessel, group.model.vessel.reward.comboStage));
        return result;
    }

    List<GrainOcclusion> occlusions() {
        List<GrainOcclusion> result = new ArrayList<>();
        for (Group group : groups.values()) result.add(GrainOcclusion.text(group.label));
        return result;
    }

    void registerRewardAnchors(RewardAnchorRegistry registry) {
        for (Group group : groups.values()) {
            registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.VESSEL, group.model.runId), group.vessel);
            for (String stepId : group.model.stepIds)
                registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.VESSEL, "flow-step:" + stepId), group.vessel);
        }
    }

    private final class Group extends LinearLayout {
        final XpVesselView vessel;
        final TextView label;
        FlowChainUiModel model;
        TodayActionSink events;
        Group(Context context) {
            super(context); setOrientation(VERTICAL); setGravity(Gravity.CENTER);
            setPadding(style.dp(6), style.dp(6), style.dp(6), style.dp(6));
            setMinimumHeight(style.dp(48)); setFocusable(true);
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            vessel = new XpVesselView(context, currentTimeMillis); vessel.setCompact(true);
            int diameter = style.dp(36 * Math.max(1f, getResources().getConfiguration().fontScale));
            addView(vessel, new LinearLayout.LayoutParams(diameter, diameter));
            label = style.sans("", 13, 0, false); label.setGravity(Gravity.CENTER);
            label.setMaxLines(2); label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
            textParams.topMargin = style.dp(10); addView(label, textParams);
            setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    if (model == null) return;
                    info.setContentDescription(description());
                    if (isClickable()) info.setClassName("android.widget.Button");
                    if (!model.editableWaits.isEmpty()) info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_LONG_CLICK, getContext().getString(R.string.flow_chain_adjust)));
                }
            });
        }

        void bind(FlowChainUiModel chain, DayPalette palette, TodayActionSink sink) {
            model = chain; events = sink;
            vessel.setPalette(palette); vessel.bindChain(chain);
            label.setText(chain.title); label.setTextColor(palette.hint);
            WoodGrainView.applyTextHalo(label, palette.leaf1);
            setOnClickListener(view -> {
                if (model.mode == FlowChainUiModel.Mode.READY) events.emit(TodayAction.collectFlow(model.runId));
                else if (model.mode == FlowChainUiModel.Mode.RESOURCE)
                    getContext().startActivity(new Intent(getContext(), FlowRunsActivity.class));
                else if (!model.editableWaits.isEmpty()) editWaits();
            });
            boolean actionable = chain.mode == FlowChainUiModel.Mode.READY
                    || chain.mode == FlowChainUiModel.Mode.RESOURCE || !chain.editableWaits.isEmpty();
            setClickable(actionable);
            setOnLongClickListener(chain.editableWaits.isEmpty() ? null : view -> { editWaits(); return true; });
            setLongClickable(!chain.editableWaits.isEmpty());
            setContentDescription(description());
        }

        private String description() {
            if (model.mode == FlowChainUiModel.Mode.READY)
                return getContext().getString(R.string.flow_chain_collect, model.title, model.vessel.reward.resultXp);
            if (model.mode == FlowChainUiModel.Mode.RESOURCE)
                return getContext().getString(R.string.flow_chain_resource, model.title);
            if (model.mode == FlowChainUiModel.Mode.COUNTDOWN) {
                FlowCountdown clock = new FlowCountdown(model.waitStartedAt, model.readyAt, currentTimeMillis.getAsLong());
                return getContext().getString(R.string.flow_chain_waiting, model.title, clock.value,
                        XpVesselView.countdownUnit(getContext(), clock));
            }
            return getContext().getString(R.string.flow_chain_progress, model.title, model.vessel.done,
                    model.vessel.total, model.vessel.reward.resultXp);
        }

        private void editWaits() {
            List<FlowWaitUiModel> waits = model.editableWaits;
            if (waits.size() == 1) edit(waits.get(0));
            else if (!waits.isEmpty()) {
                String[] titles = new String[waits.size()];
                for (int i = 0; i < waits.size(); i++) titles[i] = waits.get(i).title;
                new AlertDialog.Builder(getContext()).setTitle(R.string.flow_chain_adjust)
                        .setItems(titles, (dialog, index) -> edit(waits.get(index))).show();
            }
        }

        private void edit(FlowWaitUiModel wait) {
            FlowDurationDialog.show(getContext(), getContext().getString(R.string.flow_adjust_prompt_title),
                    wait.readyAtEpochMillis == null ? 0 : Math.max(0, wait.readyAtEpochMillis - currentTimeMillis.getAsLong()),
                    delay -> events.emit(TodayAction.adjustFlowWait(wait.runId, wait.waitId, currentTimeMillis.getAsLong() + delay)));
        }
    }
}
