package de.thonktank.autosecretary.presentation.alltasks;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.EditorFlowLayout;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Virtualized management surface. Completion actions deliberately do not live here. */
@SuppressLint("ViewConstructor")
public final class AllTasksView extends LinearLayout {
    private static final PathInterpolator STATE_EASING =
            new PathInterpolator(.2f, .7f, .3f, 1f);
    private enum FilterMenu { STATUS, SLOTS, RHYTHMS, WEEKDAY }

    public interface Listener {
        default void onQuery(String query) { }
        default void onStatus(AllTasksUiState.Status status) { }
        default void onSlots(Set<TaskSlot> slots) { }
        default void onRecurrences(Set<Recurrence> recurrences) { }
        default void onWeekday(int weekday) { }
        default void onMode(AllTasksUiState.Mode mode) { }
        default void onResetFilters() { }
        default void onToggleTask(String cardKey) { }
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
    private final FrameLayout stage;
    private final LinearLayout content;
    private final EditText search;
    private final FrameLayout filtersHost;
    private final EditorFlowLayout filterFlow;
    private final TextView count;
    private final LinearLayout resultActions;
    private final RecyclerView list;
    private final RowAdapter adapter;
    private final View dismissLayer;
    private final LinearLayout dropdown;
    private boolean bindingSearch;
    private boolean filtersOpen = true;
    private boolean dragActive;
    private FilterMenu openMenu;
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
        setPadding(style.dimen(R.dimen.page_start), style.dp(16),
                style.dimen(R.dimen.page_end), 0);

        stage = new FrameLayout(context);
        stage.setClipChildren(false);
        stage.setClipToPadding(false);
        addView(stage, new LayoutParams(-1, 0, 1));

        content = column();
        content.setClipChildren(false);
        stage.addView(content, new FrameLayout.LayoutParams(-1, -1));

