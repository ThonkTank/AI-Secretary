package de.thonktank.autosecretary.presentation.alltasks;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.EditorFlowLayout;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.UiStyle;
import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Search, filters, dropdown layers and result actions bound directly from typed state. */
@SuppressLint("ViewConstructor")
final class AllTasksControlsView extends FrameLayout {
    private static final PathInterpolator STATE_EASING =
            new PathInterpolator(.2f, .7f, .3f, 1f);
    private enum FilterMenu { STATUS, SLOTS, RHYTHMS, WEEKDAY }

    private final UiStyle style;
    private final AllTasksView.Listener listener;
    private final LinearLayout content;
    private final EditText search;
    private final FrameLayout filtersHost;
    private final EditorFlowLayout filterFlow;
    private final TextView count;
    private final LinearLayout resultActions;
    private final View dismissLayer;
    private final LinearLayout dropdown;
    private boolean bindingSearch;
    private FilterMenu openMenu;
    private AllTasksUiState state = AllTasksUiState.empty();
    private DayPalette palette;

    AllTasksControlsView(Context context, UiStyle style, AllTasksView.Listener listener,
                         RecyclerView list) {
        super(context);
        this.style = style;
        this.listener = listener;
        setClipChildren(false);
        setClipToPadding(false);

        content = column();
        content.setClipChildren(false);
        addView(content, new FrameLayout.LayoutParams(-1, -1));

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

        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        dismissLayer = new View(context);
        dismissLayer.setBackgroundColor(Color.TRANSPARENT);
        dismissLayer.setVisibility(GONE);
        dismissLayer.setElevation(style.dp(4));
        dismissLayer.setOnClickListener(ignored -> closeMenu());
        addView(dismissLayer, new FrameLayout.LayoutParams(-1, -1));

        dropdown = column();
        dropdown.setVisibility(GONE);
        dropdown.setElevation(style.dp(7));
        addView(dropdown, new FrameLayout.LayoutParams(-1, -2));
        filtersHost.addOnLayoutChangeListener((view, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) ->
                positionMenuLayers());
    }

    void bind(AllTasksUiState state, DayPalette palette) {
        this.state = state;
        if (!state.filtersExpanded) openMenu = null;
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
        searchBackground.setStroke(Math.max(1, style.dp(1)), palette.leaf1Edge);
        search.setBackground(searchBackground);
        renderControls();
    }

    void closeTransientState() { closeMenu(); }
    EditText searchForTest() { return search; }
    boolean dropdownOpenForTest() { return openMenu != null; }

    private void renderControls() {
        filterFlow.removeAllViews();
        if (state.mode == AllTasksUiState.Mode.LIST)
            filterFlow.addView(filterChip(statusLabel(),
                    state.status != AllTasksUiState.Status.ACTIVE, FilterMenu.STATUS));
        filterFlow.addView(filterChip(multiLabel(getContext().getString(R.string.all_filter_time),
                slotLabels()), !state.slots.isEmpty(), FilterMenu.SLOTS));
        filterFlow.addView(filterChip(multiLabel(getContext().getString(R.string.all_filter_rhythm),
                recurrenceLabels()), !state.recurrences.isEmpty(), FilterMenu.RHYTHMS));
        if (state.mode == AllTasksUiState.Mode.SORT)
            filterFlow.addView(filterChip(weekdayChipLabel(), state.weekday != 0,
                    FilterMenu.WEEKDAY));
        if (activeFilterCount() > 0) filterFlow.addView(resetAction());
        filtersHost.setVisibility(state.filtersExpanded ? VISIBLE : GONE);

        resultActions.removeAllViews();
        int modeLabel = state.mode == AllTasksUiState.Mode.LIST
                ? R.string.all_sort_mode : R.string.all_tasks_mode;
        resultActions.addView(resultAction(getContext().getString(modeLabel), () -> {
            closeMenu();
            listener.onMode(state.mode == AllTasksUiState.Mode.LIST
                    ? AllTasksUiState.Mode.SORT : AllTasksUiState.Mode.LIST);
        }));
        String filterText = getContext().getString(R.string.all_filter_toggle);
        if (!state.filtersExpanded && activeFilterCount() > 0)
            filterText += " · " + activeFilterCount();
        filterText += state.filtersExpanded ? " ⌃" : " ⌄";
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
        if (matched == 0) count.setText("");
        else if (hasQueryOrFilters())
            count.setText(getContext().getString(R.string.all_task_result_filtered,
                    matched, state.taskPoolSize));
        else {
            int steps = 0;
            for (AllTasksUiState.TaskItem item : state.tasks) steps += item.steps.size();
            count.setText(getContext().getString(R.string.all_task_result, matched, steps));
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
        if (openMenu == null || !state.filtersExpanded) {
            dropdown.setVisibility(GONE);
            dismissLayer.setVisibility(GONE);
            return;
        }
        dropdown.setPadding(0, style.dp(6), 0, style.dp(6));
        dropdown.setBackground(style.leaf(palette.leaf1, palette.leaf1Edge, 8, 24, 8, 24));
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
        if (openMenu == null || !state.filtersExpanded) return;
        int top = filtersHost.getBottom() + style.dp(8);
        FrameLayout.LayoutParams menuParams = (FrameLayout.LayoutParams) dropdown.getLayoutParams();
        menuParams.topMargin = top;
        dropdown.setLayoutParams(menuParams);
        FrameLayout.LayoutParams dismissParams =
                (FrameLayout.LayoutParams) dismissLayer.getLayoutParams();
        dismissParams.topMargin = filtersHost.getBottom();
        dismissLayer.setLayoutParams(dismissParams);
        dropdown.bringToFront();
    }

    private void toggleFilters() {
        closeMenu();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            listener.onFiltersExpanded(!state.filtersExpanded);
            return;
        }
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(palette.motion.stateChangeDurationMs);
        transition.setInterpolator(LayoutTransition.CHANGING, STATE_EASING);
        content.setLayoutTransition(transition);
        listener.onFiltersExpanded(!state.filtersExpanded);
        content.postDelayed(() -> content.setLayoutTransition(null),
                palette.motion.stateChangeDurationMs);
    }

    private void closeMenu() {
        if (openMenu == null) return;
        openMenu = null;
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
        if (state.slots.contains(TaskSlot.MORNING))
            values.add(getContext().getString(R.string.slot_morning));
        if (state.slots.contains(TaskSlot.MIDDAY))
            values.add(getContext().getString(R.string.slot_midday));
        if (state.slots.contains(TaskSlot.EVENING))
            values.add(getContext().getString(R.string.slot_evening));
        if (state.slots.contains(TaskSlot.LATER))
            values.add(getContext().getString(R.string.slot_later));
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

    private String recurrenceLabel(Recurrence value) {
        if (value == Recurrence.ONCE) return getContext().getString(R.string.rhythm_once);
        if (value == Recurrence.DAILY) return getContext().getString(R.string.rhythm_daily);
        if (value == Recurrence.INTERVAL) return getContext().getString(R.string.rhythm_every_n);
        return getContext().getString(R.string.rhythm_weekdays);
    }

    private LinearLayout row() {
        LinearLayout value = new LinearLayout(getContext());
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setGravity(Gravity.CENTER_VERTICAL);
        return value;
    }

    private LinearLayout column() {
        LinearLayout value = new LinearLayout(getContext());
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    private RippleDrawable ripple(Drawable content, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(UiStyle.alpha(palette.ink, .10f)),
                content, style.pill(Color.WHITE, radius));
    }
}
