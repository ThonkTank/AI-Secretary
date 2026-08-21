package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Virtualized management surface. Completion actions deliberately do not live here. */
@SuppressLint("ViewConstructor")
public final class AllTasksView extends LinearLayout {
    private static final long SEARCH_DEBOUNCE_MS = 180L;

    public interface Listener {
        default void onQuery(String query) { }
        default void onStatus(AllTasksUiState.Status status) { }
        default void onSlots(Set<TaskSlot> slots) { }
        default void onRecurrences(Set<Recurrence> recurrences) { }
        default void onWeekday(int weekday) { }
        default void onMode(AllTasksUiState.Mode mode) { }
        default void onToggleTask(String taskId) { }
        default void onEditTask(String taskId) { }
        default void onEditStep(String taskId, String stepId) { }
        default void onAddStep(String taskId) { }
        default void onDeleteTask(String taskId, String title) { }
        default void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId) { }
        default void onMoveStep(String stepId, String taskId, String beforeStepId) { }
        default void onSwapSteps(String stepId, String targetStepId) { }
    }

    private final UiStyle style;
    private final Listener listener;
    private final EditText search;
    private final LinearLayout controls;
    private final RecyclerView list;
    private final RowAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable publishSearch;
    private boolean bindingSearch;
    private AllTasksUiState state = AllTasksUiState.empty();
    private DayPalette palette;
    private String controlsKey = "";

    public AllTasksView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(style.dimen(R.dimen.page_start), style.dimen(R.dimen.content_top),
                style.dimen(R.dimen.page_end), 0);

        TextView title = style.serif(context.getString(R.string.all_title), 30, 0, true, 300);
        title.setId(View.generateViewId());
        addView(title, params(-1, -2, 0, 0, 0, 14));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.all_search_hint);
        search.setTextSize(17);
        search.setTypeface(style.sans);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        search.setPadding(style.dp(18), 0, style.dp(18), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count,
                                                    int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (bindingSearch) return;
                handler.removeCallbacks(publishSearch);
                handler.postDelayed(publishSearch, SEARCH_DEBOUNCE_MS);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        publishSearch = () -> listener.onQuery(search.getText().toString());
        addView(search, params(-1, style.dp(50), 0, 0, 0, 14));
        controls = new LinearLayout(context);
        controls.setOrientation(VERTICAL);
        addView(controls, new LayoutParams(-1, -2));
        list = new RecyclerView(context);
        list.setId(R.id.all_tasks_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, style.dp(8), 0, style.dp(26));
        list.setItemAnimator(null);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        new ItemTouchHelper(new DragCallback()).attachToRecyclerView(list);
        addView(list, new LayoutParams(-1, 0, 1));
    }

    /** Transitional source-compatible constructor; the outer scroll is intentionally unused. */
    public AllTasksView(Context context, ScrollView ignoredOuterScroll, Listener listener) {
        this(context, listener);
    }

    public void bind(AllTasksUiState state, DayPalette palette) {
        this.state = state;
        boolean paletteChanged = this.palette == null || this.palette.ink != palette.ink
                || this.palette.leaf1 != palette.leaf1 || this.palette.accent != palette.accent;
        this.palette = palette;
        bindingSearch = true;
        if (!search.getText().toString().equals(state.query)) {
            search.setText(state.query);
            search.setSelection(search.length());
        }
        bindingSearch = false;
        search.setTextColor(palette.ink);
        search.setHintTextColor(palette.hint);
        GradientDrawable searchBackground = style.pill(UiStyle.alpha(palette.leaf1, .86f), 25);
        searchBackground.setStroke(style.dp(1), palette.leaf1Edge);
        search.setBackground(searchBackground);
        ((TextView) getChildAt(0)).setTextColor(palette.ink2);
        String nextControlsKey = state.status + "|" + state.slots + "|" + state.recurrences
                + "|" + state.weekday + "|" + state.mode + "|" + palette.accent + '|'
                + palette.ink2;
        if (!nextControlsKey.equals(controlsKey)) {
            controlsKey = nextControlsKey;
            renderControls();
        }
        adapter.submitList(AllTasksRow.project(state));
        if (paletteChanged && adapter.getItemCount() > 0)
            adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "palette");
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(publishSearch);
        super.onDetachedFromWindow();
    }

    private void renderControls() {
        controls.removeAllViews();
        LinearLayout modes = row();
        modes.addView(chip(R.string.all_tasks_mode, state.mode == AllTasksUiState.Mode.LIST,
                () -> listener.onMode(AllTasksUiState.Mode.LIST)));
        modes.addView(chip(R.string.all_sort_mode, state.mode == AllTasksUiState.Mode.SORT,
                () -> listener.onMode(AllTasksUiState.Mode.SORT)));
        controls.addView(horizontal(modes));

        LinearLayout statuses = row();
        statuses.addView(chip(R.string.all_status_active,
                state.status == AllTasksUiState.Status.ACTIVE,
                () -> listener.onStatus(AllTasksUiState.Status.ACTIVE)));
        statuses.addView(chip(R.string.all_status_archived,
                state.status == AllTasksUiState.Status.ARCHIVED,
                () -> listener.onStatus(AllTasksUiState.Status.ARCHIVED)));
        statuses.addView(chip(R.string.all_status_all,
                state.status == AllTasksUiState.Status.ALL,
                () -> listener.onStatus(AllTasksUiState.Status.ALL)));
        controls.addView(labeled(R.string.all_filter_status, statuses));

        LinearLayout times = row();
        int[] timeLabels = {R.string.slot_morning, R.string.slot_midday,
                R.string.slot_evening, R.string.slot_later};
        TaskSlot[] slotValues = TaskSlot.values();
        for (int index = 0; index < slotValues.length; index++) {
            TaskSlot slot = slotValues[index];
            times.addView(chip(timeLabels[index], state.slots.contains(slot), () -> {
                EnumSet<TaskSlot> values = state.slots.isEmpty()
                        ? EnumSet.noneOf(TaskSlot.class) : EnumSet.copyOf(state.slots);
                if (!values.add(slot)) values.remove(slot);
                listener.onSlots(values);
            }));
        }
        controls.addView(labeled(R.string.all_filter_time, times));

        LinearLayout rhythms = row();
        int[] rhythmLabels = {R.string.rhythm_once, R.string.rhythm_daily,
                R.string.rhythm_every_n, R.string.rhythm_weekdays};
        Recurrence[] rhythmValues = {Recurrence.ONCE, Recurrence.DAILY,
                Recurrence.INTERVAL, Recurrence.WEEKDAYS};
        for (int index = 0; index < rhythmValues.length; index++) {
            Recurrence recurrence = rhythmValues[index];
            rhythms.addView(chip(rhythmLabels[index], state.recurrences.contains(recurrence), () -> {
                EnumSet<Recurrence> values = state.recurrences.isEmpty()
                        ? EnumSet.noneOf(Recurrence.class) : EnumSet.copyOf(state.recurrences);
                if (!values.add(recurrence)) values.remove(recurrence);
                listener.onRecurrences(values);
            }));
        }
        controls.addView(labeled(R.string.all_filter_rhythm, rhythms));

        if (state.mode == AllTasksUiState.Mode.SORT) {
            LinearLayout days = row();
            days.addView(chip(R.string.all_every_day, state.weekday == 0,
                    () -> listener.onWeekday(0)));
            int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                    R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
            for (int index = 0; index < labels.length; index++) {
                int day = index + 1;
                days.addView(chip(labels[index], state.weekday == day,
                        () -> listener.onWeekday(day)));
            }
            controls.addView(labeled(R.string.all_filter_day, days));
        }
    }

    private final class RowAdapter extends ListAdapter<AllTasksRow, RowHolder> {
        private String selectedStepId;

        RowAdapter() {
            super(new DiffUtil.ItemCallback<AllTasksRow>() {
                @Override public boolean areItemsTheSame(@NonNull AllTasksRow oldItem,
                                                         @NonNull AllTasksRow newItem) {
                    return oldItem.key.equals(newItem.key);
                }
                @Override public boolean areContentsTheSame(@NonNull AllTasksRow oldItem,
                                                            @NonNull AllTasksRow newItem) {
                    return oldItem.content.equals(newItem.content);
                }
            });
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) { return getItem(position).stableId; }
        @Override public int getItemViewType(int position) { return getItem(position).kind.ordinal(); }

        @NonNull @Override public RowHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                               int viewType) {
            FrameLayout root = new FrameLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RowHolder(root);
        }

        @Override public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            holder.bind(getItem(position));
        }

        void selectForSwap(String id) {
            selectedStepId = id;
            notifyItemRangeChanged(0, getItemCount(), "actions");
            list.announceForAccessibility(getContext().getString(R.string.a11y_step_selected));
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        private AllTasksRow row;

        RowHolder(@NonNull FrameLayout root) { super(root); }

        void bind(AllTasksRow value) {
            row = value;
            FrameLayout root = (FrameLayout) itemView;
            root.removeAllViews();
            root.setAccessibilityDelegate(null);
            root.setContentDescription(null);
            root.setFocusable(false);
            root.setClickable(false);
            View child;
            switch (value.kind) {
                case TASK_HEADER: child = taskHeader(value); break;
                case STEP: child = stepRow(value); break;
                case STEP_TARGET: child = stepTarget(value); break;
                case SLOT_HEADER: child = slotHeader(value.slot); break;
                case SCHEDULE: child = scheduleRow(value); break;
                case SCHEDULE_TARGET: child = scheduleTarget(value); break;
                default: child = empty(value.emptyReason);
            }
            root.addView(child, new FrameLayout.LayoutParams(-1, -2));
            installAccessibility(root, value);
        }
    }

    private View taskHeader(AllTasksRow row) {
        AllTasksUiState.TaskItem item = row.task;
        LinearLayout card = column();
        card.setPadding(style.dp(18), style.dp(14), style.dp(10), style.dp(12));
        card.setBackground(style.leaf(item.archived ? palette.leaf3 : palette.leaf2,
                style.edge(palette, item.archived ? 3 : 2), 42, 8, 42, 8));
        style.shadow(card, palette, 5, .55f);
        LinearLayout header = row();
        LinearLayout copy = column();
        copy.addView(style.serif(item.task.title, 22,
                item.archived ? palette.done : palette.ink, false, 350));
        copy.addView(style.sans(taskMeta(item), 14, palette.hint, false));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton expand = icon(item.expanded ? R.drawable.ic_expand_less
                : R.drawable.ic_expand_more, item.expanded ? R.string.a11y_collapse_task
                : R.string.a11y_expand_task);
        expand.setOnClickListener(view -> listener.onToggleTask(item.task.id.value));
        header.addView(expand, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        card.addView(header);
        LinearLayout actions = row();
        if (!item.archived)
            actions.addView(textAction(R.string.task_edit,
                    () -> listener.onEditTask(item.task.id.value)));
        actions.addView(textAction(R.string.task_delete,
                () -> listener.onDeleteTask(item.task.id.value, item.task.title)));
        card.addView(actions);
        card.setContentDescription(getContext().getString(R.string.a11y_task_row,
                item.task.title, taskMeta(item)));
        card.setLayoutParams(rowMargins(0, 0, 0, 12));
        return card;
    }

    private View stepRow(AllTasksRow row) {
        LinearLayout result = row();
        result.setPadding(style.dp(8), style.dp(3), style.dp(8), style.dp(3));
        if (!row.task.archived) result.addView(icon(R.drawable.ic_drag_handle,
                R.string.all_drag_step), new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        TextView title = style.sans(row.step.text, 17,
                row.task.archived ? palette.done : palette.ink, false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(style.dp(48));
        result.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        result.setBackground(style.pill(UiStyle.alpha(palette.leaf1, .72f), 18));
        if (!row.task.archived)
            result.setOnClickListener(view -> listener.onEditStep(row.taskId, row.step.id));
        result.setContentDescription(getContext().getString(row.task.archived
                ? R.string.a11y_archived_step_row : R.string.a11y_step_row, row.step.text));
        result.setLayoutParams(rowMargins(style.dp(8), 0, 0, style.dp(4)));
        return result;
    }

    private View stepTarget(AllTasksRow row) {
        TextView target = style.sans(getContext().getString(row.endTarget
                ? R.string.all_add_step : R.string.all_step_insert_target), 14,
                palette.muted, false);
        target.setGravity(Gravity.CENTER_VERTICAL);
        target.setMinHeight(style.dp(48));
        target.setPadding(style.dp(56), 0, style.dp(8), 0);
        if (row.endTarget)
            target.setOnClickListener(view -> listener.onAddStep(row.taskId));
        target.setContentDescription(getContext().getString(row.endTarget
                ? R.string.a11y_add_step_target : R.string.a11y_step_drop_target));
        return target;
    }

    private View slotHeader(TaskSlot slot) {
        TextView marker = style.serif(slotLabel(slot), 17, palette.muted, true, 300);
        marker.setPadding(style.dp(4), style.dp(12), 0, style.dp(6));
        ViewCompat.setAccessibilityHeading(marker, true);
        return marker;
    }

    private View scheduleRow(AllTasksRow row) {
        AllTasksUiState.ScheduleItem item = row.schedule;
        LinearLayout result = row();
        result.setPadding(style.dp(8), style.dp(4), style.dp(10), style.dp(4));
        result.setBackground(style.leaf(palette.leaf2, palette.leaf2Edge, 36, 8, 36, 8));
        result.addView(icon(R.drawable.ic_drag_handle, R.string.all_drag_task),
                new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        LinearLayout copy = column();
        copy.addView(style.serif(item.title, 20, palette.ink, false, 350));
        copy.addView(style.sans(recurrenceLabel(item.recurrence), 14, palette.hint, false));
        result.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        result.setContentDescription(getContext().getString(R.string.a11y_schedule_row,
                item.title, slotLabel(item.slot), recurrenceLabel(item.recurrence)));
        return result;
    }

    private View scheduleTarget(AllTasksRow row) {
        TextView target = style.sans(getContext().getString(R.string.all_schedule_insert_target),
                14, palette.muted, false);
        target.setGravity(Gravity.CENTER_VERTICAL);
        target.setMinHeight(style.dp(48));
        target.setPadding(style.dp(56), 0, style.dp(8), 0);
        target.setContentDescription(getContext().getString(R.string.a11y_schedule_drop_target,
                slotLabel(row.slot)));
        return target;
    }

    private View empty(AllTasksRow.EmptyReason reason) {
        LinearLayout empty = column();
        empty.setPadding(style.dp(22), style.dp(28), style.dp(22), style.dp(28));
        empty.setBackground(style.dashed(palette));
        int title;
        int subtitle;
        if (reason == AllTasksRow.EmptyReason.NO_TASKS) {
            title = R.string.all_no_tasks_title; subtitle = R.string.all_no_tasks_subtitle;
        } else if (reason == AllTasksRow.EmptyReason.SEARCH) {
            title = R.string.all_empty_search_title; subtitle = R.string.all_empty_search_subtitle;
        } else if (reason == AllTasksRow.EmptyReason.FILTERS) {
            title = R.string.all_empty_filter_title; subtitle = R.string.all_empty_filter_subtitle;
        } else {
            title = R.string.all_empty_status_title; subtitle = R.string.all_empty_status_subtitle;
        }
        empty.addView(style.serif(getContext().getString(title), 25, palette.ink, false, 250));
        empty.addView(style.sans(getContext().getString(subtitle), 16, palette.hint, false),
                params(-1, -2, 0, 12, 0, 0));
        return empty;
    }

    private void installAccessibility(View view, AllTasksRow row) {
        List<Action> actions = accessibilityActions(row);
        if (actions.isEmpty()) return;
        if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0) {
            View child = ((ViewGroup) view).getChildAt(0);
            if (child.getContentDescription() != null) {
                view.setContentDescription(child.getContentDescription());
                child.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        }
        view.setFocusable(true);
        view.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host,
                                                                    AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                for (Action action : actions)
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                            action.id, getContext().getString(action.label)));
            }

            @Override public boolean performAccessibilityAction(View host, int action,
                                                                Bundle arguments) {
                for (Action candidate : actions)
                    if (candidate.id == action) return dispatchAccessibility(row, action);
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
    }

    private List<Action> accessibilityActions(AllTasksRow row) {
        List<Action> result = new ArrayList<>();
        if (row.kind == AllTasksRow.Kind.STEP && !row.task.archived) {
            if (stepIndex(row) > 0) result.add(new Action(R.id.action_step_up, R.string.a11y_step_up));
            if (stepIndex(row) < row.task.steps.size() - 1)
                result.add(new Action(R.id.action_step_down, R.string.a11y_step_down));
            if (otherTask(row.taskId, -1) != null)
                result.add(new Action(R.id.action_step_previous_task,
                        R.string.a11y_step_previous_task));
            if (otherTask(row.taskId, 1) != null)
                result.add(new Action(R.id.action_step_next_task, R.string.a11y_step_next_task));
            result.add(new Action(R.id.action_step_select_swap, R.string.a11y_step_select_swap));
            if (adapter.selectedStepId != null && !adapter.selectedStepId.equals(row.step.id))
                result.add(new Action(R.id.action_step_swap_selected,
                        R.string.a11y_step_swap_selected));
        } else if (row.kind == AllTasksRow.Kind.SCHEDULE) {
            int index = scheduleIndex(row);
            int count = scheduleInSlot(row.slot).size();
            if (index > 0) result.add(new Action(R.id.action_schedule_up,
                    R.string.a11y_schedule_up));
            if (index < count - 1) result.add(new Action(R.id.action_schedule_down,
                    R.string.a11y_schedule_down));
            if (row.slot.rank > 0) result.add(new Action(R.id.action_schedule_previous_slot,
                    R.string.a11y_schedule_previous_slot));
            if (row.slot.rank < TaskSlot.values().length - 1)
                result.add(new Action(R.id.action_schedule_next_slot,
                        R.string.a11y_schedule_next_slot));
        }
        return result;
    }

    private boolean dispatchAccessibility(AllTasksRow row, int action) {
        if (row.kind == AllTasksRow.Kind.STEP && row.task.archived) return false;
        if (action == R.id.action_step_up) return moveStepBy(row, -1);
        if (action == R.id.action_step_down) return moveStepBy(row, 1);
        if (action == R.id.action_step_previous_task) return moveStepToTask(row, -1);
        if (action == R.id.action_step_next_task) return moveStepToTask(row, 1);
        if (action == R.id.action_step_select_swap) {
            adapter.selectForSwap(row.step.id); return true;
        }
        if (action == R.id.action_step_swap_selected && adapter.selectedStepId != null) {
            listener.onSwapSteps(adapter.selectedStepId, row.step.id);
            adapter.selectedStepId = null;
            return true;
        }
        if (action == R.id.action_schedule_up) return moveScheduleBy(row, -1);
        if (action == R.id.action_schedule_down) return moveScheduleBy(row, 1);
        if (action == R.id.action_schedule_previous_slot)
            return moveScheduleToSlot(row, TaskSlot.values()[row.slot.rank - 1]);
        if (action == R.id.action_schedule_next_slot)
            return moveScheduleToSlot(row, TaskSlot.values()[row.slot.rank + 1]);
        return false;
    }

    private boolean moveStepBy(AllTasksRow row, int delta) {
        int index = stepIndex(row);
        int target = index + delta;
        if (target < 0 || target >= row.task.steps.size()) return false;
        String before;
        if (delta < 0) before = row.task.steps.get(target).id;
        else before = index + 2 < row.task.steps.size()
                ? row.task.steps.get(index + 2).id : null;
        listener.onMoveStep(row.step.id, row.taskId, before);
        return true;
    }

    private boolean moveStepToTask(AllTasksRow row, int direction) {
        AllTasksUiState.TaskItem target = otherTask(row.taskId, direction);
        if (target == null) return false;
        listener.onMoveStep(row.step.id, target.task.id.value, null);
        return true;
    }

    private AllTasksUiState.TaskItem otherTask(String taskId, int direction) {
        int current = -1;
        for (int index = 0; index < state.tasks.size(); index++)
            if (state.tasks.get(index).task.id.value.equals(taskId)) current = index;
        for (int index = current + direction; index >= 0 && index < state.tasks.size();
             index += direction)
            if (!state.tasks.get(index).archived) return state.tasks.get(index);
        return null;
    }

    private int stepIndex(AllTasksRow row) {
        for (int index = 0; index < row.task.steps.size(); index++)
            if (row.task.steps.get(index).id.equals(row.step.id)) return index;
        return -1;
    }

    private boolean moveScheduleBy(AllTasksRow row, int delta) {
        List<AllTasksUiState.ScheduleItem> values = scheduleInSlot(row.slot);
        int index = scheduleIndex(row);
        int target = index + delta;
        if (target < 0 || target >= values.size()) return false;
        String before = delta < 0 ? values.get(target).id
                : index + 2 < values.size() ? values.get(index + 2).id : null;
        listener.onMoveSchedule(row.schedule.id, row.slot, before);
        return true;
    }

    private boolean moveScheduleToSlot(AllTasksRow row, TaskSlot target) {
        listener.onMoveSchedule(row.schedule.id, target, null);
        return true;
    }

    private int scheduleIndex(AllTasksRow row) {
        List<AllTasksUiState.ScheduleItem> values = scheduleInSlot(row.slot);
        for (int index = 0; index < values.size(); index++)
            if (values.get(index).id.equals(row.schedule.id)) return index;
        return -1;
    }

    private List<AllTasksUiState.ScheduleItem> scheduleInSlot(TaskSlot slot) {
        List<AllTasksUiState.ScheduleItem> result = new ArrayList<>();
        for (AllTasksUiState.ScheduleItem item : state.schedule)
            if (item.slot == slot) result.add(item);
        return result;
    }

    private final class DragCallback extends ItemTouchHelper.Callback {
        @Override public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                              @NonNull RecyclerView.ViewHolder viewHolder) {
            int position = viewHolder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return 0;
            AllTasksRow row = adapter.getCurrentList().get(position);
            boolean movable = row.kind == AllTasksRow.Kind.SCHEDULE
                    || row.kind == AllTasksRow.Kind.STEP && !row.task.archived;
            return makeMovementFlags(movable ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0, 0);
        }

        @Override public boolean isLongPressDragEnabled() { return true; }

        @Override public boolean onMove(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder,
                                        @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            return dispatchDrag(from, to);
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                       int direction) { }
    }

    private boolean dispatchDrag(int from, int to) {
        if (from < 0 || to < 0 || from >= adapter.getItemCount() || to >= adapter.getItemCount())
            return false;
        AllTasksRow source = adapter.getCurrentList().get(from);
        AllTasksRow target = adapter.getCurrentList().get(to);
        if (source.kind == AllTasksRow.Kind.STEP) {
            if (source.task.archived) return false;
            if (target.kind == AllTasksRow.Kind.STEP && !target.task.archived) {
                if (!source.step.id.equals(target.step.id))
                    listener.onSwapSteps(source.step.id, target.step.id);
                return true;
            }
            if (target.kind == AllTasksRow.Kind.STEP_TARGET) {
                listener.onMoveStep(source.step.id, target.taskId, target.beforeId);
                return true;
            }
            if (target.kind == AllTasksRow.Kind.TASK_HEADER && !target.task.archived) {
                listener.onMoveStep(source.step.id, target.taskId, null);
                return true;
            }
        } else if (source.kind == AllTasksRow.Kind.SCHEDULE) {
            if (target.kind == AllTasksRow.Kind.SCHEDULE_TARGET) {
                listener.onMoveSchedule(source.schedule.id, target.slot, target.beforeId);
                return true;
            }
            if (target.kind == AllTasksRow.Kind.SCHEDULE) {
                listener.onMoveSchedule(source.schedule.id, target.slot, target.schedule.id);
                return true;
            }
        }
        return false;
    }

    int rowCountForTest() { return adapter.getItemCount(); }
    long rowIdForTest(int position) { return adapter.getItemId(position); }
    boolean dragForTest(int from, int to) { return dispatchDrag(from, to); }
    boolean accessibilityActionForTest(int position, int action) {
        return dispatchAccessibility(adapter.getCurrentList().get(position), action);
    }
    int positionForTest(AllTasksRow.Kind kind, String id) {
        for (int index = 0; index < adapter.getItemCount(); index++) {
            AllTasksRow row = adapter.getCurrentList().get(index);
            if (row.kind != kind) continue;
            if (id == null || row.key.endsWith(id) || row.key.equals(id)) return index;
        }
        return -1;
    }
    RecyclerView recyclerForTest() { return list; }
    EditText searchForTest() { return search; }

    private ImageButton icon(int drawable, int description) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(drawable);
        button.setColorFilter(palette.dot);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(getContext().getString(description));
        button.setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12));
        return button;
    }

    private TextView chip(int label, boolean selected, Runnable action) {
        TextView chip = style.sans(getContext().getString(label), 14,
                selected ? palette.accentText : palette.ink2, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(style.dp(44));
        chip.setPadding(style.dp(15), 0, style.dp(15), 0);
        chip.setSelected(selected);
        GradientDrawable background = style.pill(selected ? palette.accent
                : Color.TRANSPARENT, 22);
        if (!selected) background.setStroke(style.dp(1), palette.dot);
        chip.setBackground(background);
        chip.setOnClickListener(view -> action.run());
        LayoutParams params = new LayoutParams(-2, style.dp(44));
        params.rightMargin = style.dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    private View labeled(int label, LinearLayout row) {
        LinearLayout wrapper = column();
        TextView heading = style.serif(getContext().getString(label), 14,
                palette.muted, true, 300);
        ViewCompat.setAccessibilityHeading(heading, true);
        wrapper.addView(heading);
        wrapper.addView(horizontal(row), params(-1, -2, 0, 5, 0, 0));
        wrapper.setLayoutParams(params(-1, -2, 0, 10, 0, 0));
        return wrapper;
    }

    private HorizontalScrollView horizontal(LinearLayout row) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        return column;
    }

    private TextView textAction(int label, Runnable action) {
        TextView view = style.sans(getContext().getString(label), 15, palette.ink2, false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(style.dp(48));
        view.setPadding(style.dp(14), 0, style.dp(14), 0);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private String taskMeta(AllTasksUiState.TaskItem item) {
        String timing = item.archived ? getContext().getString(R.string.all_archived)
                : item.task.nextDueOn == null ? getContext().getString(R.string.all_no_due)
                : getContext().getString(R.string.all_next_due,
                item.task.nextDueOn.format(DateTimeFormatter.ofPattern("dd.MM.")));
        String steps = item.steps.size() == 1 ? getContext().getString(R.string.all_step_count)
                : getContext().getString(R.string.all_steps_count, item.steps.size());
        return getContext().getString(R.string.all_task_meta,
                recurrenceLabel(item.task.recurrence), timing, steps);
    }

    private String recurrenceLabel(Recurrence value) {
        if (value == Recurrence.ONCE) return getContext().getString(R.string.rhythm_once);
        if (value == Recurrence.DAILY) return getContext().getString(R.string.rhythm_daily);
        if (value == Recurrence.INTERVAL) return getContext().getString(R.string.rhythm_every_n);
        return getContext().getString(R.string.rhythm_weekdays);
    }

    private String slotLabel(TaskSlot slot) {
        if (slot == TaskSlot.MORNING) return getContext().getString(R.string.slot_morning);
        if (slot == TaskSlot.MIDDAY) return getContext().getString(R.string.slot_midday);
        if (slot == TaskSlot.EVENING) return getContext().getString(R.string.slot_evening);
        return getContext().getString(R.string.slot_later);
    }

    private static LayoutParams params(int width, int height, int left, int top,
                                       int right, int bottom) {
        LayoutParams value = new LayoutParams(width, height);
        value.setMargins(left, top, right, bottom);
        return value;
    }

    private static LinearLayout.LayoutParams rowMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams value = new LinearLayout.LayoutParams(-1, -2);
        value.setMargins(left, top, right, bottom);
        return value;
    }

    private static final class Action {
        final int id;
        final int label;
        Action(int id, int label) { this.id = id; this.label = label; }
    }
}
