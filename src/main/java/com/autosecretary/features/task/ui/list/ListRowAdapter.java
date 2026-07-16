package com.autosecretary.features.task.ui.list;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.features.task.application.listmodel.TaskListItem;
import com.autosecretary.features.task.ui.list.state.ViewSlot;
import com.autosecretary.shared.ui.ColorUtil;
import com.autosecretary.shared.ui.UiConstants;

import com.autosecretary.shared.DateFormatters;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * RecyclerView adapter for the task list. Renders two view types:
 * <ul>
 *   <li><b>Row</b> ({@code task_row_item.xml}) — task and calendar-event rows in the agenda
 *       layout: an optional time rail (start over end) + state line on the left (Checklist
 *       mode) next to an interactive two-line card (title line + metadata line). Task rows
 *       show checkbox/state-button or a compact progress stepper; calendar rows are
 *       read-only with a "Kalender" chip.</li>
 *   <li><b>Header</b> ({@code task_row_header_item.xml}) — slim card-free category section
 *       headers in Manage mode; the whole row toggles the category's expansion.</li>
 * </ul>
 *
 * <p>The card ({@code @id/TaskCard}) is the single interaction, accessibility, and animation
 * target of a row: click/long-press, the merged content description, the state background
 * (in progress / completed), and the completion flash all live on it. The rail stays neutral
 * and is excluded from accessibility (its times are part of the merged description).
 *
 * <p>The adapter delegates all user interactions (checkoff, edit, progress) to
 * {@link TaskRowActions} callbacks provided by {@link TaskListFragment}. It does not talk
 * to the ViewModel directly.
 *
 * <p>{@link #setInteractionsEnabled(boolean)} is called with {@code false} when the list is
 * read-only (a day other than today in the day-scoped modes).
 */
public class ListRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_ROW = 0;
    private static final int VIEW_TYPE_HEADER = 1;
    private static final long CHECKBOX_SCALE_DURATION_MS = 100L;
    private static final long COMPLETION_FLASH_DURATION_MS = 300L;
    private List<ViewSlot> viewSlots;
    private final TaskRowActions actions;
    /** False when the list is read-only (past/future day); disables checkboxes, steppers, editing. */
    private boolean interactionsEnabled = true;
    /** Active display mode; drives the time rail, Manage-only affordances, and the deadline bar. */
    private ListConfig displayMode = ListConfig.CHECKLIST;
    /** True in Manage mode; enables the expand/collapse toggle on parent task rows. */
    private boolean manageMode = false;

    // Cached resource values — resolved once in onAttachedToRecyclerView to avoid per-bind lookups.
    private int indentStepPx;
    private int leadingControlWidthPx;
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
    private int colorStateLineOpen;
    private int colorStateLineInProgress;
    private int colorStateLineCompleted;
    private int colorStateLineCalendar;
    private int[] streakTierColors;

    public ListRowAdapter(List<ViewSlot> viewSlots, TaskRowActions actions) {
        this.viewSlots = viewSlots;
        this.actions = actions;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        Context ctx = recyclerView.getContext();
        android.content.res.Resources res = ctx.getResources();
        indentStepPx = res.getDimensionPixelSize(R.dimen.task_indent_step);
        leadingControlWidthPx = res.getDimensionPixelSize(R.dimen.touch_target_min);
        rowCornerRadius = res.getDimension(R.dimen.corner_radius_sm);
        rowStrokeWidth = (int) res.getDimension(R.dimen.editor_input_stroke_width);
        colorOutlineSemi = ContextCompat.getColor(ctx, R.color.color_outline_semi);
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
        colorStateLineOpen = ContextCompat.getColor(ctx, R.color.color_outline);
        colorStateLineInProgress = ContextCompat.getColor(ctx, R.color.color_primary);
        colorStateLineCompleted = ContextCompat.getColor(ctx, R.color.color_primary_variant);
        colorStateLineCalendar = ContextCompat.getColor(ctx, R.color.task_calendar_outline);
        streakTierColors = new int[StreakTier.values().length];
        for (StreakTier tier : StreakTier.values()) {
            streakTierColors[tier.ordinal()] = ContextCompat.getColor(ctx, tier.colorRes);
        }
    }

    public static class TaskRowActions {
        private final Consumer<ViewSlot> onCheck;
        private final Consumer<ViewSlot> onUndo;
        private final Consumer<ViewSlot> onEdit;
        private final Consumer<ViewSlot> onProgressPlus;
        private final Consumer<ViewSlot> onProgressMinus;
        private final Consumer<ViewSlot> onToggleExpand;
        private final Function<ViewSlot, Boolean> isExpanded;

        public TaskRowActions(Consumer<ViewSlot> onCheck,
                              Consumer<ViewSlot> onUndo,
                              Consumer<ViewSlot> onEdit,
                              Consumer<ViewSlot> onProgressPlus,
                              Consumer<ViewSlot> onProgressMinus,
                              Consumer<ViewSlot> onToggleExpand,
                              Function<ViewSlot, Boolean> isExpanded) {
            this.onCheck = onCheck;
            this.onUndo = onUndo;
            this.onEdit = onEdit;
            this.onProgressPlus = onProgressPlus;
            this.onProgressMinus = onProgressMinus;
            this.onToggleExpand = onToggleExpand;
            this.isExpanded = isExpanded;
        }
    }

    static class TaskRowViewHolder extends RecyclerView.ViewHolder {
        /** The interactive card — click, a11y, state background, and flash animation target. */
        LinearLayout card;
        View timeRail;
        TextView timeStart;
        TextView timeEnd;
        View stateLine;
        TextView title;
        TextView goalIcon;
        CheckBox checkBox;
        ImageButton stateButton;
        View progressContainer;
        ImageButton progressMinus;
        ImageButton progressPlus;
        TextView progressText;
        View metadataLine;
        TextView metaTimeRange;
        ProgressBar deadlineBar;
        TextView deadlineCountdown;
        TextView streakDisplay;
        TextView expandToggle;
        TextView calendarChip;
        ValueAnimator completionAnimator;

        TaskRowViewHolder(View taskRow) {
            super(taskRow);
            this.card = taskRow.findViewById(R.id.TaskCard);
            this.timeRail = taskRow.findViewById(R.id.TimeRail);
            this.timeStart = taskRow.findViewById(R.id.TimeStart);
            this.timeEnd = taskRow.findViewById(R.id.TimeEnd);
            this.stateLine = taskRow.findViewById(R.id.StateLine);
            this.title = taskRow.findViewById(R.id.TaskTitle);
            this.goalIcon = taskRow.findViewById(R.id.GoalIcon);
            this.checkBox = taskRow.findViewById(R.id.TaskCheckBox);
            this.stateButton = taskRow.findViewById(R.id.TaskStateButton);
            this.progressContainer = taskRow.findViewById(R.id.ProgressContainer);
            this.progressMinus = taskRow.findViewById(R.id.ProgressMinusButton);
            this.progressPlus = taskRow.findViewById(R.id.ProgressPlusButton);
            this.progressText = taskRow.findViewById(R.id.ProgressText);
            this.metadataLine = taskRow.findViewById(R.id.MetadataLine);
            this.metaTimeRange = taskRow.findViewById(R.id.MetaTimeRange);
            this.deadlineBar = taskRow.findViewById(R.id.DeadlineProgressBar);
            this.deadlineCountdown = taskRow.findViewById(R.id.DeadlineCountdown);
            this.streakDisplay = taskRow.findViewById(R.id.StreakDisplay);
            this.expandToggle = taskRow.findViewById(R.id.ExpandToggle);
            this.calendarChip = taskRow.findViewById(R.id.CalendarChip);
        }
    }

    static class HeaderRowViewHolder extends RecyclerView.ViewHolder {
        TextView icon;
        TextView title;
        TextView chevron;

        HeaderRowViewHolder(View headerRow) {
            super(headerRow);
            this.icon = headerRow.findViewById(R.id.HeaderIcon);
            this.title = headerRow.findViewById(R.id.HeaderTitle);
            this.chevron = headerRow.findViewById(R.id.HeaderChevron);
        }
    }

    @Override
    public int getItemCount() {
        return viewSlots.size();
    }

    @Override
    public int getItemViewType(int position) {
        return viewSlots.get(position).getItem().isCategoryHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_ROW;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderRowViewHolder(inflater.inflate(R.layout.task_row_header_item, parent, false));
        }
        return new TaskRowViewHolder(inflater.inflate(R.layout.task_row_item, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        ViewSlot viewSlot = viewSlots.get(position);
        TaskListItem item = viewSlot.getItem();
        bindIndentation(viewHolder.itemView, viewSlot.getDepth());

        if (viewHolder instanceof HeaderRowViewHolder headerHolder) {
            bindCategoryHeaderRow(headerHolder, item, viewSlot);
            return;
        }

        TaskRowViewHolder holder = (TaskRowViewHolder) viewHolder;
        holder.title.setText(item.title);
        bindTimeRail(holder, item);

        if (item.isCalendarEvent()) {
            bindCalendarEventRow(holder, item);
            return;
        }

        bindTaskRow(holder);
        bindGoalAppearance(holder, item);
        bindMetaTime(holder, item);
        bindDeadline(holder, item);
        bindDeadlineBar(holder, item);
        bindStreak(holder, item);
        bindProgressState(holder, item);
        bindCompletionMode(holder, item, viewSlot);
        syncMetadataLine(holder);
        bindExpandToggle(holder, viewSlot);
        bindInteractions(holder, item, viewSlot);
        holder.card.setContentDescription(buildRowContentDescription(holder.itemView.getContext(), item));
    }

    /** Sets left padding based on tree depth for parent-child indentation in Manage mode. */
    private void bindIndentation(View rowRoot, int depth) {
        rowRoot.setPaddingRelative(
                indentStepPx * depth,
                rowRoot.getPaddingTop(),
                rowRoot.getPaddingEnd(),
                rowRoot.getPaddingBottom());
    }

    /**
     * Shows the agenda time rail (start over end) with the state line in Checklist mode;
     * hidden in all other modes (their time range renders in the metadata line instead).
     */
    private void bindTimeRail(TaskRowViewHolder holder, TaskListItem item) {
        if (displayMode != ListConfig.CHECKLIST) {
            holder.timeRail.setVisibility(View.GONE);
            holder.stateLine.setVisibility(View.GONE);
            return;
        }
        Context context = holder.itemView.getContext();
        holder.timeStart.setText(formatTimeOrFallback(context, item.start, R.string.task_time_fallback_start));
        holder.timeEnd.setText(formatTimeOrFallback(context, item.end, R.string.task_time_fallback_end));
        holder.timeRail.setVisibility(View.VISIBLE);

        int lineColor;
        if (item.isCalendarEvent()) {
            lineColor = colorStateLineCalendar;
        } else if (item.completed) {
            lineColor = colorStateLineCompleted;
        } else if (item.inProgress) {
            lineColor = colorStateLineInProgress;
        } else {
            lineColor = colorStateLineOpen;
        }
        ViewCompat.setBackgroundTintList(holder.stateLine, ColorStateList.valueOf(lineColor));
        holder.stateLine.setVisibility(View.VISIBLE);
    }

    /** Shows the time range in the metadata line for scheduled rows outside Checklist mode. */
    private void bindMetaTime(TaskRowViewHolder holder, TaskListItem item) {
        if (displayMode == ListConfig.CHECKLIST || item.start == null) {
            holder.metaTimeRange.setVisibility(View.GONE);
            return;
        }
        Context context = holder.itemView.getContext();
        holder.metaTimeRange.setText(context.getString(R.string.task_row_time_range_display,
                formatTimeOrFallback(context, item.start, R.string.task_time_fallback_start),
                formatTimeOrFallback(context, item.end, R.string.task_time_fallback_end)));
        holder.metaTimeRange.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the metadata line only when at least one of its children is visible, indented to
     * align with the title column when a leading control (checkbox/state button) is present.
     * Must run after the completion-mode bind, which decides the leading control's visibility.
     */
    private void syncMetadataLine(TaskRowViewHolder holder) {
        boolean anyVisible = holder.goalIcon.getVisibility() == View.VISIBLE
                || holder.metaTimeRange.getVisibility() == View.VISIBLE
                || holder.deadlineBar.getVisibility() == View.VISIBLE
                || holder.deadlineCountdown.getVisibility() == View.VISIBLE
                || holder.streakDisplay.getVisibility() == View.VISIBLE
                || holder.calendarChip.getVisibility() == View.VISIBLE;
        boolean hasLeadingControl = holder.checkBox.getVisibility() == View.VISIBLE
                || holder.stateButton.getVisibility() == View.VISIBLE;
        holder.metadataLine.setPaddingRelative(
                hasLeadingControl ? leadingControlWidthPx : 0,
                holder.metadataLine.getPaddingTop(),
                holder.metadataLine.getPaddingEnd(),
                holder.metadataLine.getPaddingBottom());
        holder.metadataLine.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
    }

    /** Resets all card views/listeners to the task-row defaults (as opposed to calendar-row). */
    private void bindTaskRow(TaskRowViewHolder holder) {
        holder.card.setBackgroundResource(R.drawable.bg_row);
        holder.checkBox.setVisibility(View.VISIBLE);
        holder.stateButton.setVisibility(View.GONE);
        holder.expandToggle.setVisibility(View.GONE);
        holder.calendarChip.setVisibility(View.GONE);
        holder.stateButton.setOnClickListener(null);
        holder.card.setOnClickListener(null);
        holder.card.setOnLongClickListener(null);
    }

    /** Shows the goal emoji/icon with its color if the task has a progress target and a goal icon set. */
    private void bindGoalAppearance(TaskRowViewHolder holder, TaskListItem item) {
        if (!item.hasProgressTarget() || item.goalIcon == null || item.goalIcon.trim().isEmpty()) {
            holder.goalIcon.setVisibility(View.GONE);
            return;
        }

        holder.goalIcon.setText(item.goalIcon);
        holder.goalIcon.setTextColor(ColorUtil.parseColorSafe(item.goalColorHex, holder.title.getCurrentTextColor()));
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

    /**
     * Builds a composite content description for a task row so screen readers announce the full
     * row in a single pass: title, time range, optional deadline, optional streak, and current state.
     *
     * <p>Example output: "Sport, 10:00 – 11:00, Heute fällig, Streak 5, In Bearbeitung"
     */
    private String buildRowContentDescription(Context context, TaskListItem item) {
        String timeRange = context.getString(R.string.task_row_time_range,
                formatTimeOrFallback(context, item.start, R.string.task_time_fallback_start),
                formatTimeOrFallback(context, item.end, R.string.task_time_fallback_end));

        String deadlineSegment = "";
        TaskListItem.DeadlineUrgency urgency = item.deadlineUrgency();
        if (urgency != TaskListItem.DeadlineUrgency.NONE) {
            String deadlineDesc;
            if (urgency == TaskListItem.DeadlineUrgency.OVERDUE) {
                deadlineDesc = context.getString(R.string.task_deadline_overdue_content_description);
            } else if (urgency == TaskListItem.DeadlineUrgency.TODAY) {
                deadlineDesc = context.getString(R.string.task_deadline_today_content_description);
            } else {
                deadlineDesc = context.getString(R.string.task_deadline_in_days_content_description,
                        item.daysUntilDeadline());
            }
            deadlineSegment = context.getString(R.string.task_row_deadline_segment, deadlineDesc);
        }

        String streakSegment = item.streak > 0
                ? context.getString(R.string.task_row_streak_segment, item.streak)
                : "";

        String stateLabel;
        if (item.completed) {
            stateLabel = context.getString(R.string.task_row_state_completed);
        } else if (item.inProgress) {
            stateLabel = context.getString(R.string.task_row_state_in_progress);
        } else {
            stateLabel = context.getString(R.string.task_row_state_pending);
        }

        return context.getString(R.string.task_row_content_description,
                item.title, timeRange, deadlineSegment, streakSegment, stateLabel);
    }

    /**
     * Configures a header holder as a category group header (Manage mode): category icon + name
     * and an expand/collapse chevron; the whole row toggles the category's expansion.
     */
    private void bindCategoryHeaderRow(HeaderRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        Context context = holder.itemView.getContext();
        boolean hasIcon = item.categoryIcon != null && !item.categoryIcon.trim().isEmpty();
        holder.icon.setVisibility(hasIcon ? View.VISIBLE : View.GONE);
        if (hasIcon) {
            holder.icon.setText(item.categoryIcon);
            holder.icon.setTextColor(
                    ColorUtil.parseColorSafe(item.categoryColorHex, holder.title.getCurrentTextColor()));
        }
        holder.title.setText(item.title);

        boolean expanded = actions.isExpanded.apply(viewSlot);
        holder.chevron.setText(expanded ? R.string.task_row_toggle_expanded : R.string.task_row_toggle_collapsed);
        holder.itemView.setOnClickListener(v -> actions.onToggleExpand.accept(viewSlot));
        holder.itemView.setContentDescription(item.title);
        ViewCompat.setStateDescription(holder.itemView, context.getString(
                expanded ? R.string.task_row_collapse_children : R.string.task_row_expand_children));
    }

    /** Configures the row as a read-only calendar event: muted card, "Kalender" chip, no controls. */
    private void bindCalendarEventRow(TaskRowViewHolder holder, TaskListItem item) {
        Context context = holder.itemView.getContext();
        holder.card.setBackgroundResource(R.drawable.task_bg_calendar_row);
        holder.checkBox.setVisibility(View.GONE);
        holder.stateButton.setVisibility(View.GONE);
        holder.goalIcon.setVisibility(View.GONE);
        holder.progressContainer.setVisibility(View.GONE);
        holder.deadlineBar.setVisibility(View.GONE);
        holder.deadlineCountdown.setVisibility(View.GONE);
        holder.streakDisplay.setVisibility(View.GONE);
        holder.expandToggle.setVisibility(View.GONE);
        holder.calendarChip.setVisibility(View.VISIBLE);
        holder.calendarChip.setText(context.getString(R.string.task_calendar_label));
        bindMetaTime(holder, item);
        syncMetadataLine(holder);
        holder.checkBox.setOnClickListener(null);
        holder.stateButton.setOnClickListener(null);
        holder.card.setOnLongClickListener(null);
        holder.card.setOnClickListener(null);
        holder.card.setContentDescription(item.title);
        ViewCompat.setStateDescription(holder.card, context.getString(R.string.task_calendar_state_description));
    }

    private static String formatTimeOrFallback(Context context, java.time.LocalTime time, int fallbackRes) {
        return time != null ? time.format(DateFormatters.TIME_HH_MM) : context.getString(fallbackRes);
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
     * Shows the remaining-time bar in Deadline mode: the filled fraction is the elapsed share of
     * the created→deadline span, tinted by urgency (full and red when overdue). Hidden in all
     * other modes; the textual "in Nd" label from {@link #bindDeadline} stays alongside.
     */
    private void bindDeadlineBar(TaskRowViewHolder holder, TaskListItem item) {
        ProgressBar bar = holder.deadlineBar;
        if (displayMode != ListConfig.DEADLINE || item.deadline == null) {
            bar.setVisibility(View.GONE);
            bar.setContentDescription(null);
            return;
        }

        Context context = holder.itemView.getContext();
        int percent = item.deadlineElapsedPercent();
        TaskListItem.DeadlineUrgency urgency = item.deadlineUrgency();
        int barColor;
        if (urgency == TaskListItem.DeadlineUrgency.OVERDUE) {
            barColor = colorDeadlineOverdue;
        } else if (urgency == TaskListItem.DeadlineUrgency.TODAY || urgency == TaskListItem.DeadlineUrgency.SOON) {
            barColor = colorDeadlineSoon;
        } else {
            barColor = colorDeadlineFuture;
        }
        bar.setProgress(percent);
        bar.setProgressTintList(ColorStateList.valueOf(barColor));
        bar.setContentDescription(context.getString(R.string.task_deadline_bar_content_description, percent));
        bar.setVisibility(View.VISIBLE);
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
        if (item.streak <= 0 || item.leisure) {
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

    /** Sets the card background and checkbox tint based on task state (not started / in-progress / completed). */
    private void bindProgressState(TaskRowViewHolder holder, TaskListItem item) {
        holder.checkBox.animate().cancel();
        holder.checkBox.setScaleX(1f);
        holder.checkBox.setScaleY(1f);
        if (holder.completionAnimator != null) {
            holder.completionAnimator.cancel();
            holder.completionAnimator = null;
        }

        if (item.completed) {
            applyStateBackground(holder.card, colorCompletedBg);
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(colorCompletedCheckboxTint));
            ViewCompat.setStateDescription(holder.card, null);
        } else if (item.inProgress) {
            applyStateBackground(holder.card, colorInProgressBg);
            holder.checkBox.setButtonTintList(ColorStateList.valueOf(colorInProgressCheckboxTint));
            Context context = holder.itemView.getContext();
            ViewCompat.setStateDescription(holder.card, context.getString(R.string.task_in_progress_state_description));
        } else {
            holder.checkBox.setButtonTintList(null);
            ViewCompat.setStateDescription(holder.card, null);
        }
    }

    /** Routes to either checkbox controls (standard tasks) or progress +/- controls (goal-based tasks). */
    private void bindCompletionMode(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        // Leisure tasks carry no progress metrics, so always use the simple checkbox control.
        if (item.hasProgressTarget() && !item.leisure) {
            bindProgressControls(holder, item, viewSlot);
        } else {
            bindCheckboxControls(holder, item, viewSlot);
        }
    }

    private void bindCheckboxControls(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        Context context = holder.itemView.getContext();
        holder.progressContainer.setVisibility(View.GONE);

        // Slotless items are checkable too: the use case creates an ad-hoc slot for today.
        boolean interactionsAllowed = interactionsEnabled;
        boolean stateButtonMode = item.inProgress || item.completed;

        if (!stateButtonMode) {
            holder.stateButton.setVisibility(View.GONE);
            holder.stateButton.setOnClickListener(null);

            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setContentDescription(context.getString(R.string.task_row_checkbox_start));
            holder.checkBox.setOnClickListener(v -> {
                if (!interactionsAllowed) {
                    return;
                }
                animateCompletion(holder, item);
                actions.onCheck.accept(viewSlot);
            });
            holder.checkBox.setChecked(false);
            holder.checkBox.setEnabled(interactionsAllowed);
            holder.checkBox.setAlpha(interactionsEnabled ? UiConstants.ALPHA_ENABLED : UiConstants.ALPHA_DISABLED);
            return;
        }

        holder.checkBox.setVisibility(View.GONE);
        holder.checkBox.setOnClickListener(null);
        holder.stateButton.setVisibility(View.VISIBLE);
        holder.stateButton.setEnabled(interactionsAllowed);
        holder.stateButton.setAlpha(interactionsEnabled ? UiConstants.ALPHA_ENABLED : UiConstants.ALPHA_DISABLED);

        if (item.inProgress) {
            holder.stateButton.setImageResource(R.drawable.ic_play_24);
            holder.stateButton.setImageTintList(ColorStateList.valueOf(colorInProgressCheckboxTint));
            holder.stateButton.setContentDescription(context.getString(R.string.task_row_state_button_in_progress));
            holder.stateButton.setOnClickListener(v -> {
                if (!interactionsAllowed) {
                    return;
                }
                animateCompletion(holder, item);
                actions.onCheck.accept(viewSlot);
            });
        } else {
            // Completed rows: tapping the check opens the popup, which offers the undo action.
            holder.stateButton.setImageResource(R.drawable.ic_check_24);
            holder.stateButton.setImageTintList(ColorStateList.valueOf(colorCompletedCheckboxTint));
            holder.stateButton.setContentDescription(context.getString(R.string.task_row_state_button_completed));
            holder.stateButton.setOnClickListener(v -> showDescriptionPopup(v, item, viewSlot));
        }
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
        button.setAlpha(enabled ? UiConstants.ALPHA_ENABLED : UiConstants.ALPHA_DISABLED);
        button.setImageTintList(ColorStateList.valueOf(
                enabled ? colorProgressButtonTint : colorProgressButtonTintDisabled));
    }

    /**
     * Wires card interactions: tapping the card opens the description popup (which offers an
     * edit action and, for started/completed rows, a clearly labelled undo action),
     * long-pressing edits directly. Both are gated on {@link #interactionsEnabled} so
     * read-only days stay read-only.
     */
    private void bindInteractions(TaskRowViewHolder holder, TaskListItem item, ViewSlot viewSlot) {
        holder.card.setOnClickListener(v -> showDescriptionPopup(v, item, viewSlot));

        holder.card.setOnLongClickListener(interactionsEnabled
                ? v -> { actions.onEdit.accept(viewSlot); return true; }
                : null);
    }

    private void showDescriptionPopup(View view, TaskListItem item, ViewSlot viewSlot) {
        if (!(view.getContext() instanceof FragmentActivity activity)) {
            return;
        }
        TaskDescriptionDialog dialog = TaskDescriptionDialog.newInstance(item.title, item.description);
        dialog.setOnEdit(interactionsEnabled ? () -> actions.onEdit.accept(viewSlot) : null);
        if (interactionsEnabled && !item.isCalendarEvent() && (item.inProgress || item.completed)) {
            int undoLabelRes = item.completed
                    ? R.string.task_undo_revert_completion
                    : R.string.task_undo_revert_start;
            dialog.setOnUndo(view.getContext().getString(undoLabelRes),
                    () -> actions.onUndo.accept(viewSlot));
        }
        dialog.show(activity.getSupportFragmentManager(), TaskDescriptionDialog.TAG);
    }

    /** Plays a checkbox scale-bounce and card background flash animation on checkoff. */
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
        holder.card.setBackground(animDrawable);
        holder.completionAnimator = ValueAnimator.ofArgb(colorCompletionFlash, finalColor);
        holder.completionAnimator.setDuration(COMPLETION_FLASH_DURATION_MS);
        holder.completionAnimator.addUpdateListener(animation ->
                animDrawable.setColor((int) animation.getAnimatedValue()));
        holder.completionAnimator.start();
    }

    /**
     * Creates a GradientDrawable with the standard card shape (corner radius + outline stroke).
     * Uses cached dimension/color values to avoid per-call resource lookups.
     */
    private GradientDrawable createRowBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(rowCornerRadius);
        drawable.setStroke(rowStrokeWidth, colorOutlineSemi);
        return drawable;
    }

    /**
     * Sets a rounded-corner background with the given fill color, preserving the card's
     * visual shape (corner radius + outline stroke) that would be lost by setBackgroundColor().
     */
    private void applyStateBackground(View view, int color) {
        GradientDrawable drawable = createRowBackground();
        drawable.setColor(color);
        view.setBackground(drawable);
    }

    public void setList(List<ViewSlot> viewSlots) {
        List<ViewSlot> updatedSlots = viewSlots == null ? Collections.emptyList() : viewSlots;
        List<ViewSlot> previousSlots = this.viewSlots;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previousSlots.size();
            }

            @Override
            public int getNewListSize() {
                return updatedSlots.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return stableItemKey(previousSlots.get(oldItemPosition))
                        .equals(stableItemKey(updatedSlots.get(newItemPosition)));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return hasSameRenderedContent(previousSlots.get(oldItemPosition), updatedSlots.get(newItemPosition));
            }
        });
        this.viewSlots = updatedSlots;
        diff.dispatchUpdatesTo(this);
    }

    public void setInteractionsEnabled(boolean enabled) {
        if (this.interactionsEnabled != enabled) {
            this.interactionsEnabled = enabled;
            notifyDataSetChanged();
        }
    }

    void setDisplayMode(ListConfig displayMode) {
        this.displayMode = displayMode;
        this.manageMode = displayMode == ListConfig.MANAGE;
        notifyDataSetChanged();
    }

    private static String stableItemKey(ViewSlot slot) {
        TaskListItem item = slot.getItem();
        if (item.isCategoryHeader()) {
            return "header|" + item.categoryId;
        }
        if (item.slotId != null) {
            return item.slotId;
        }
        if (item.taskId != null) {
            return item.taskId + "|" + item.itemType;
        }
        return item.title + "|" + item.day + "|" + item.start + "|" + item.end;
    }

    private static boolean hasSameRenderedContent(ViewSlot oldSlot, ViewSlot newSlot) {
        TaskListItem oldItem = oldSlot.getItem();
        TaskListItem newItem = newSlot.getItem();
        return oldSlot.getDepth() == newSlot.getDepth()
                && oldSlot.getChildren().size() == newSlot.getChildren().size()
                && oldItem.itemType == newItem.itemType
                && Objects.equals(oldItem.taskId, newItem.taskId)
                && Objects.equals(oldItem.slotId, newItem.slotId)
                && Objects.equals(oldItem.slotParentId, newItem.slotParentId)
                && Objects.equals(oldItem.categoryId, newItem.categoryId)
                && Objects.equals(oldItem.categoryName, newItem.categoryName)
                && Objects.equals(oldItem.categoryIcon, newItem.categoryIcon)
                && Objects.equals(oldItem.categoryColorHex, newItem.categoryColorHex)
                && Objects.equals(oldItem.title, newItem.title)
                && Objects.equals(oldItem.description, newItem.description)
                && Objects.equals(oldItem.day, newItem.day)
                && Objects.equals(oldItem.start, newItem.start)
                && Objects.equals(oldItem.end, newItem.end)
                && Objects.equals(oldItem.deadline, newItem.deadline)
                && Objects.equals(oldItem.created, newItem.created)
                && oldItem.repeating == newItem.repeating
                && oldItem.streak == newItem.streak
                && oldItem.leisure == newItem.leisure
                && oldItem.score == newItem.score
                && oldItem.completed == newItem.completed
                && oldItem.inProgress == newItem.inProgress
                && oldItem.progressCurrent == newItem.progressCurrent
                && oldItem.progressTarget == newItem.progressTarget
                && Objects.equals(oldItem.progressUnit, newItem.progressUnit)
                && oldItem.progressStepDelta == newItem.progressStepDelta
                && Objects.equals(oldItem.goalIcon, newItem.goalIcon)
                && Objects.equals(oldItem.goalColorHex, newItem.goalColorHex);
    }
}
