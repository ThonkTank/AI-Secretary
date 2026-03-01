package com.autosecretary.features.task.ui.list;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.ui.list.state.ViewSlot;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * RecyclerView adapter for the task list. Renders two visually distinct row types:
 * <ul>
 *   <li><b>Task rows</b> — show checkbox/progress, timer, deadline, streak, and edit controls.</li>
 *   <li><b>Calendar event rows</b> — read-only; show a "Kalender" chip, no interaction controls.</li>
 * </ul>
 *
 * <p>The adapter delegates all user interactions (checkoff, timer, edit, progress) to
 * {@link TaskRowActions} callbacks provided by {@link TaskListFragment}. It does not talk
 * to the ViewModel directly.
 *
 * <p>{@link #setInteractionsEnabled(boolean)} is called with {@code false} when the user
 * navigates to a day other than today, making the entire list read-only.
 */
public class ListRowAdapter extends RecyclerView.Adapter<ListRowAdapter.TaskRowViewHolder> {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final long CHECKBOX_SCALE_DURATION_MS = 100L;
    private static final long COMPLETION_FLASH_DURATION_MS = 300L;
    private static final float ALPHA_ENABLED = 1.0f;
    private static final float ALPHA_DISABLED = 0.4f;

    private List<ViewSlot> viewSlots;
    private final TaskRowActions actions;
    /** False when viewing a past or future day; disables checkboxes, timers, and edit buttons. */
    private boolean interactionsEnabled = true;
    /** True in Manage mode; enables the expand/collapse toggle on parent task rows. */
    private boolean manageMode = false;

    // Cached resource values — resolved once in onAttachedToRecyclerView to avoid per-bind lookups.
    private int indentStepPx;
    private float rowCornerRadius;
    private int rowStrokeWidth;
    private int colorOutlineSemi;
    private int colorCompletedBg;
    private int colorInProgressBg;
    private int colorCompletedCheckboxTint;
    private int colorInProgressCheckboxTint;
    private int colorDeadlineOverdue;
    private int colorDeadlineSoon;
    private int colorDeadlineFuture;
    private int colorCompletionFlash;
    private int colorProgressText;
    private int colorProgressTextDisabled;
    private int colorProgressButtonTint;
    private int colorProgressButtonTintDisabled;
    private int[] streakTierColors;

    public ListRowAdapter(List<ViewSlot> viewSlots, TaskRowActions actions) {
        this.viewSlots = viewSlots;
        this.actions = actions;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        Context ctx = recyclerView.getContext();
        indentStepPx = ctx.getResources().getDimensionPixelSize(R.dimen.task_indent_step);
        rowCornerRadius = ctx.getResources().getDimension(R.dimen.corner_radius_md);
        rowStrokeWidth = (int) ctx.getResources().getDimension(R.dimen.task_editor_input_stroke_width);
        colorOutlineSemi = ContextCompat.getColor(ctx, R.color.task_color_outline_semi);
        colorCompletedBg = ContextCompat.getColor(ctx, R.color.task_completed_background);
        colorInProgressBg = ContextCompat.getColor(ctx, R.color.task_in_progress_background);
        colorCompletedCheckboxTint = ContextCompat.getColor(ctx, R.color.task_completed_checkbox_tint);
        colorInProgressCheckboxTint = ContextCompat.getColor(ctx, R.color.task_in_progress_checkbox_tint);
        colorDeadlineOverdue = ContextCompat.getColor(ctx, R.color.task_deadline_overdue);
        colorDeadlineSoon = ContextCompat.getColor(ctx, R.color.task_deadline_soon);
        colorDeadlineFuture = ContextCompat.getColor(ctx, R.color.task_deadline_future);
        colorCompletionFlash = ContextCompat.getColor(ctx, R.color.task_completion_flash);
        colorProgressText = ContextCompat.getColor(ctx, R.color.task_progress_text);
        colorProgressTextDisabled = ContextCompat.getColor(ctx, R.color.task_progress_text_disabled);
        colorProgressButtonTint = ContextCompat.getColor(ctx, R.color.task_progress_button_tint);
        colorProgressButtonTintDisabled = ContextCompat.getColor(ctx, R.color.task_progress_button_tint_disabled);
        streakTierColors = new int[StreakTier.values().length];
        for (StreakTier tier : StreakTier.values()) {
            streakTierColors[tier.ordinal()] = ContextCompat.getColor(ctx, tier.colorRes);
        }
    }

    public static class TaskRowActions {
        private final Consumer<ViewSlot> onCheck;
        private final Consumer<ViewSlot> onEdit;
        private final Consumer<ViewSlot> onTimerToggle;
        private final Consumer<ViewSlot> onProgressPlus;
        private final Consumer<ViewSlot> onProgressMinus;
        private final Consumer<ViewSlot> onToggleExpand;
        private final Function<ViewSlot, Boolean> isExpanded;

        public TaskRowActions(Consumer<ViewSlot> onCheck,
                              Consumer<ViewSlot> onEdit,
                              Consumer<ViewSlot> onTimerToggle,
                              Consumer<ViewSlot> onProgressPlus,
                              Consumer<ViewSlot> onProgressMinus,
                              Consumer<ViewSlot> onToggleExpand,
                              Function<ViewSlot, Boolean> isExpanded) {
            this.onCheck = onCheck;
            this.onEdit = onEdit;
            this.onTimerToggle = onTimerToggle;
            this.onProgressPlus = onProgressPlus;
            this.onProgressMinus = onProgressMinus;
            this.onToggleExpand = onToggleExpand;
            this.isExpanded = isExpanded;
        }
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        LinearLayout root;
        TextView title;
        TextView goalIcon;
        TextView start;
        TextView end;
        CheckBox checkBox;
        View progressContainer;
        ImageButton progressMinus;
        ImageButton progressPlus;
        TextView progressText;
        TextView deadlineCountdown;
        TextView streakDisplay;
        ImageButton timerButton;
        ImageButton editButton;
        TextView expandToggle;
        ValueAnimator completionAnimator;
        TextView calendarChip;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.root = taskRow.findViewById(R.id.TaskRowRoot);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.goalIcon = taskRow.findViewById(R.id.GoalIcon);
            this.start = taskRow.findViewById(R.id.StartTime);
            this.end = taskRow.findViewById(R.id.EndTime);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
            this.progressContainer = taskRow.findViewById(R.id.ProgressContainer);
            this.progressMinus = taskRow.findViewById(R.id.ProgressMinusButton);
            this.progressPlus = taskRow.findViewById(R.id.ProgressPlusButton);
            this.progressText = taskRow.findViewById(R.id.ProgressText);
            this.deadlineCountdown = taskRow.findViewById(R.id.DeadlineCountdown);
            this.streakDisplay = taskRow.findViewById(R.id.StreakDisplay);
            this.timerButton = taskRow.findViewById(R.id.TaskTimerButton);
            this.editButton = taskRow.findViewById(R.id.EditTaskButton);
            this.expandToggle = taskRow.findViewById(R.id.ExpandToggle);
            this.calendarChip = taskRow.findViewById(R.id.CalendarChip);
        }
    }

    @Override
    public int getItemCount() {
        return viewSlots.size();
    }

    @Override
    public TaskRowViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View taskRow = LayoutInflater.from(parent.getContext()).inflate(R.layout.task_row_item, parent, false);
        return new TaskRowViewHolder(taskRow);
    }

    @Override
    public void onBindViewHolder(TaskRowViewHolder holder, int position) {
        ViewSlot viewSlot = viewSlots.get(position);
        TaskListItem item = viewSlot.getItem();

        holder.title.setText(item.title);
        holder.itemView.setContentDescription(item.title);

        bindIndentation(holder, viewSlot.getDepth());
        bindTimeRange(holder, item);

        if (item.isCalendarEvent()) {
            bindCalendarEventRow(holder);
            return;
        }

        bindTaskRow(holder);
        bindGoalAppearance(holder, item);
        bindDeadline(holder, item);
        bindStreak(holder, item);
        bindProgressState(holder, item);
        bindCompletionMode(holder, item, viewSlot);
        bindTimerState(holder, item);
        bindExpandToggle(holder, viewSlot);
        bindInteractions(holder, item, viewSlot);
    }

    /** Sets left padding based on tree depth for parent-child indentation in Manage mode. */
    private void bindIndentation(TaskRowViewHolder holder, int depth) {
        holder.itemView.setPaddingRelative(
                indentStepPx * depth,
                holder.itemView.getPaddingTop(),
                holder.itemView.getPaddingEnd(),
                holder.itemView.getPaddingBottom());
    }

    /** Resets all view visibilities/listeners to the task-row defaults (as opposed to calendar-row). */
    private void bindTaskRow(TaskRowViewHolder holder) {
        holder.root.setBackgroundResource(R.drawable.task_bg_row);
        holder.checkBox.setVisibility(View.VISIBLE);
        holder.deadlineCountdown.setVisibility(View.VISIBLE);
        holder.streakDisplay.setVisibility(View.VISIBLE);
        holder.timerButton.setVisibility(View.VISIBLE);
        holder.editButton.setVisibility(View.VISIBLE);
        holder.expandToggle.setVisibility(View.GONE);
        holder.calendarChip.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
    }

    /** Shows the goal emoji/icon with its color if the task has a progress target and a goal icon set. */
    private void bindGoalAppearance(TaskRowViewHolder holder, TaskListItem item) {
        if (!item.hasProgressTarget() || item.goalIcon == null || item.goalIcon.trim().isEmpty()) {
            holder.goalIcon.setVisibility(View.GONE);
            return;
        }

        holder.goalIcon.setText(item.goalIcon);
        try {
            holder.goalIcon.setTextColor(Color.parseColor(item.goalColorHex));
        } catch (Exception ex) {
            holder.goalIcon.setTextColor(holder.title.getCurrentTextColor());
        }
        holder.goalIcon.setVisibility(View.VISIBLE);
    }

    /** Shows/hides the expand/collapse arrow for parent tasks in Manage mode. */
    private void bindExpandToggle(TaskRowViewHolder holder, ViewSlot viewSlot) {
        if (!manageMode || viewSlot.getChildren().isEmpty()) {
            holder.expandToggle.setVisibility(View.GONE);
            holder.expandToggle.setOnClickListener(null);
            return;
        }

        holder.expandToggle.setVisibility(View.VISIBLE);
        boolean expanded = actions.isExpanded.apply(viewSlot);
        holder.expandToggle.setText(expanded ? R.string.task_row_toggle_expanded : R.string.task_row_toggle_collapsed);
        holder.expandToggle.setContentDescription(holder.itemView.getContext().getString(
                expanded ? R.string.task_row_collapse_children : R.string.task_row_expand_children));
        holder.expandToggle.setOnClickListener(v -> actions.onToggleExpand.accept(viewSlot));
    }

    /** Configures the row as a read-only calendar event: hides all task controls, shows "Kalender" chip. */
    private void bindCalendarEventRow(TaskRowViewHolder holder) {
        Context context = holder.itemView.getContext();
        holder.root.setBackgroundResource(R.drawable.task_bg_calendar_row);
        holder.checkBox.setVisibility(View.GONE);
        holder.goalIcon.setVisibility(View.GONE);
        holder.progressContainer.setVisibility(View.GONE);
        holder.deadlineCountdown.setVisibility(View.GONE);
        holder.streakDisplay.setVisibility(View.GONE);
        holder.timerButton.setVisibility(View.GONE);
        holder.editButton.setVisibility(View.GONE);
        holder.expandToggle.setVisibility(View.GONE);
        holder.calendarChip.setVisibility(View.VISIBLE);
        holder.calendarChip.setText(context.getString(R.string.task_calendar_label));
        holder.checkBox.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setOnClickListener(null);
        ViewCompat.setStateDescription(holder.itemView, context.getString(R.string.task_calendar_state_description));
    }

    /** Formats and displays the start/end time for the slot, with fallback text if unset. */
    private void bindTimeRange(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        String startString = item.start != null
                ? item.start.format(TIME_FORMATTER)
                : context.getString(R.string.task_time_fallback_start);
        String endString = item.end != null
                ? item.end.format(TIME_FORMATTER)
                : context.getString(R.string.task_time_fallback_end);
        holder.start.setText(startString);
        holder.end.setText(endString);
    }

    /** Shows a deadline urgency label (overdue/today/N days) with urgency-based text color. */
    private void bindDeadline(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        TaskListItem.DeadlineUrgency urgency = item.deadlineUrgency();
        TextView countdown = holder.deadlineCountdown;
        if (urgency == TaskListItem.DeadlineUrgency.NONE) {
            countdown.setVisibility(View.GONE);
            countdown.setContentDescription(null);
            return;
        }

        if (urgency == TaskListItem.DeadlineUrgency.OVERDUE) {
            countdown.setText(R.string.task_deadline_overdue_label);
            countdown.setTextColor(colorDeadlineOverdue);
            countdown.setContentDescription(context.getString(R.string.task_deadline_overdue_content_description));
        } else if (urgency == TaskListItem.DeadlineUrgency.TODAY) {
            countdown.setText(R.string.task_deadline_today_label);
            countdown.setTextColor(colorDeadlineSoon);
            countdown.setContentDescription(context.getString(R.string.task_deadline_today_content_description));
        } else {
            long daysUntil = item.daysUntilDeadline();
            countdown.setText(context.getString(R.string.task_deadline_in_days_label, daysUntil));
            countdown.setTextColor(urgency == TaskListItem.DeadlineUrgency.SOON
                    ? colorDeadlineSoon : colorDeadlineFuture);
            countdown.setContentDescription(context.getString(R.string.task_deadline_in_days_content_description, daysUntil));
        }
        countdown.setVisibility(View.VISIBLE);
    }

    /**
     * Visual tiers for the streak display. Each tier has an inclusive upper bound on streak count:
     * COMMON = 1–3, RARE = 4–7, EPIC = 8–14, LEGENDARY = 15+.
     * The tier determines the text colour and the screen-reader label (e.g. "legendary streak").
     */
    enum StreakTier {
        COMMON(3, R.color.task_streak_common, R.string.task_streak_tier_common),
        RARE(7, R.color.task_streak_rare, R.string.task_streak_tier_rare),
        EPIC(14, R.color.task_streak_epic, R.string.task_streak_tier_epic),
        LEGENDARY(Integer.MAX_VALUE, R.color.task_streak_legendary, R.string.task_streak_tier_legendary);

        final int maxStreak;
        final int colorRes;
        final int labelRes;

        StreakTier(int maxStreak, int colorRes, int labelRes) {
            this.maxStreak = maxStreak;
            this.colorRes = colorRes;
            this.labelRes = labelRes;
        }

        static StreakTier forStreak(int streak) {
            for (StreakTier tier : values()) {
                if (streak <= tier.maxStreak) return tier;
            }
            return LEGENDARY;
        }
    }

    /** Shows the streak badge (e.g. "5x") with tier-based coloring, or hides it if streak is 0. */
    private void bindStreak(TaskRowViewHolder holder, TaskListItem item) {
        if (item.streak <= 0) {
            holder.streakDisplay.setVisibility(View.GONE);
            holder.streakDisplay.setContentDescription(null);
            return;
        }

        Context context = holder.itemView.getContext();
        StreakTier tier = StreakTier.forStreak(item.streak);
        holder.streakDisplay.setText(context.getString(R.string.task_streak_display, item.streak));
        holder.streakDisplay.setTextColor(streakTierColors[tier.ordinal()]);
        holder.streakDisplay.setContentDescription(
                context.getString(
                        R.string.task_streak_content_description,
                        item.streak,
                        context.getString(tier.labelRes)));
        holder.streakDisplay.setVisibility(View.VISIBLE);
    }

    /** Sets the row background and checkbox tint based on task state (not started / in-progress / completed). */
    private void bindProgressState(TaskRowViewHolder holder, TaskListItem item) {
        holder.checkBox.animate().cancel();
        holder.checkBox.setScaleX(1f);
        holder.checkBox.setScaleY(1f);
        if (holder.completionAnimator != null) {
            holder.completionAnimator.cancel();
            holder.completionAnimator = null;
        }

        if (item.completed) {
            applyStateBackground(holder.root, colorCompletedBg);
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(colorCompletedCheckboxTint));
            ViewCompat.setStateDescription(holder.itemView, null);
        } else if (item.inProgress) {
            applyStateBackground(holder.root, colorInProgressBg);
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(colorInProgressCheckboxTint));
            Context context = holder.itemView.getContext();
            ViewCompat.setStateDescription(holder.itemView, context.getString(R.string.task_in_progress_state_description));
        } else {
            holder.checkBox.setButtonTintList(null);
            ViewCompat.setStateDescription(holder.itemView, null);
        }
    }

    /** Routes to either checkbox controls (standard tasks) or progress +/- controls (goal-based tasks). */
    private void bindCompletionMode(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        if (item.hasProgressTarget()) {
            bindProgressControls(holder, item, viewSlot);
        } else {
            bindCheckboxControls(holder, item, viewSlot);
        }
    }

    private void bindCheckboxControls(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        holder.checkBox.setVisibility(View.VISIBLE);
        holder.progressContainer.setVisibility(View.GONE);
        holder.checkBox.setOnClickListener(v -> {
            boolean shouldAnimateCompletion = interactionsEnabled && item.slotId != null && !item.completed;
            if (shouldAnimateCompletion) {
                animateCompletion(holder, item);
            }
            actions.onCheck.accept(viewSlot);
        });
        holder.checkBox.setChecked(item.completed);
        // Two-phase checkoff: the checkbox is disabled once completed (second tap already recorded
        // slot.realEnd in CheckOffTaskUseCase). Unscheduled slots (slotId == null) cannot be
        // checked off at all — they have no execution window to mark complete.
        boolean checkable = !item.completed && item.slotId != null && interactionsEnabled;
        holder.checkBox.setEnabled(checkable);
        holder.checkBox.setAlpha(interactionsEnabled ? ALPHA_ENABLED : ALPHA_DISABLED);
    }

    private void bindProgressControls(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        Context context = holder.itemView.getContext();
        holder.checkBox.setVisibility(View.GONE);
        holder.checkBox.setOnClickListener(null);
        holder.progressContainer.setVisibility(View.VISIBLE);

        int current = Math.max(0, item.progressCurrent);
        int target = Math.max(0, item.progressTarget);
        String unit = item.progressUnit == null ? "" : item.progressUnit;
        holder.progressText.setText(context.getString(R.string.task_progress_display, current, target, unit));

        boolean canDecrease = interactionsEnabled && current > 0;
        boolean canIncrease = interactionsEnabled && current < target;

        applyProgressButtonState(holder.progressMinus, canDecrease);
        applyProgressButtonState(holder.progressPlus, canIncrease);
        holder.progressText.setTextColor(interactionsEnabled ? colorProgressText : colorProgressTextDisabled);

        holder.progressMinus.setOnClickListener(v -> actions.onProgressMinus.accept(viewSlot));
        holder.progressPlus.setOnClickListener(v -> actions.onProgressPlus.accept(viewSlot));
    }

    private void applyProgressButtonState(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? ALPHA_ENABLED : ALPHA_DISABLED);
        button.setImageTintList(ColorStateList.valueOf(
                enabled ? colorProgressButtonTint : colorProgressButtonTintDisabled));
    }

    /** Updates the timer button icon (play/pause) and its accessibility description. */
    private void bindTimerState(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        holder.timerButton.setImageResource(item.inProgress
                ? R.drawable.ic_pause_24
                : R.drawable.ic_play_24);
        holder.timerButton.setContentDescription(context.getString(
                item.inProgress ? R.string.task_timer_stop : R.string.task_timer_start));
        ViewCompat.setStateDescription(holder.timerButton, context.getString(
                item.inProgress ? R.string.task_timer_running : R.string.task_timer_stopped));
    }

    /** Wires click/long-click handlers for timer, edit, and title; disabled when viewing non-today dates. */
    private void bindInteractions(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        boolean timerEligible = item.slotId != null && !item.completed;
        boolean timerEnabled = interactionsEnabled && timerEligible;
        holder.timerButton.setEnabled(timerEnabled);

        holder.title.setOnClickListener(v -> showDescriptionPopup(v, item));

        holder.itemView.setOnLongClickListener(interactionsEnabled
                ? v -> { actions.onEdit.accept(viewSlot); return true; }
                : null);
        holder.timerButton.setOnClickListener(interactionsEnabled
                ? v -> actions.onTimerToggle.accept(viewSlot)
                : null);
        holder.timerButton.setAlpha(interactionsEnabled && timerEligible ? ALPHA_ENABLED : ALPHA_DISABLED);
        holder.editButton.setOnClickListener(interactionsEnabled
                ? v -> actions.onEdit.accept(viewSlot)
                : null);
        holder.editButton.setAlpha(interactionsEnabled ? ALPHA_ENABLED : ALPHA_DISABLED);
    }

    private void showDescriptionPopup(View view, TaskListItem item) {
        if (!(view.getContext() instanceof FragmentActivity activity)) {
            return;
        }
        TaskDescriptionDialog.newInstance(item.title, item.description)
                .show(activity.getSupportFragmentManager(), TaskDescriptionDialog.TAG);
    }

    /** Plays a checkbox scale-bounce and background flash animation on checkoff. */
    private void animateCompletion(TaskRowViewHolder holder, TaskListItem item) {
        holder.checkBox.animate().cancel();
        holder.checkBox.setScaleX(1f);
        holder.checkBox.setScaleY(1f);
        holder.checkBox.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(CHECKBOX_SCALE_DURATION_MS)
                .withEndAction(() -> holder.checkBox.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(CHECKBOX_SCALE_DURATION_MS)
                        .start())
                .start();

        if (holder.completionAnimator != null) {
            holder.completionAnimator.cancel();
        }

        // item.inProgress reflects the PRE-toggle state. The animation previews the POST-toggle
        // state: if currently in-progress, the next tap completes it; if not yet started, the
        // next tap starts it.
        int finalColor = item.inProgress ? colorCompletedBg : colorInProgressBg;
        GradientDrawable animDrawable = createRowBackground();
        holder.root.setBackground(animDrawable);
        holder.completionAnimator = ValueAnimator.ofArgb(colorCompletionFlash, finalColor);
        holder.completionAnimator.setDuration(COMPLETION_FLASH_DURATION_MS);
        holder.completionAnimator.addUpdateListener(animation ->
                animDrawable.setColor((int) animation.getAnimatedValue()));
        holder.completionAnimator.start();
    }

    /**
     * Creates a GradientDrawable with the standard row shape (corner radius + outline stroke).
     * Uses cached dimension/color values to avoid per-call resource lookups.
     */
    private GradientDrawable createRowBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(rowCornerRadius);
        drawable.setStroke(rowStrokeWidth, colorOutlineSemi);
        return drawable;
    }

    /**
     * Sets a rounded-corner background with the given fill color, preserving the row's
     * visual shape (corner radius + outline stroke) that would be lost by setBackgroundColor().
     */
    private void applyStateBackground(View view, int color) {
        GradientDrawable drawable = createRowBackground();
        drawable.setColor(color);
        view.setBackground(drawable);
    }

    public void setList(List<ViewSlot> viewSlots) {
        this.viewSlots = viewSlots;
        notifyDataSetChanged();
    }

    public void setInteractionsEnabled(boolean enabled) {
        if (this.interactionsEnabled != enabled) {
            this.interactionsEnabled = enabled;
            notifyDataSetChanged();
        }
    }

    public void setManageMode(boolean manageMode) {
        this.manageMode = manageMode;
        notifyDataSetChanged();
    }
}
