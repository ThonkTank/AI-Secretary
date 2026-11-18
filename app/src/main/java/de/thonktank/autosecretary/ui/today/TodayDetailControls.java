package de.thonktank.autosecretary.ui.today;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.text.InputFilter;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.thonktank.autosecretary.AccessibilityRoles;
import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayItemTarget;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.ui.leaf.LeafShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed navigation and step actions for the selected Today task. */
public final class TodayDetailControls {
    private final Context context;
    private final UiStyle style;
    private final TodayActionSink actions;
    private final LinearLayout navigation;
    private final LinearLayout tools;
    private final TextView back;
    private final TextView progress;
    private final TextView picker;
    private final TextView sort;
    private final TextView add;
    private final TextView menu;
    private final LinearLayout undoStrip;
    private final TextView undoMessage;
    private final TextView undo;
    private AlertDialog openDialog;

    public TodayDetailControls(Context context, TodayActionSink actions) {
        this.context = context;
        this.actions = actions;
        style = new UiStyle(context);
        navigation = bar();
        navigation.setOrientation(LinearLayout.VERTICAL);
        LinearLayout navButtons = new LinearLayout(context);
        navButtons.setGravity(Gravity.CENTER_VERTICAL);
        back = button(R.string.today_back, false);
        back.setContentDescription(context.getString(R.string.today_title));
        back.setId(R.id.today_back_to_overview);
        progress = style.sans("", 14, 0, false);
        progress.setGravity(Gravity.CENTER);
        picker = button(R.string.today_switch_task, true);
        picker.setId(R.id.today_choose_task);
        navButtons.addView(back);
        navButtons.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1));
        navButtons.addView(picker);
        navigation.addView(navButtons, new LinearLayout.LayoutParams(-1, -2));
        navigation.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        back.setOnClickListener(view -> actions.emit(TodayAction.showOverview()));
        tools = bar();
        sort = button(R.string.today_sort_steps, true);
        sort.setId(R.id.today_sort_steps);
        add = button(R.string.today_step_add_short, true);
        add.setId(R.id.today_add_step);
        add.setContentDescription(context.getString(R.string.step_add));
        menu = style.sans("⋮", 21, 0, true);
        menu.setGravity(Gravity.CENTER);
        menu.setMinHeight(style.dp(48));
        menu.setMinWidth(style.dp(48));
        menu.setContentDescription(context.getString(R.string.content_task_menu));
        AccessibilityRoles.button(menu);
        tools.addView(sort);
        tools.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1));
        tools.addView(add);
        tools.addView(menu);
        undoStrip = bar();
        undoMessage = style.sans("", 15, 0, false);
        undoMessage.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        undo = button(R.string.action_undo, true);
        undoStrip.addView(undoMessage, new LinearLayout.LayoutParams(0, -2, 1));
        undoStrip.addView(undo);
        hide();
    }

    public View navigation() { return navigation; }
    public View tools() { return tools; }
    public View undoStrip() { return undoStrip; }

    public void hide() {
        navigation.setVisibility(View.GONE);
        tools.setVisibility(View.GONE);
        undoStrip.setVisibility(View.GONE);
        if (openDialog != null) openDialog.dismiss();
    }

    public void bind(TodayUiModel dashboard, DayPalette palette) {
        FocusTaskUiModel selected = dashboard.focus;
        if (selected == null) {
            hide();
            return;
        }
        navigation.setVisibility(View.VISIBLE);
        tools.setVisibility(View.VISIBLE);
        undoStrip.setVisibility(View.GONE);
        tint(navigation, palette);
        tint(tools, palette);
        back.setTextColor(palette.ink2);
        picker.setTextColor(palette.ink2);
        int total = selected.steps.size();
        progress.setText(total == 0 ? "" : context.getString(
                R.string.today_progress, total - selected.remainingSteps, total));
        progress.setTextColor(palette.hint);
        progress.setVisibility(total == 0 ? View.GONE : View.VISIBLE);
        progress.setPadding(0, 0, 0, style.dp(5));
        picker.setEnabled(dashboard.openTasks.size() > 1);
        picker.setOnClickListener(view -> showPicker(dashboard));
        boolean occurrence = selected.actionTarget.item.kind == TodayItemTarget.Kind.OCCURRENCE;
        sort.setVisibility(occurrence && selected.remainingSteps > 1 ? View.VISIBLE : View.GONE);
        sort.setTextColor(palette.ink2);
        sort.setOnClickListener(view -> showSort(selected, palette));
        add.setVisibility(occurrence ? View.VISIBLE : View.GONE);
        add.setTextColor(palette.accentText);
        add.setBackground(style.pill(palette.accent, 24));
        add.setOnClickListener(view -> showAddStep(selected.itemId()));
        menu.setTextColor(palette.ink2);
        menu.setOnClickListener(view -> actions.emit(TodayAction.openTaskMenu(selected.actionTarget)));
    }

    public void bindUndo(TodayUiModel dashboard,
                         de.thonktank.autosecretary.presentation.today.TimelineTaskUiModel pending,
                         DayPalette palette, Runnable clear) {
        if (pending == null || dashboard.focus != null || dashboard.hiddenTasks.stream().noneMatch(
                task -> task.actionTarget.item.key().equals(pending.actionTarget.item.key()))) {
            undoStrip.setVisibility(View.GONE);
            return;
        }
        undoStrip.setVisibility(View.VISIBLE);
        tint(undoStrip, palette);
        undoMessage.setTextColor(palette.ink2);
        undoMessage.setText(context.getString(R.string.today_hidden_feedback, pending.title));
        undo.setTextColor(palette.accent);
        undo.setOnClickListener(view -> {
            clear.run();
            actions.emit(TodayAction.restoreTask(pending.actionTarget));
        });
    }

    private LinearLayout bar() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(14), 0, style.dp(9), 0);
        return row;
    }

    private void tint(LinearLayout row, DayPalette palette) {
        row.setBackground(new LeafShape(6, 24, 6, 24)
                .drawable(context, palette.leaf2, palette.leaf2Edge, 1));
    }

    private TextView button(int label, boolean bold) {
        TextView view = style.sans(context.getString(label), 16, 0, bold);
        view.setMinHeight(style.dp(48));
        view.setMinWidth(style.dp(48));
        view.setPadding(style.dp(8), 0, style.dp(8), 0);
        view.setGravity(Gravity.CENTER);
        AccessibilityRoles.button(view);
        return view;
    }

    private void showPicker(TodayUiModel dashboard) {
        String[] labels = new String[dashboard.openTasks.size()];
        int selected = -1;
        for (int i = 0; i < labels.length; i++) {
            TodayUiModel.OpenTask task = dashboard.openTasks.get(i);
            labels[i] = task.row.title;
            if (task.row.actionTarget.item.key().equals(dashboard.focus.actionTarget.item.key()))
                selected = i;
        }
        show(new AlertDialog.Builder(context).setTitle(R.string.today_switch_task)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    actions.emit(TodayAction.selectTask(dashboard.openTasks.get(which).row.actionTarget));
                })
                .setNegativeButton(R.string.today_cancel, null).create());
    }

    private void showAddStep(String occurrenceId) {
        LinearLayout fieldBox = new LinearLayout(context);
        fieldBox.setPadding(style.dp(24), style.dp(4), style.dp(24), 0);
        EditText field = new EditText(context);
        field.setId(R.id.today_step_name_field);
        field.setSingleLine(true);
        field.setHint(R.string.today_step_name);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        fieldBox.addView(field, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.step_add)
                .setView(fieldBox).setNegativeButton(R.string.today_cancel, null)
                .setPositiveButton(R.string.today_step_submit, null).create();
        field.requestFocus();
        show(dialog);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Runnable submit = () -> {
            String title = field.getText().toString().trim();
            if (title.isEmpty()) {
                field.setError(context.getString(R.string.today_step_name));
            } else {
                dialog.dismiss();
                actions.emit(TodayAction.addTodayStep(occurrenceId, title));
            }
        };
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> submit.run());
        field.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit.run();
            return true;
        });
    }

    private void showSort(FocusTaskUiModel selected, DayPalette palette) {
        List<FocusStepUiModel> draft = new ArrayList<>();
        for (FocusStepUiModel step : selected.steps) if (!step.isDone()) draft.add(step);
        ScrollView scroll = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(style.dp(12), 0, style.dp(12), 0);
        scroll.addView(list);
        EdgeAutoScroller autoScroll = new EdgeAutoScroller(new EdgeAutoScroller.AndroidScrollHost(scroll),
                android.os.SystemClock::uptimeMillis, style.dp(48), style.dp(300));
        populateSortRows(list, draft, palette);
        scroll.setOnDragListener((view, event) -> {
            if (!(event.getLocalState() instanceof FocusStepUiModel)) return false;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_LOCATION:
                    autoScroll.update(event.getY(), scroll.getHeight());
                    break;
                case DragEvent.ACTION_DROP:
                    int from = draft.indexOf(event.getLocalState());
                    int to = 0;
                    float y = event.getY() + scroll.getScrollY();
                    while (to + 1 < list.getChildCount()) {
                        View row = list.getChildAt(to);
                        if (y < row.getTop() + row.getHeight() / 2f) break;
                        to++;
                    }
                    if (from >= 0 && from != to) {
                        draft.add(to, draft.remove(from));
                        populateSortRows(list, draft, palette);
                    }
                    autoScroll.stop();
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                case DragEvent.ACTION_DRAG_EXITED:
                    autoScroll.stop();
                    break;
                default:
                    break;
            }
            return true;
        });
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.today_sort_step_title)
                .setView(scroll).setNegativeButton(R.string.today_cancel, null)
                .setPositiveButton(R.string.today_finish_sort, (closed, which) -> {
                    List<String> order = new ArrayList<>();
                    for (FocusStepUiModel step : draft) order.add(step.id);
                    actions.emit(TodayAction.sortTodaySteps(selected.itemId(), order));
                }).create();
        show(dialog, autoScroll::stop);
    }

    private void populateSortRows(LinearLayout list, List<FocusStepUiModel> draft, DayPalette palette) {
        list.removeAllViews();
        for (int i = 0; i < draft.size(); i++) {
            int index = i;
            FocusStepUiModel step = draft.get(i);
            LinearLayout row = bar();
            row.setPadding(0, 0, 0, 0);
            tint(row, palette);
            TextView grip = sortControl("⠿", step.title, palette.ink2);
            grip.setFocusable(false);
            grip.setContentDescription(context.getString(R.string.today_drag_step, step.title));
            grip.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() != android.view.MotionEvent.ACTION_DOWN) return false;
                return grip.startDragAndDrop(ClipData.newPlainText("step", step.id),
                        new View.DragShadowBuilder(row), step, 0);
            });
            row.addView(grip);
            TextView label = style.sans(step.title, 17, palette.ink, false);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(sortArrow("↑", R.string.today_sort_step_up, list, draft, palette, index, -1));
            row.addView(sortArrow("↓", R.string.today_sort_step_down, list, draft, palette, index, 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = style.dp(4);
            list.addView(row, params);
        }
    }

    private TextView sortArrow(String symbol, int description, LinearLayout list,
                               List<FocusStepUiModel> draft, DayPalette palette, int index, int delta) {
        String title = draft.get(index).title;
        TextView arrow = sortControl(symbol, context.getString(description, title), palette.ink2);
        int target = index + delta;
        boolean enabled = target >= 0 && target < draft.size();
        arrow.setEnabled(enabled);
        arrow.setAlpha(enabled ? 1f : .38f);
        arrow.setOnClickListener(view -> {
            Collections.swap(draft, index, target);
            populateSortRows(list, draft, palette);
            LinearLayout row = (LinearLayout) list.getChildAt(target);
            int next = target + delta;
            int directionButton = delta < 0 ? 2 : 3;
            row.getChildAt(next >= 0 && next < draft.size()
                    ? directionButton : 5 - directionButton).requestFocus();
        });
        return arrow;
    }

    private TextView sortControl(String glyph, String description, int color) {
        TextView value = style.sans(glyph, 21, color, false);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(style.dp(48));
        value.setMinHeight(style.dp(48));
        value.setContentDescription(description);
        AccessibilityRoles.button(value);
        return value;
    }

    private void show(AlertDialog dialog) {
        show(dialog, () -> { });
    }

    private void show(AlertDialog dialog, Runnable cleanup) {
        if (openDialog != null) openDialog.dismiss();
        openDialog = dialog;
        dialog.setOnDismissListener(closed -> {
            if (openDialog == dialog) openDialog = null;
            cleanup.run();
        });
        dialog.show();
    }
}
