package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

/** Dense management surface. Completion actions deliberately do not live here. */
@SuppressLint("ViewConstructor")
public final class AllTasksView extends LinearLayout {
    public interface Listener {
        void onQuery(String query);
        void onStatus(AllTasksUiState.Status status);
        void onSlots(Set<TaskSlot> slots);
        void onRecurrences(Set<Recurrence> recurrences);
        void onWeekday(int weekday);
        void onMode(AllTasksUiState.Mode mode);
        void onToggleTask(String taskId);
        void onEditTask(String taskId);
        void onEditStep(String taskId, String stepId);
        void onAddStep(String taskId);
        void onDeleteTask(String taskId, String title);
        void onMoveSchedule(String entryId, TaskSlot slot, String beforeEntryId);
        void onMoveStep(String stepId, String taskId, String beforeStepId);
        void onSwapSteps(String stepId, String targetStepId);
    }

    private final UiStyle style;
    private final ScrollView scroll;
    private final Listener listener;
    private final EditText search;
    private final LinearLayout controls;
    private final LinearLayout body;
    private boolean bindingSearch;
    private AllTasksUiState state = AllTasksUiState.empty();
    private DayPalette palette;

    public AllTasksView(Context context, ScrollView scroll, Listener listener) {
        super(context);
        this.scroll = scroll;
        this.listener = listener;
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        TextView title = style.serif(context.getString(R.string.all_title), 30, 0,
                true, 300);
        addView(title, params(-1, -2, 0, 0, 0, 14));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.all_search_hint);
        search.setTextSize(17);
        search.setTypeface(style.sans);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        search.setPadding(style.dp(18), 0, style.dp(18), 0);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingSearch) listener.onQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        addView(search, params(-1, style.dp(50), 0, 0, 0, 14));
        controls = new LinearLayout(context);
        controls.setOrientation(VERTICAL);
        addView(controls, new LayoutParams(-1, -2));
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setClipChildren(false);
        addView(body, params(-1, -2, 0, 12, 0, 24));
    }

    public void bind(AllTasksUiState state, DayPalette palette) {
        this.state = state;
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
        renderControls();
        renderBody();
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

    private void renderBody() {
        body.removeAllViews();
        if (state.mode == AllTasksUiState.Mode.SORT) renderSchedule();
        else renderTasks();
    }

    private void renderTasks() {
        if (state.tasks.isEmpty()) {
            addEmpty(state.catalog.items.isEmpty());
            return;
        }
        for (AllTasksUiState.TaskItem item : state.tasks) addTask(item);
    }

    private void addTask(AllTasksUiState.TaskItem item) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(style.dp(18), style.dp(16), style.dp(14), style.dp(16));
        card.setBackground(style.leaf(item.archived ? palette.leaf3 : palette.leaf2,
                style.edge(palette, item.archived ? 3 : 2), 42, 8, 42, 8));
        style.shadow(card, palette, 5, .55f);
        if (!item.archived)
            card.setOnDragListener((view, event) -> stepDrop(view, event,
                    item.task.id.value, null, false));

        LinearLayout header = row();
        TextView title = style.serif(item.task.title, 22,
                item.archived ? palette.done : palette.ink, false, 350);
        LinearLayout copy = new LinearLayout(getContext()); copy.setOrientation(VERTICAL);
        copy.addView(title);
        String meta = taskMeta(item);
        copy.addView(style.sans(meta, 14, palette.hint, false));
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView expand = style.sans(item.expanded ? "⌃" : "⌄", 20, palette.dot, false);
        expand.setGravity(Gravity.CENTER);
        header.addView(expand, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        header.setOnClickListener(view -> listener.onToggleTask(item.task.id.value));
        card.addView(header);

        LinearLayout actions = row();
        if (!item.archived) {
            actions.addView(textAction(R.string.task_edit,
                    () -> listener.onEditTask(item.task.id.value)));
        }
        actions.addView(textAction(R.string.task_delete,
                () -> listener.onDeleteTask(item.task.id.value, item.task.title)));
        card.addView(actions, params(-1, -2, 0, 8, 0, 0));

        if (item.expanded) {
            for (TaskStepTemplate step : item.steps) {
                View gap = item.archived ? new View(getContext())
                        : stepGap(item.task.id.value, step.id);
                card.addView(gap, new LinearLayout.LayoutParams(-1, style.dp(14)));
                card.addView(stepRow(item.task.id.value, step, !item.archived));
            }
            if (!item.archived) {
                card.addView(stepGap(item.task.id.value, null),
                        new LinearLayout.LayoutParams(-1, style.dp(18)));
                TextView add = style.sans(getContext().getString(R.string.all_add_step), 15,
                        palette.ink2, false);
                add.setGravity(Gravity.CENTER_VERTICAL);
                add.setMinHeight(style.dp(48));
                add.setOnClickListener(view -> listener.onAddStep(item.task.id.value));
                card.addView(add);
            }
        }
        body.addView(card, params(-1, -2, 0, 0, 0, 14));
    }

    private View stepRow(String taskId, TaskStepTemplate step, boolean draggable) {
        LinearLayout row = row();
        row.setPadding(style.dp(8), style.dp(4), style.dp(4), style.dp(4));
        TextView handle = style.sans("≡", 21, palette.dot, false);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription(getContext().getString(R.string.all_drag_step));
        row.addView(handle, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        TextView title = style.sans(step.text, 17, palette.ink, false);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.setBackground(style.pill(UiStyle.alpha(palette.leaf1, .72f), 18));
        if (draggable) {
            row.setOnClickListener(view -> listener.onEditStep(taskId, step.id));
            handle.setOnLongClickListener(view -> startDrag(view,
                    DragPayload.step(step.id, taskId)));
        }
        if (draggable)
            row.setOnDragListener((view, event) -> stepDrop(view, event,
                    taskId, step.id, true));
        return row;
    }

    private View stepGap(String taskId, String beforeStepId) {
        View gap = new View(getContext());
        gap.setOnDragListener((view, event) -> stepDrop(view, event,
                taskId, beforeStepId, false));
        return gap;
    }

    private boolean stepDrop(View target, DragEvent event, String taskId,
                             String targetStepId, boolean swap) {
        autoScroll(target, event);
        DragPayload payload = payload(event);
        if (payload == null || payload.kind != DragPayload.STEP) return false;
        if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED)
            ((View) event.getLocalState()).setAlpha(.55f);
        if (event.getAction() == DragEvent.ACTION_DRAG_EXITED
                || event.getAction() == DragEvent.ACTION_DRAG_ENDED)
            ((View) event.getLocalState()).setAlpha(1f);
        if (event.getAction() == DragEvent.ACTION_DROP) {
            ((View) event.getLocalState()).setAlpha(1f);
            if (payload.id.equals(targetStepId)) return true;
            if (swap && targetStepId != null) listener.onSwapSteps(payload.id, targetStepId);
            else listener.onMoveStep(payload.id, taskId, targetStepId);
        }
        return true;
    }

    private void renderSchedule() {
        if (state.schedule.isEmpty()) {
            addEmpty(false);
            return;
        }
        for (TaskSlot slot : TaskSlot.values()) {
            if (!state.slots.isEmpty() && !state.slots.contains(slot)) continue;
            TextView marker = style.serif(slotLabel(slot).toLowerCase(), 17,
                    palette.muted, true, 300);
            body.addView(marker, params(-1, -2, 4, 12, 0, 6));
            for (AllTasksUiState.ScheduleItem item : state.schedule) {
                if (item.slot != slot) continue;
                body.addView(scheduleGap(slot, item.id),
                        new LinearLayout.LayoutParams(-1, style.dp(16)));
                body.addView(scheduleRow(item));
            }
            body.addView(scheduleGap(slot, null),
                    new LinearLayout.LayoutParams(-1, style.dp(28)));
        }
    }

    private View scheduleRow(AllTasksUiState.ScheduleItem item) {
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(14), style.dp(8), style.dp(10), style.dp(8));
        row.setBackground(style.leaf(palette.leaf2, palette.leaf2Edge, 36, 8, 36, 8));
        TextView handle = style.sans("≡", 22, palette.dot, false);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription(getContext().getString(R.string.all_drag_task));
        row.addView(handle, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        LinearLayout copy = new LinearLayout(getContext()); copy.setOrientation(VERTICAL);
        copy.addView(style.serif(item.title, 20, palette.ink, false, 350));
        copy.addView(style.sans(recurrenceLabel(item.recurrence), 14, palette.hint, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        handle.setOnLongClickListener(view -> startDrag(view,
                DragPayload.schedule(item.id, item.taskId)));
        return row;
    }

    private View scheduleGap(TaskSlot slot, String beforeEntryId) {
        View gap = new View(getContext());
        gap.setOnDragListener((view, event) -> {
            autoScroll(view, event);
            DragPayload payload = payload(event);
            if (payload == null || payload.kind != DragPayload.SCHEDULE) return false;
            if (event.getAction() == DragEvent.ACTION_DROP && !payload.id.equals(beforeEntryId))
                listener.onMoveSchedule(payload.id, slot, beforeEntryId);
            return true;
        });
        return gap;
    }

    private boolean startDrag(View source, DragPayload payload) {
        source.setTag(payload);
        ClipData data = ClipData.newPlainText("auto-secretary", payload.id);
        return source.startDragAndDrop(data, new View.DragShadowBuilder(source), source, 0);
    }

    private void autoScroll(View target, DragEvent event) {
        if (scroll == null || event.getAction() != DragEvent.ACTION_DRAG_LOCATION) return;
        int[] scrollLocation = new int[2];
        int[] targetLocation = new int[2];
        scroll.getLocationOnScreen(scrollLocation);
        target.getLocationOnScreen(targetLocation);
        float y = targetLocation[1] - scrollLocation[1] + event.getY();
        if (y < style.dp(72)) scroll.smoothScrollBy(0, -style.dp(28));
        else if (y > scroll.getHeight() - style.dp(72)) scroll.smoothScrollBy(0, style.dp(28));
    }

    private static DragPayload payload(DragEvent event) {
        Object value = event.getLocalState();
        if (!(value instanceof View)) return null;
        Object tag = ((View) value).getTag();
        return tag instanceof DragPayload ? (DragPayload) tag : null;
    }

    private TextView chip(int label, boolean selected, Runnable action) {
        TextView chip = style.sans(getContext().getString(label), 14,
                selected ? palette.accentText : palette.ink2, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(style.dp(44));
        chip.setPadding(style.dp(15), 0, style.dp(15), 0);
        GradientDrawable background = style.pill(selected ? palette.accent
                : Color.TRANSPARENT, 22);
        if (!selected) background.setStroke(style.dp(1), palette.dot);
        chip.setBackground(background);
        chip.setOnClickListener(view -> action.run());
        LayoutParams params = new LayoutParams(-2, style.dp(44)); params.rightMargin = style.dp(8);
        chip.setLayoutParams(params);
        return chip;
    }

    private View labeled(int label, LinearLayout row) {
        LinearLayout wrapper = new LinearLayout(getContext()); wrapper.setOrientation(VERTICAL);
        wrapper.addView(style.serif(getContext().getString(label), 14,
                palette.muted, true, 300));
        wrapper.addView(horizontal(row), params(-1, -2, 0, 5, 0, 0));
        wrapper.setLayoutParams(params(-1, -2, 0, 10, 0, 0));
        return wrapper;
    }

    private HorizontalScrollView horizontal(LinearLayout row) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false); scroll.addView(row);
        return scroll;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); return row;
    }

    private TextView textAction(int label, Runnable action) {
        TextView view = style.sans(getContext().getString(label), 15, palette.ink2, false);
        view.setGravity(Gravity.CENTER); view.setMinHeight(style.dp(48));
        view.setPadding(style.dp(14), 0, style.dp(14), 0);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private void addEmpty(boolean noTasks) {
        LinearLayout empty = new LinearLayout(getContext()); empty.setOrientation(VERTICAL);
        empty.setPadding(style.dp(22), style.dp(28), style.dp(22), style.dp(28));
        empty.setBackground(style.dashed(palette));
        empty.addView(style.serif(getContext().getString(noTasks
                ? R.string.all_no_tasks_title : R.string.all_empty_title),
                25, palette.ink, false, 250));
        empty.addView(style.sans(getContext().getString(noTasks
                ? R.string.all_no_tasks_subtitle : R.string.all_empty_subtitle),
                16, palette.hint, false), params(-1, -2, 0, 12, 0, 0));
        body.addView(empty);
    }

    private String taskMeta(AllTasksUiState.TaskItem item) {
        StringBuilder value = new StringBuilder(recurrenceLabel(item.task.recurrence));
        if (item.archived) value.append(" · ").append(getContext().getString(R.string.all_archived));
        else if (item.task.nextDueOn != null)
            value.append(" · ").append(getContext().getString(R.string.all_next_due,
                    item.task.nextDueOn.format(DateTimeFormatter.ofPattern("dd.MM."))));
        value.append(" · ").append(item.steps.size() == 1
                ? getContext().getString(R.string.all_step_count)
                : getContext().getString(R.string.all_steps_count, item.steps.size()));
        return value.toString();
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
        value.setMargins(left, top, right, bottom); return value;
    }

    private static final class DragPayload {
        static final int SCHEDULE = 1, STEP = 2;
        final int kind; final String id; final String taskId;
        private DragPayload(int kind, String id, String taskId) {
            this.kind = kind; this.id = id; this.taskId = taskId;
        }
        static DragPayload schedule(String id, String taskId) {
            return new DragPayload(SCHEDULE, id, taskId);
        }
        static DragPayload step(String id, String taskId) {
            return new DragPayload(STEP, id, taskId);
        }
    }
}
