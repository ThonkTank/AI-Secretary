package com.autosecretary.features.task.ui.list;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import com.autosecretary.features.task.ui.list.state.ViewSlotList.ViewSlot;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ListRowAdapter extends RecyclerView.Adapter<ListRowAdapter.TaskRowViewHolder> {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int STREAK_COMMON_MAX = 3;
    private static final int STREAK_RARE_MAX = 7;
    private static final int STREAK_EPIC_MAX = 14;
    private static final long CHECKBOX_SCALE_DURATION_MS = 100L;
    private static final long COMPLETION_FLASH_DURATION_MS = 300L;

    private List<ViewSlot> viewSlots;
    private final TaskRowActions actions;
    private boolean interactionsEnabled = true;
    private boolean manageMode = false;

    public ListRowAdapter(List<ViewSlot> viewSlots, TaskRowActions actions) {
        this.viewSlots = viewSlots;
        this.actions = actions;
    }

    public static class TaskRowActions {
        final Consumer<ViewSlot> onCheck;
        final Consumer<ViewSlot> onEdit;
        final Consumer<ViewSlot> onTimerToggle;
        final Consumer<ViewSlot> onProgressPlus;
        final Consumer<ViewSlot> onProgressMinus;
        final Consumer<ViewSlot> onToggleExpand;
        final Function<ViewSlot, Boolean> isExpanded;

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
        TaskListItem item = viewSlot.item;

        holder.title.setText(item.title);
        holder.itemView.setContentDescription(item.title);

        bindIndentation(holder, viewSlot.depth);
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

    private void bindIndentation(TaskRowViewHolder holder, int depth) {
        int step = holder.itemView.getContext().getResources().getDimensionPixelSize(R.dimen.indent_step);
        holder.itemView.setPaddingRelative(
                step * depth,
                holder.itemView.getPaddingTop(),
                holder.itemView.getPaddingEnd(),
                holder.itemView.getPaddingBottom());
    }

    private void bindTaskRow(TaskRowViewHolder holder) {
        holder.root.setBackgroundResource(R.drawable.bg_task_row);
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

    private void bindExpandToggle(TaskRowViewHolder holder, ViewSlot viewSlot) {
        if (!manageMode || !viewSlot.hasChildren()) {
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

    private void bindCalendarEventRow(TaskRowViewHolder holder) {
        Context context = holder.itemView.getContext();
        holder.root.setBackgroundResource(R.drawable.bg_calendar_row);
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

    private void bindDeadline(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        TaskListItem.DeadlineUrgency deadlineUrgency = item.deadlineUrgency();
        if (deadlineUrgency == TaskListItem.DeadlineUrgency.NONE) {
            holder.deadlineCountdown.setVisibility(View.GONE);
            holder.deadlineCountdown.setContentDescription(null);
            return;
        }

        long daysUntil = item.daysUntilDeadline();
        if (deadlineUrgency == TaskListItem.DeadlineUrgency.OVERDUE) {
            holder.deadlineCountdown.setText(R.string.task_deadline_overdue_label);
            holder.deadlineCountdown.setTextColor(ContextCompat.getColor(context, R.color.task_deadline_overdue));
            holder.deadlineCountdown.setContentDescription(context.getString(R.string.task_deadline_overdue_content_description));
        } else if (deadlineUrgency == TaskListItem.DeadlineUrgency.TODAY) {
            holder.deadlineCountdown.setText(R.string.task_deadline_today_label);
            holder.deadlineCountdown.setTextColor(ContextCompat.getColor(context, R.color.task_deadline_soon));
            holder.deadlineCountdown.setContentDescription(context.getString(R.string.task_deadline_today_content_description));
        } else {
            holder.deadlineCountdown.setText(context.getString(R.string.task_deadline_in_days_label, daysUntil));
            int countdownColor = deadlineUrgency == TaskListItem.DeadlineUrgency.SOON
                    ? R.color.task_deadline_soon
                    : R.color.task_deadline_future;
            holder.deadlineCountdown.setTextColor(ContextCompat.getColor(context, countdownColor));
            holder.deadlineCountdown.setContentDescription(context.getString(R.string.task_deadline_in_days_content_description, daysUntil));
        }
        holder.deadlineCountdown.setVisibility(View.VISIBLE);
    }

    private void bindStreak(TaskRowViewHolder holder, TaskListItem item) {
        if (item.streak > 0) {
            Context context = holder.itemView.getContext();
            int streakColor;
            int rarityLabel;
            if (item.streak <= STREAK_COMMON_MAX) {
                streakColor = R.color.task_streak_common;
                rarityLabel = R.string.task_streak_tier_common;
            } else if (item.streak <= STREAK_RARE_MAX) {
                streakColor = R.color.task_streak_rare;
                rarityLabel = R.string.task_streak_tier_rare;
            } else if (item.streak <= STREAK_EPIC_MAX) {
                streakColor = R.color.task_streak_epic;
                rarityLabel = R.string.task_streak_tier_epic;
            } else {
                streakColor = R.color.task_streak_legendary;
                rarityLabel = R.string.task_streak_tier_legendary;
            }

            holder.streakDisplay.setText(context.getString(R.string.task_streak_display, item.streak));
            holder.streakDisplay.setTextColor(ContextCompat.getColor(context, streakColor));
            holder.streakDisplay.setContentDescription(
                    context.getString(
                            R.string.task_streak_content_description,
                            item.streak,
                            context.getString(rarityLabel)));
            holder.streakDisplay.setVisibility(View.VISIBLE);
        } else {
            holder.streakDisplay.setVisibility(View.GONE);
            holder.streakDisplay.setContentDescription(null);
        }
    }

    private void bindProgressState(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        if (holder.completionAnimator != null) {
            holder.completionAnimator.cancel();
            holder.completionAnimator = null;
        }

        if (item.completed) {
            holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.task_completed_background));
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.task_completed_checkbox_tint)));
            ViewCompat.setStateDescription(holder.itemView, null);
        } else if (item.inProgress) {
            holder.root.setBackgroundColor(ContextCompat.getColor(context, R.color.task_in_progress_background));
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.task_in_progress_checkbox_tint)));
            ViewCompat.setStateDescription(holder.itemView, context.getString(R.string.task_in_progress_state_description));
        } else {
            holder.checkBox.setButtonTintList(null);
            ViewCompat.setStateDescription(holder.itemView, null);
        }
    }

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
        boolean checkable = !item.completed && item.slotId != null && interactionsEnabled;
        holder.checkBox.setEnabled(checkable);
        holder.checkBox.setAlpha(interactionsEnabled ? 1.0f : 0.4f);
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

        applyProgressButtonState(context, holder.progressMinus, canDecrease);
        applyProgressButtonState(context, holder.progressPlus, canIncrease);
        holder.progressText.setTextColor(ContextCompat.getColor(context,
                interactionsEnabled ? R.color.task_progress_text : R.color.task_progress_text_disabled));

        holder.progressMinus.setOnClickListener(v -> actions.onProgressMinus.accept(viewSlot));
        holder.progressPlus.setOnClickListener(v -> actions.onProgressPlus.accept(viewSlot));
    }

    private void applyProgressButtonState(Context context, ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.4f);
        button.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                enabled ? R.color.task_progress_button_tint : R.color.task_progress_button_tint_disabled)));
    }

    private void bindTimerState(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        holder.timerButton.setImageResource(item.inProgress
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
        holder.timerButton.setContentDescription(context.getString(
                item.inProgress ? R.string.task_timer_stop : R.string.task_timer_start));
        ViewCompat.setStateDescription(holder.timerButton, context.getString(
                item.inProgress ? R.string.task_timer_running : R.string.task_timer_stopped));
    }

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
        holder.timerButton.setAlpha(interactionsEnabled && timerEligible ? 1.0f : 0.4f);
        holder.editButton.setOnClickListener(interactionsEnabled
                ? v -> actions.onEdit.accept(viewSlot)
                : null);
        holder.editButton.setAlpha(interactionsEnabled ? 1.0f : 0.4f);
    }

    private void showDescriptionPopup(View view, TaskListItem item) {
        Context context = view.getContext();
        if (!(context instanceof FragmentActivity)) {
            return;
        }

        FragmentActivity activity = (FragmentActivity) context;
        TaskDescriptionDialogFragment.newInstance(item.title, item.description)
                .show(activity.getSupportFragmentManager(), TaskDescriptionDialogFragment.TAG);
    }

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

        Context context = holder.itemView.getContext();
        int flashColor = ContextCompat.getColor(context, R.color.task_completion_flash);
        int finalColor = resolveCompletedStateBackground(item, context);
        holder.completionAnimator = ValueAnimator.ofArgb(flashColor, finalColor);
        holder.completionAnimator.setDuration(COMPLETION_FLASH_DURATION_MS);
        holder.completionAnimator.addUpdateListener(animation ->
                holder.root.setBackgroundColor((int) animation.getAnimatedValue()));
        holder.completionAnimator.start();
    }

    private int resolveCompletedStateBackground(TaskListItem item, Context context) {
        if (item.inProgress) {
            return ContextCompat.getColor(context, R.color.task_in_progress_background);
        }
        return ContextCompat.getColor(context, R.color.task_completed_background);
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
