package com.autosecretary.features.task.application.listmodel;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Immutable presentation model for displaying a task or calendar event in a list.
 *
 * <p>TaskListItem flattens the domain {@link com.autosecretary.features.task.domain.model.Task} and
 * {@link com.autosecretary.features.task.domain.model.TaskSlot} into a single record optimized for list
 * rendering. This class decouples the UI layer from domain changes.
 *
 * <p><strong>Field Semantics:</strong>
 * <ul>
 *   <li><strong>Scheduled tasks:</strong> All fields are populated except calendar-event fields.
 *   <li><strong>Unscheduled tasks:</strong> {@code start}, {@code end}, {@code slotId},
 *       {@code slotParentId} are null; {@code score} is 0.
 *   <li><strong>Calendar events:</strong> Only {@code itemType}, {@code taskId}, {@code slotId},
 *       {@code title}, {@code day}, {@code start}, {@code end} are populated; all other fields are null.
 *       Both {@code taskId} and {@code slotId} hold the same synthetic event ID (always check
 *       {@link #isCalendarEvent()} before relying on these fields as DB IDs).
 * </ul>
 *
 * <p><strong>Field Grouping:</strong>
 * <ul>
 *   <li><strong>Identifiers:</strong> {@code taskId}, {@code slotId}, {@code slotParentId}, {@code categoryId}.
 *   <li><strong>Display:</strong> {@code title}, {@code description}.
 *   <li><strong>Scheduling:</strong> {@code day}, {@code start}, {@code end}, {@code deadline}.
 *   <li><strong>Progress:</strong> {@code progressCurrent}, {@code progressTarget}, {@code progressUnit}, {@code progressStepDelta}.
 *   <li><strong>Goal decoration:</strong> {@code goalIcon}, {@code goalColorHex}.
 *   <li><strong>Flags:</strong> {@code completed}, {@code inProgress}, {@code streak}, {@code score}.
 * </ul>
 *
 * @see TaskListItemMapper for construction and transformation logic.
 */
public class TaskListItem {
    /**
     * Type of list item: either a domain Task or a calendar event from the device calendar.
     *
     * <p><strong>TASK:</strong> A scheduled or unscheduled task with all TaskListItem fields
     * populated. Use {@code isCalendarEvent()} to distinguish.
     *
     * <p><strong>CALENDAR_EVENT:</strong> An event from the device calendar. Only {@code itemType},
     * {@code taskId} (holds event ID), {@code slotId} (also holds event ID), {@code title},
     * {@code day}, {@code start}, {@code end} are populated; all other fields are null or have
     * default values. This type is used to render calendar entries alongside tasks in the same list.
     */
    public enum ItemType {
        TASK,
        CALENDAR_EVENT,
        /** Synthetic group header for a {@link com.autosecretary.features.task.domain.model.TaskCategory}
         *  in Manage mode. Only {@code itemType}, {@code categoryId}, {@code title} (category name),
         *  {@code categoryIcon}, {@code categoryColorHex} are populated. */
        CATEGORY_HEADER
    }

    /**
     * Deadline urgency threshold: deadlines within this many days are classified as SOON.
     *
     * <p>Used by {@link #deadlineUrgency()} to distinguish between SOON (0-3 days) and
     * FUTURE (> 3 days) urgency classifications.
     */
    private static final int SOON_DEADLINE_DAYS = 3;

    /**
     * Deadline urgency classification based on days until deadline.
     *
     * <p>Thresholds:
     * <ul>
     *   <li><strong>NONE:</strong> No deadline set.
     *   <li><strong>OVERDUE:</strong> Deadline has passed (daysUntil &lt; 0).
     *   <li><strong>TODAY:</strong> Deadline is today (daysUntil == 0).
     *   <li><strong>SOON:</strong> Deadline within {@link #SOON_DEADLINE_DAYS} days (0 &lt; daysUntil &lt;= {@link #SOON_DEADLINE_DAYS}).
     *   <li><strong>FUTURE:</strong> Deadline more than {@link #SOON_DEADLINE_DAYS} days away (daysUntil &gt; {@link #SOON_DEADLINE_DAYS}).
     * </ul>
     *
     * <p>Used by UI for deadline highlighting, icon color, and priority ordering.
     */
    public enum DeadlineUrgency {
        NONE,
        OVERDUE,
        TODAY,
        SOON,
        FUTURE
    }

    public final ItemType itemType;
    public final String taskId;
    public final String slotId;
    public final String slotParentId;
    public final String categoryId;
    public final String categoryName;
    public final String categoryIcon;
    public final String categoryColorHex;
    public final String title;
    public final String description;
    public final LocalDate day;
    public final LocalTime start;
    public final LocalTime end;
    public final LocalDate deadline;
    public final int streak;
    public final int score;
    public final int priorityWeight;
    public final boolean completed;
    public final boolean inProgress;
    /** When true, this is a leisure/hobby item: no streak, progress, or scoring pressure is shown. */
    public final boolean leisure;
    /** True when the task repeats (repetition reps &gt; 0); false for one-off tasks. */
    public final boolean repeating;
    /** Task creation date; start of the created→deadline span used by {@link #deadlineElapsedPercent()}. */
    public final LocalDate created;
    public final int progressCurrent;
    public final int progressTarget;
    public final String progressUnit;
    public final int progressStepDelta;
    public final String goalIcon;
    public final String goalColorHex;

    TaskListItem(ItemType itemType,
                 String taskId,
                 String slotId,
                 String slotParentId,
                 String categoryId,
                 String categoryName,
                 String categoryIcon,
                 String categoryColorHex,
                 String title,
                 String description,
                 LocalDate day,
                 LocalTime start,
                 LocalTime end,
                 LocalDate deadline,
                 int streak,
                 int score,
                 int priorityWeight,
                 boolean completed,
                 boolean inProgress,
                 boolean leisure,
                 boolean repeating,
                 LocalDate created,
                 int progressCurrent,
                 int progressTarget,
                 String progressUnit,
                 int progressStepDelta,
                 String goalIcon,
                 String goalColorHex) {
        this.itemType = itemType;
        this.taskId = taskId;
        this.slotId = slotId;
        this.slotParentId = slotParentId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryIcon = categoryIcon;
        this.categoryColorHex = categoryColorHex;
        this.title = title;
        this.description = description;
        this.day = day;
        this.start = start;
        this.end = end;
        this.deadline = deadline;
        this.streak = streak;
        this.score = score;
        this.priorityWeight = priorityWeight;
        this.completed = completed;
        this.inProgress = inProgress;
        this.leisure = leisure;
        this.repeating = repeating;
        this.created = created;
        this.progressCurrent = progressCurrent;
        this.progressTarget = progressTarget;
        this.progressUnit = progressUnit;
        this.progressStepDelta = progressStepDelta;
        this.goalIcon = goalIcon;
        this.goalColorHex = goalColorHex;
    }

    /** Sentinel category id for the synthetic "uncategorised" group header in Manage mode. */
    public static final String UNCATEGORIZED_ID = "__uncategorized__";

    /** Factory for a synthetic category group header (Manage mode). */
    public static TaskListItem categoryHeader(String categoryId, String name, String icon, String colorHex) {
        return new TaskListItem(
                ItemType.CATEGORY_HEADER,
                null,
                null,
                null,
                categoryId,
                name,
                icon,
                colorHex,
                name,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                false,
                false,
                false,
                false,
                null,
                0,
                0,
                null,
                0,
                icon,
                colorHex
        );
    }

    public boolean isCategoryHeader() {
        return itemType == ItemType.CATEGORY_HEADER;
    }

    /**
     * Returns true if this item has a progress target, meaning the task tracks incremental progress.
     *
     * <p>This check determines whether the UI should render a progress bar or counter.
     *
     * @return true if {@code progressTarget > 0}, indicating progressive task tracking is enabled.
     */
    public boolean hasProgressTarget() {
        return progressTarget > 0;
    }

    /**
     * Factory method for creating a calendar event list item.
     *
     * <p>Calendar events are read from the device calendar and displayed alongside tasks in the
     * task list. This method creates a minimal TaskListItem with only the event's essential data.
     *
     * <p><strong>Important:</strong> Both {@code taskId} and {@code slotId} are set to the same
     * synthetic {@code eventId}. These are NOT database IDs. Always check {@link #isCalendarEvent()}
     * before using {@code taskId} or {@code slotId} in queries or database operations. This dual
     * assignment is an implementation detail to allow unified list rendering.
     *
     * @param eventId the synthetic event identifier (not a database ID; generated by the calendar provider)
     * @param title   the event title
     * @param day     the event date
     * @param start   the event start time (may be null for all-day events)
     * @param end     the event end time (may be null for all-day events)
     * @return a TaskListItem representing the calendar event
     */
    public static TaskListItem calendarEvent(String eventId, String title, LocalDate day, LocalTime start, LocalTime end) {
        return new TaskListItem(eventId, title, day, start, end);
    }

    private TaskListItem(String eventId, String title, LocalDate day, LocalTime start, LocalTime end) {
        this.itemType = ItemType.CALENDAR_EVENT;
        this.taskId = eventId;
        this.slotId = eventId;
        this.slotParentId = null;
        this.categoryId = null;
        this.categoryName = null;
        this.categoryIcon = null;
        this.categoryColorHex = null;
        this.title = title;
        this.description = null;
        this.day = day;
        this.start = start;
        this.end = end;
        this.deadline = null;
        this.streak = 0;
        this.score = 0;
        this.priorityWeight = 0;
        this.completed = false;
        this.inProgress = false;
        this.leisure = false;
        this.repeating = false;
        this.created = null;
        this.progressCurrent = 0;
        this.progressTarget = 0;
        this.progressUnit = null;
        this.progressStepDelta = 0;
        this.goalIcon = null;
        this.goalColorHex = null;
    }

    /**
     * Returns true if this is a calendar event.
     *
     * <p>Calendar events have a different field semantic than tasks: only {@code itemType},
     * {@code taskId}, {@code slotId}, {@code title}, {@code day}, {@code start}, {@code end} are
     * populated. Always check this before treating {@code taskId} or {@code slotId} as database IDs.
     *
     * @return true if {@code itemType == ItemType.CALENDAR_EVENT}
     */
    public boolean isCalendarEvent() {
        return itemType == ItemType.CALENDAR_EVENT;
    }

    /**
     * Returns true if this is a scheduled (non-calendar) task item on the given day.
     *
     * <p>A task is considered "scheduled on a day" if it has a {@code start} time (indicating a
     * slot exists) and either the given day is null (match any day) or matches {@code this.day}.
     *
     * @param day the date to check, or null to check if scheduled regardless of day
     * @return true if scheduled and on the given day (or any day if day is null)
     */
    public boolean isScheduledOn(LocalDate day) {
        return this.start != null && (day == null || day.equals(this.day));
    }

    /**
     * Returns the number of days from today until the deadline.
     *
     * <p>Positive values indicate future deadlines, zero indicates today, negative values indicate
     * past deadlines. Returns {@link Long#MAX_VALUE} if no deadline is set.
     *
     * @return days until deadline, or Long.MAX_VALUE if deadline is null
     */
    public long daysUntilDeadline() {
        if (deadline == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), deadline);
    }

    /**
     * Returns how much of the created→deadline span has already elapsed, as a percentage (0–100).
     *
     * <p>Basis for the visual remaining-time bar in the deadline list mode. Overdue items and
     * items whose span is empty or inverted (deadline on or before creation) return 100.
     * Items without a deadline return 0.
     *
     * @return elapsed percentage, clamped to 0–100
     */
    public int deadlineElapsedPercent() {
        if (deadline == null) {
            return 0;
        }
        LocalDate base = created != null ? created : LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(base, deadline);
        if (totalDays <= 0) {
            return 100;
        }
        long elapsedDays = ChronoUnit.DAYS.between(base, LocalDate.now());
        return (int) Math.max(0, Math.min(100, (elapsedDays * 100) / totalDays));
    }

    /**
     * Returns the urgency classification of this item's deadline.
     *
     * <p>See {@link DeadlineUrgency} for threshold definitions. Used by the UI for highlighting,
     * sorting, and icon decoration.
     *
     * @return the urgency classification; never null
     */
    public DeadlineUrgency deadlineUrgency() {
        if (deadline == null) {
            return DeadlineUrgency.NONE;
        }

        long daysUntil = daysUntilDeadline();
        if (daysUntil < 0) return DeadlineUrgency.OVERDUE;
        if (daysUntil == 0) return DeadlineUrgency.TODAY;
        if (daysUntil <= SOON_DEADLINE_DAYS) return DeadlineUrgency.SOON;
        return DeadlineUrgency.FUTURE;
    }
}