        search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.all_search_hint);
        search.setTextSize(17);
        search.setTypeface(style.sans);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        search.setPadding(style.dp(18), 0, style.dp(18), 0);
        search.setElevation(style.dp(6));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count,
                                                    int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingSearch) listener.onQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        content.addView(search, new LinearLayout.LayoutParams(-1, style.dp(50)));

        filtersHost = new FrameLayout(context);
        filtersHost.setClipChildren(false);
        filtersHost.setElevation(style.dp(6));
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, -2);
        filterParams.topMargin = style.dp(12);
        content.addView(filtersHost, filterParams);
        filterFlow = new EditorFlowLayout(context);
        filtersHost.addView(filterFlow, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout result = row();
        result.setPadding(style.dp(2), 0, 0, 0);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.topMargin = style.dp(12);
        content.addView(result, resultParams);
        count = style.sans("", 14, Color.TRANSPARENT, false);
        count.setMaxLines(2);
        count.setGravity(Gravity.CENTER_VERTICAL);
        result.addView(count, new LinearLayout.LayoutParams(0, style.dp(44), 1));
        resultActions = row();
        result.addView(resultActions, new LinearLayout.LayoutParams(-2, -2));

        list = new RecyclerView(context);
        list.setId(R.id.all_tasks_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, style.dp(10), 0, style.dp(26));
        configureListAnimations();
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        new ItemTouchHelper(new DragCallback()).attachToRecyclerView(list);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        dismissLayer = new View(context);
        dismissLayer.setBackgroundColor(Color.TRANSPARENT);
        dismissLayer.setVisibility(GONE);
        dismissLayer.setElevation(style.dp(4));
        dismissLayer.setOnClickListener(ignored -> closeMenu());
        stage.addView(dismissLayer, new FrameLayout.LayoutParams(-1, -1));

        dropdown = column();
        dropdown.setVisibility(GONE);
        dropdown.setElevation(style.dp(7));
        stage.addView(dropdown, new FrameLayout.LayoutParams(-1, -2));
        filtersHost.addOnLayoutChangeListener((view, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) ->
                positionMenuLayers());
    }

    public void bind(AllTasksUiState state, DayPalette palette) {
        this.state = state;
        boolean paletteChanged = this.palette == null || this.palette.ink != palette.ink
                || this.palette.leaf1 != palette.leaf1 || this.palette.accent != palette.accent;
        this.palette = palette;
        configureListAnimations();
        bindingSearch = true;
        if (!search.getText().toString().equals(state.query)) {
            search.setText(state.query);
            search.setSelection(search.length());
        }
        bindingSearch = false;
        search.setTextColor(palette.ink);
        search.setHintTextColor(palette.hint);
        GradientDrawable searchBackground = style.pill(UiStyle.alpha(palette.leaf1, .86f), 25);
        searchBackground.setStroke(Math.max(1, style.dp(1)), palette.leaf1Edge);
        search.setBackground(searchBackground);

        String nextControlsKey = state.status + "|" + state.slots + "|" + state.recurrences
                + "|" + state.weekday + "|" + state.mode + "|" + filtersOpen + "|"
                + openMenu + "|" + palette.accent + '|' + palette.ink2;
        if (!nextControlsKey.equals(controlsKey)) {
            controlsKey = nextControlsKey;
            renderControls();
        } else {
            renderCount();
        }
        adapter.submitList(AllTasksRow.project(state));
        if (paletteChanged && adapter.getItemCount() > 0)
            adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "palette");
    }

    private void configureListAnimations() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (list != null) list.setItemAnimator(null);
            return;
        }
        if (list != null && list.getItemAnimator() instanceof DefaultItemAnimator) return;
        if (list == null) return;
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(240);
        animator.setRemoveDuration(240);
        animator.setMoveDuration(240);
        animator.setChangeDuration(240);
        list.setItemAnimator(animator);
    }

    private void renderControls() {
        filterFlow.removeAllViews();
        if (state.mode == AllTasksUiState.Mode.LIST)
            filterFlow.addView(filterChip(statusLabel(), state.status != AllTasksUiState.Status.ACTIVE,
                    FilterMenu.STATUS));
        filterFlow.addView(filterChip(multiLabel(getContext().getString(R.string.all_filter_time),
                slotLabels()), !state.slots.isEmpty(), FilterMenu.SLOTS));
        filterFlow.addView(filterChip(multiLabel(getContext().getString(R.string.all_filter_rhythm),
                recurrenceLabels()), !state.recurrences.isEmpty(), FilterMenu.RHYTHMS));
        if (state.mode == AllTasksUiState.Mode.SORT)
            filterFlow.addView(filterChip(weekdayChipLabel(), state.weekday != 0,
                    FilterMenu.WEEKDAY));
        if (activeFilterCount() > 0) filterFlow.addView(resetAction());
        filtersHost.setVisibility(filtersOpen ? VISIBLE : GONE);

        resultActions.removeAllViews();
        int modeLabel = state.mode == AllTasksUiState.Mode.LIST
                ? R.string.all_sort_mode : R.string.all_tasks_mode;
        resultActions.addView(resultAction(getContext().getString(modeLabel), () -> {
            closeMenu();
            listener.onMode(state.mode == AllTasksUiState.Mode.LIST
                    ? AllTasksUiState.Mode.SORT : AllTasksUiState.Mode.LIST);
        }));
        String filterText = getContext().getString(R.string.all_filter_toggle);
        if (!filtersOpen && activeFilterCount() > 0)
            filterText += " · " + activeFilterCount();
        filterText += filtersOpen ? " ⌃" : " ⌄";
        resultActions.addView(resultAction(filterText, this::toggleFilters));
        renderCount();
        renderDropdown();
        post(this::positionMenuLayers);
    }

    private void renderCount() {
        count.setTextColor(palette.muted);
        if (state.mode == AllTasksUiState.Mode.SORT) {
            int matched = state.schedule.size();
            if (matched == 0) count.setText("");
            else if (hasQueryOrFilters())
                count.setText(getContext().getString(R.string.all_schedule_result_filtered,
                        matched, state.schedulePoolSize));
            else count.setText(getContext().getResources().getQuantityString(
                    R.plurals.all_schedule_result, matched, matched));
            return;
        }
        int matched = state.tasks.size();
        if (matched == 0) {
            count.setText("");
        } else if (hasQueryOrFilters()) {
            count.setText(getContext().getString(R.string.all_task_result_filtered,
                    matched, state.taskPoolSize));
        } else {
            int steps = 0;
            for (AllTasksUiState.TaskItem item : state.tasks) steps += item.steps.size();
            count.setText(getContext().getString(R.string.all_task_result,
                    matched, steps));
        }
    }

    private TextView filterChip(String label, boolean selected, FilterMenu menu) {
        TextView chip = style.sans(label + " ⌄", 14,
                selected ? palette.accentText : palette.ink2, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(style.dp(44));
        chip.setPadding(style.dp(14), 0, style.dp(14), 0);
        chip.setSelected(selected);
        GradientDrawable background = style.pill(selected ? palette.accent
                : Color.TRANSPARENT, 22);
        if (!selected) background.setStroke(Math.max(1, style.dp(1)), palette.dot);
        chip.setBackground(ripple(background, 22));
        chip.setOnClickListener(ignored -> {
            openMenu = openMenu == menu ? null : menu;
            controlsKey = "";
            renderControls();
        });
        chip.setContentDescription(label);
        return chip;
    }

    private TextView resetAction() {
        TextView reset = style.sans(getContext().getString(R.string.all_filter_reset),
                14, palette.ink2, false);
        reset.setGravity(Gravity.CENTER);
        reset.setMinHeight(style.dp(44));
        reset.setPadding(style.dp(8), 0, style.dp(8), 0);
        reset.setPaintFlags(reset.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        reset.setBackground(ripple(style.pill(Color.TRANSPARENT, 22), 22));
        reset.setOnClickListener(ignored -> {
            closeMenu();
            listener.onResetFilters();
        });
        return reset;
    }

    private TextView resultAction(String label, Runnable action) {
        TextView view = style.sans(label, 14, palette.ink2, false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(style.dp(44));
        view.setPadding(style.dp(8), 0, style.dp(8), 0);
        view.setBackground(ripple(style.pill(Color.TRANSPARENT, 22), 22));
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private void renderDropdown() {
        dropdown.removeAllViews();
        if (openMenu == null || !filtersOpen) {
            dropdown.setVisibility(GONE);
            dismissLayer.setVisibility(GONE);
            return;
        }
        dropdown.setPadding(0, style.dp(6), 0, style.dp(6));
        dropdown.setBackground(style.leaf(palette.leaf1, palette.leaf1Edge,
                8, 24, 8, 24));
        style.shadow(dropdown, palette, 7, .75f);
        if (openMenu == FilterMenu.STATUS) {
            addMenuItem(R.string.all_status_active,
                    state.status == AllTasksUiState.Status.ACTIVE, () -> {
                        closeMenu(); listener.onStatus(AllTasksUiState.Status.ACTIVE);
                    });
            addMenuItem(R.string.all_status_archived,
                    state.status == AllTasksUiState.Status.ARCHIVED, () -> {
                        closeMenu(); listener.onStatus(AllTasksUiState.Status.ARCHIVED);
                    });
            addMenuItem(R.string.all_status_all,
                    state.status == AllTasksUiState.Status.ALL, () -> {
                        closeMenu(); listener.onStatus(AllTasksUiState.Status.ALL);
                    });
        } else if (openMenu == FilterMenu.SLOTS) {
            int[] labels = {R.string.slot_morning, R.string.slot_midday,
                    R.string.slot_evening, R.string.slot_later};
            TaskSlot[] values = TaskSlot.values();
            for (int index = 0; index < values.length; index++) {
                TaskSlot slot = values[index];
                addMenuItem(labels[index], state.slots.contains(slot), () -> {
                    EnumSet<TaskSlot> selected = state.slots.isEmpty()
                            ? EnumSet.noneOf(TaskSlot.class) : EnumSet.copyOf(state.slots);
                    if (!selected.add(slot)) selected.remove(slot);
                    listener.onSlots(selected);
                });
            }
        } else if (openMenu == FilterMenu.RHYTHMS) {
            int[] labels = {R.string.rhythm_once, R.string.rhythm_daily,
                    R.string.rhythm_every_n, R.string.rhythm_weekdays};
            Recurrence[] values = {Recurrence.ONCE, Recurrence.DAILY,
                    Recurrence.INTERVAL, Recurrence.WEEKDAYS};
            for (int index = 0; index < values.length; index++) {
                Recurrence recurrence = values[index];
                addMenuItem(labels[index], state.recurrences.contains(recurrence), () -> {
                    EnumSet<Recurrence> selected = state.recurrences.isEmpty()
                            ? EnumSet.noneOf(Recurrence.class)
                            : EnumSet.copyOf(state.recurrences);
                    if (!selected.add(recurrence)) selected.remove(recurrence);
                    listener.onRecurrences(selected);
                });
            }
        } else {
            addMenuItem(R.string.all_every_day, state.weekday == 0, () -> {
                closeMenu(); listener.onWeekday(0);
            });
            int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                    R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
            for (int index = 0; index < labels.length; index++) {
                int day = index + 1;
                addMenuItem(labels[index], state.weekday == day, () -> {
                    closeMenu(); listener.onWeekday(day);
                });
            }
        }
        dropdown.setVisibility(VISIBLE);
        dismissLayer.setVisibility(VISIBLE);
    }

    private void addMenuItem(int label, boolean selected, Runnable action) {
        TextView item = style.sans(getContext().getString(label), 17,
                selected ? palette.accentText : palette.ink, selected);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinHeight(style.dp(48));
        item.setPadding(style.dp(20), 0, style.dp(20), 0);
        item.setBackground(ripple(style.pill(selected ? palette.accent
                : Color.TRANSPARENT, 0), 0));
        item.setSelected(selected);
        item.setOnClickListener(ignored -> action.run());
        dropdown.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }

    private void positionMenuLayers() {
        if (openMenu == null || !filtersOpen) return;
        int top = filtersHost.getBottom() + style.dp(8);
        FrameLayout.LayoutParams menuParams = (FrameLayout.LayoutParams) dropdown.getLayoutParams();
        menuParams.topMargin = top;
        dropdown.setLayoutParams(menuParams);
        FrameLayout.LayoutParams dismissParams = (FrameLayout.LayoutParams)
                dismissLayer.getLayoutParams();
        dismissParams.topMargin = filtersHost.getBottom();
        dismissLayer.setLayoutParams(dismissParams);
        dropdown.bringToFront();
    }

    private void toggleFilters() {
        closeMenu();
        filtersOpen = !filtersOpen;
        controlsKey = "";
        if (!ValueAnimator.areAnimatorsEnabled()) {
            renderControls();
            return;
        }
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(palette.motion.stateChangeDurationMs);
        transition.setInterpolator(LayoutTransition.CHANGING, STATE_EASING);
        content.setLayoutTransition(transition);
        renderControls();
        content.postDelayed(() -> content.setLayoutTransition(null),
                palette.motion.stateChangeDurationMs);
    }

    private void closeMenu() {
        if (openMenu == null) return;
        openMenu = null;
        controlsKey = "";
        renderControls();
    }

    private int activeFilterCount() {
        int result = state.slots.size() + state.recurrences.size();
        if (state.mode == AllTasksUiState.Mode.LIST
                && state.status != AllTasksUiState.Status.ACTIVE) result++;
        if (state.mode == AllTasksUiState.Mode.SORT && state.weekday != 0) result++;
        return result;
    }

    private boolean hasQueryOrFilters() {
        return !state.query.trim().isEmpty() || activeFilterCount() > 0;
    }

    private String statusLabel() {
        String value;
        if (state.status == AllTasksUiState.Status.ARCHIVED)
            value = getContext().getString(R.string.all_status_archived);
        else if (state.status == AllTasksUiState.Status.ALL)
            value = getContext().getString(R.string.all_status_all);
        else value = getContext().getString(R.string.all_status_active);
        return getContext().getString(R.string.all_filter_value,
                getContext().getString(R.string.all_filter_status), value);
    }

    private String weekdayChipLabel() {
        if (state.weekday == 0) return getContext().getString(R.string.all_filter_day);
        int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
        return getContext().getString(R.string.all_filter_value,
                getContext().getString(R.string.all_filter_day),
                getContext().getString(labels[state.weekday - 1]));
    }

    private List<String> slotLabels() {
        List<String> values = new ArrayList<>();
        if (state.slots.contains(TaskSlot.MORNING)) values.add(getContext().getString(R.string.slot_morning));
        if (state.slots.contains(TaskSlot.MIDDAY)) values.add(getContext().getString(R.string.slot_midday));
        if (state.slots.contains(TaskSlot.EVENING)) values.add(getContext().getString(R.string.slot_evening));
        if (state.slots.contains(TaskSlot.LATER)) values.add(getContext().getString(R.string.slot_later));
        return values;
    }

    private List<String> recurrenceLabels() {
        List<String> values = new ArrayList<>();
        Recurrence[] recurrences = {Recurrence.ONCE, Recurrence.DAILY,
                Recurrence.INTERVAL, Recurrence.WEEKDAYS};
        for (Recurrence recurrence : recurrences)
            if (state.recurrences.contains(recurrence)) values.add(recurrenceLabel(recurrence));
        return values;
    }

    private String multiLabel(String base, List<String> selected) {
        if (selected.isEmpty()) return base;
        String summary = selected.size() == 1 && selected.get(0).length() <= 12
                ? selected.get(0)
                : getContext().getString(R.string.all_filter_selected_count, selected.size());
        return getContext().getString(R.string.all_filter_value, base, summary);
    }

    @Override protected void onDetachedFromWindow() {
        closeMenu();
        super.onDetachedFromWindow();
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

        @Override public void onBindViewHolder(@NonNull RowHolder holder, int position,
                                               @NonNull List<Object> payloads) {
            holder.bind(getItem(position));
        }

        void selectForSwap(String id) {
            selectedStepId = id;
            notifyItemRangeChanged(0, getItemCount(), "actions");
            list.announceForAccessibility(getContext().getString(R.string.a11y_step_selected));
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        RowHolder(@NonNull FrameLayout root) { super(root); }

        void bind(AllTasksRow value) {
            FrameLayout root = (FrameLayout) itemView;
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,
                    value.kind == AllTasksRow.Kind.STEP_TARGET
                            ? dragActive ? style.dp(44) : 0 : -2));
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
                case STEP_ADD: child = stepAdd(value); break;
                case SLOT_HEADER: child = slotHeader(value.slot); break;
                case SCHEDULE: child = scheduleRow(value); break;
                case SCHEDULE_TARGET: child = scheduleTarget(value); break;
                default: child = empty(value.emptyReason);
            }
            root.addView(child, new FrameLayout.LayoutParams(-1,
                    value.kind == AllTasksRow.Kind.STEP_TARGET ? -1 : -2));
            installAccessibility(root, value);
        }
    }

    private View taskHeader(AllTasksRow row) {
        AllTasksUiState.TaskItem item = row.task;
        LinearLayout card = column();
        card.setPadding(style.dp(18), style.dp(14), style.dp(8),
                item.expanded ? 0 : style.dp(12));
        card.setBackground(item.expanded
                ? new CardSegmentDrawable(CardSegment.TOP)
                : style.leaf(palette.leaf2, palette.leaf2Edge, 42, 8, 42, 8));
        if (!item.expanded) style.shadow(card, palette, 5, .55f);
        LinearLayout header = row();
        LinearLayout copy = column();
        TextView title = style.serif(item.task.title, 22, palette.ink, false, 350);
        title.setText(highlight(item.task.title, item.needle));
        copy.addView(title);
        TextView meta = style.sans(taskMeta(item), 14, palette.hint, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = style.dp(3);
        copy.addView(meta, metaParams);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView menu = style.sans("⋮", 21, palette.dot, false);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription(getContext().getString(R.string.a11y_task_menu,
                item.task.title));
        menu.setBackground(ripple(style.pill(Color.TRANSPARENT, 24), 24));
        menu.setOnClickListener(anchor -> showTaskMenu(anchor, item));
        header.addView(menu, new LinearLayout.LayoutParams(style.dp(48), style.dp(48)));
        card.addView(header);

        TextView steps = style.sans(stepLine(item), 15, palette.ink2, false);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        steps.setMinHeight(style.dp(44));
        steps.setPadding(0, 0, style.dp(8), 0);
        if (!item.steps.isEmpty()) {
            steps.setOnClickListener(ignored -> listener.onToggleTask(item.cardKey));
            steps.setContentDescription(getContext().getString(item.expanded
                    ? R.string.a11y_collapse_task : R.string.a11y_expand_task));
        }
        LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(-2, -2);
        stepParams.topMargin = style.dp(-4);
        card.addView(steps, stepParams);
        card.setContentDescription(getContext().getString(R.string.a11y_task_row,
                item.task.title, taskMeta(item)));
        card.setLayoutParams(rowMargins(0, 0, 0, item.expanded ? 0 : style.dp(10)));
        return card;
    }

    private void showTaskMenu(View anchor, AllTasksUiState.TaskItem item) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.task_edit);
        menu.getMenu().add(0, 2, 1, R.string.task_delete);
        menu.setOnMenuItemClickListener(selected -> {
            if (selected.getItemId() == 1) listener.onEditTask(item.task.id.value);
            else listener.onDeleteTask(item.task.id.value, item.task.title);
            return true;
        });
        menu.show();
    }

    private View stepRow(AllTasksRow row) {
        LinearLayout shell = row();
        shell.setPadding(style.dp(8), 0, style.dp(8), style.dp(4));
        shell.setBackground(new CardSegmentDrawable(CardSegment.MIDDLE));
        LinearLayout pill = row();
        pill.setPadding(style.dp(8), style.dp(3), style.dp(8), style.dp(3));
        pill.setBackground(ripple(style.pill(UiStyle.alpha(palette.leaf1, .72f), 18), 18));
        if (!row.task.archived)
            pill.addView(icon(R.drawable.ic_drag_handle, R.string.all_drag_step),
                    new LinearLayout.LayoutParams(style.dp(44), style.dp(44)));
        TextView title = style.sans(row.step.text, 17, palette.ink, false);
        title.setText(highlight(row.step.text, row.task.needle));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(style.dp(44));
        pill.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        pill.setOnClickListener(view -> listener.onEditStep(row.taskId, row.step.id));
        pill.setContentDescription(getContext().getString(R.string.a11y_step_row, row.step.text));
        shell.addView(pill, new LinearLayout.LayoutParams(-1, -2));
        return shell;
    }

    private View stepTarget(AllTasksRow row) {
        FrameLayout target = new FrameLayout(getContext());
        target.setBackground(new CardSegmentDrawable(CardSegment.MIDDLE));
        if (dragActive) {
            View line = new View(getContext());
            line.setBackgroundColor(palette.light);
            FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(-1, style.dp(2));
            lineParams.gravity = Gravity.CENTER_VERTICAL;
            lineParams.setMargins(style.dp(16), 0, style.dp(16), 0);
            target.addView(line, lineParams);
            target.setContentDescription(getContext().getString(R.string.a11y_step_drop_target));
        }
        return target;
    }

    private View stepAdd(AllTasksRow row) {
        TextView add = style.sans("＋ " + getContext().getString(R.string.all_add_step),
                14, palette.ink2, false);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinHeight(style.dp(44));
        add.setPadding(style.dp(20), 0, style.dp(8), 0);
        add.setBackground(new CardSegmentDrawable(CardSegment.BOTTOM));
        add.setOnClickListener(ignored -> listener.onAddStep(row.taskId));
        add.setContentDescription(getContext().getString(R.string.a11y_add_step_target));
        add.setLayoutParams(rowMargins(0, 0, 0, style.dp(10)));
        return add;
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
        empty.setRotation(-.5f);
        int title;
        int subtitle;
        if (reason == AllTasksRow.EmptyReason.SEARCH) {
            title = R.string.all_empty_search_title; subtitle = R.string.all_empty_search_subtitle;
        } else if (reason == AllTasksRow.EmptyReason.FILTERS) {
            title = R.string.all_empty_filter_title;
            subtitle = state.mode == AllTasksUiState.Mode.SORT
                    ? R.string.all_empty_filter_sort_subtitle
                    : R.string.all_empty_filter_subtitle;
        } else {
            title = R.string.all_empty_status_title; subtitle = R.string.all_empty_status_subtitle;
        }
        empty.addView(style.serif(getContext().getString(title), 25, palette.ink, false, 250));
        empty.addView(style.sans(getContext().getString(subtitle), 16, palette.hint, false),
                params(-1, -2, 0, 12, 0, 0));
        return empty;
    }

    private CharSequence highlight(String value, String needle) {
        if (needle == null || needle.isEmpty()) return value;
        SpannableString result = new SpannableString(value);
        String haystack = value.toLowerCase(Locale.GERMAN);
        int from = 0;
        while (from < haystack.length()) {
            int start = haystack.indexOf(needle, from);
            if (start < 0) break;
            int end = start + needle.length();
            result.setSpan(new RoundedHighlightSpan(UiStyle.alpha(palette.light, .34f),
                            style.dp(3), style.dp(1)), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            from = end;
        }
        return result;
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
            if (otherTask(row.cardKey, row.taskId, -1) != null)
                result.add(new Action(R.id.action_step_previous_task,
                        R.string.a11y_step_previous_task));
            if (otherTask(row.cardKey, row.taskId, 1) != null)
                result.add(new Action(R.id.action_step_next_task,
                        R.string.a11y_step_next_task));
            result.add(new Action(R.id.action_step_select_swap, R.string.a11y_step_select_swap));
            if (adapter.selectedStepId != null && !adapter.selectedStepId.equals(row.step.id))
                result.add(new Action(R.id.action_step_swap_selected,
                        R.string.a11y_step_swap_selected));
        } else if (row.kind == AllTasksRow.Kind.SCHEDULE) {
            int index = scheduleIndex(row);
            int inSlot = scheduleInSlot(row.slot).size();
            if (index > 0) result.add(new Action(R.id.action_schedule_up,
                    R.string.a11y_schedule_up));
            if (index < inSlot - 1) result.add(new Action(R.id.action_schedule_down,
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
        String before = delta < 0 ? row.task.steps.get(target).id
                : index + 2 < row.task.steps.size() ? row.task.steps.get(index + 2).id : null;
        listener.onMoveStep(row.step.id, row.taskId, before);
        return true;
    }

    private boolean moveStepToTask(AllTasksRow row, int direction) {
        AllTasksUiState.TaskItem target = otherTask(row.cardKey, row.taskId, direction);
        if (target == null) return false;
        listener.onMoveStep(row.step.id, target.task.id.value, null);
        return true;
    }

    private AllTasksUiState.TaskItem otherTask(String cardKey, String taskId, int direction) {
        int current = -1;
        for (int index = 0; index < state.tasks.size(); index++)
            if (state.tasks.get(index).cardKey.equals(cardKey)) current = index;
        for (int index = current + direction; index >= 0 && index < state.tasks.size();
             index += direction) {
            AllTasksUiState.TaskItem candidate = state.tasks.get(index);
            if (!candidate.archived && !candidate.task.id.value.equals(taskId)) return candidate;
        }
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

        @Override public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder == null) return;
            int position = viewHolder.getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            if (adapter.getCurrentList().get(position).kind == AllTasksRow.Kind.STEP)
                setDragActive(true);
        }

        @Override public void clearView(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            setDragActive(false);
        }

        @Override public boolean onMove(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder,
                                        @NonNull RecyclerView.ViewHolder target) {
            return dispatchDrag(viewHolder.getBindingAdapterPosition(),
                    target.getBindingAdapterPosition());
        }

        @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder,
                                       int direction) { }
    }

    private void setDragActive(boolean active) {
        if (dragActive == active) return;
        dragActive = active;
        for (int index = 0; index < adapter.getItemCount(); index++)
            if (adapter.getCurrentList().get(index).kind == AllTasksRow.Kind.STEP_TARGET)
                adapter.notifyItemChanged(index, "drag");
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
    AllTasksRow adapterRowForTest(int position) { return adapter.getCurrentList().get(position); }
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
    void setDragActiveForTest(boolean active) { setDragActive(active); }
    boolean filtersOpenForTest() { return filtersOpen; }

    private ImageButton icon(int drawable, int description) {
        ImageButton button = new ImageButton(getContext());
        button.setImageResource(drawable);
        button.setColorFilter(palette.dot);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(getContext().getString(description));
        button.setPadding(style.dp(10), style.dp(10), style.dp(10), style.dp(10));
        return button;
    }

    private RippleDrawable ripple(Drawable content, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(UiStyle.alpha(palette.ink, .10f)),
                content, style.pill(Color.WHITE, radius));
    }

    private String taskMeta(AllTasksUiState.TaskItem item) {
        String timing = item.task.nextDueOn == null ? getContext().getString(R.string.all_no_due)
                : getContext().getString(R.string.all_next_due,
                item.task.nextDueOn.format(DateTimeFormatter.ofPattern("dd.MM.")));
        String value = slotLabel(item.slot) + " · " + recurrenceLabel(item.task.recurrence)
                + " · " + timing;
        return item.archived ? value + " · " + getContext().getString(R.string.all_archived)
                : value;
    }

    private String stepLine(AllTasksUiState.TaskItem item) {
        int count = item.steps.size();
        if (count == 0) return getContext().getString(R.string.all_no_steps);
        if (item.searchExpanded)
            return getContext().getString(R.string.all_steps_matching,
                    item.matchingSteps.size(), count) + " ⌃";
        String label = count == 1 ? getContext().getString(R.string.all_step_count)
                : getContext().getString(R.string.all_steps_count, count);
        return label + (item.expanded ? " ⌃" : " ⌄");
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

    private static final class RoundedHighlightSpan extends ReplacementSpan {
        private final int color;
        private final float radius;
        private final float padding;

        RoundedHighlightSpan(int color, float radius, float padding) {
            this.color = color;
            this.radius = radius;
            this.padding = padding;
        }

        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                                     Paint.FontMetricsInt metrics) {
            return Math.round(paint.measureText(text, start, end) + padding * 2);
        }

        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                                   float x, int top, int y, int bottom,
                                   @NonNull Paint paint) {
            int oldColor = paint.getColor();
            float width = paint.measureText(text, start, end);
            paint.setColor(color);
            canvas.drawRoundRect(new RectF(x, top, x + width + padding * 2, bottom),
                    radius, radius, paint);
            paint.setColor(oldColor);
            canvas.drawText(text, start, end, x + padding, y, paint);
        }
    }

    private enum CardSegment { TOP, MIDDLE, BOTTOM }

    /** Draws one continuous leaf edge across independently virtualized card rows. */
    private final class CardSegmentDrawable extends Drawable {
        private final CardSegment segment;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

        CardSegmentDrawable(CardSegment segment) {
            this.segment = segment;
            fill.setColor(palette.leaf2);
            fill.setStyle(Paint.Style.FILL);
            edge.setColor(palette.leaf2Edge);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1, style.dp(1)));
        }

        @Override public void draw(@NonNull Canvas canvas) {
            float half = edge.getStrokeWidth() / 2f;
            RectF bounds = new RectF(getBounds().left + half, getBounds().top,
                    getBounds().right - half, getBounds().bottom);
            Path shape = new Path();
            float topLeft = segment == CardSegment.TOP ? style.dp(42) : 0;
            float topRight = segment == CardSegment.TOP ? style.dp(8) : 0;
            float bottomRight = segment == CardSegment.BOTTOM ? style.dp(42) : 0;
            float bottomLeft = segment == CardSegment.BOTTOM ? style.dp(8) : 0;
            shape.addRoundRect(bounds, new float[]{topLeft, topLeft, topRight, topRight,
                    bottomRight, bottomRight, bottomLeft, bottomLeft}, Path.Direction.CW);
            canvas.drawPath(shape, fill);

            Path border = new Path();
            if (segment == CardSegment.TOP) {
                border.moveTo(bounds.left, bounds.bottom);
                border.lineTo(bounds.left, bounds.top + topLeft);
                border.quadTo(bounds.left, bounds.top,
                        bounds.left + topLeft, bounds.top + half);
                border.lineTo(bounds.right - topRight, bounds.top + half);
                border.quadTo(bounds.right, bounds.top,
                        bounds.right, bounds.top + topRight);
                border.lineTo(bounds.right, bounds.bottom);
            } else if (segment == CardSegment.MIDDLE) {
                border.moveTo(bounds.left, bounds.top);
                border.lineTo(bounds.left, bounds.bottom);
                border.moveTo(bounds.right, bounds.top);
                border.lineTo(bounds.right, bounds.bottom);
            } else {
                border.moveTo(bounds.left, bounds.top);
                border.lineTo(bounds.left, bounds.bottom - bottomLeft);
                border.quadTo(bounds.left, bounds.bottom,
                        bounds.left + bottomLeft, bounds.bottom - half);
                border.lineTo(bounds.right - bottomRight, bounds.bottom - half);
                border.quadTo(bounds.right, bounds.bottom,
                        bounds.right, bounds.bottom - bottomRight);
                border.lineTo(bounds.right, bounds.top);
            }
            canvas.drawPath(border, edge);
        }

        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); edge.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter); edge.setColorFilter(colorFilter);
        }
        @Override @SuppressWarnings("deprecation")
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
