package com.autosecretary.features.task.domain.scheduling;

import java.time.LocalDate;

/**
 * Represents a scheduling constraint violation preventing task slot generation.
 *
 * <p>Immutable result object returned by {@link TaskSlotGenerator} when a requested task slot
 * cannot be placed due to a scheduling conflict. Used for reporting scheduling issues to the user
 * (via the application layer) and for debugging scheduling decisions.
 *
 * @param taskId Task UUID that could not be scheduled
 * @param title User-facing task title (for UI display)
 * @param day The day on which scheduling was attempted
 * @param reasonCode Categorization of the conflict reason
 * @param details Human-readable explanation in German (for debugging/logging; not localized for display)
 */
public record SchedulingConflict(String taskId,
                                 String title,
                                 LocalDate day,
                                 ReasonCode reasonCode,
                                 String details) {

    /**
     * Categorizes the reason a task slot could not be placed.
     *
     * <p>Each reason has different implications for scheduling logic and UI messaging:
     * <ul>
     *   <li><strong>OUTSIDE_WINDOW:</strong> Slot falls outside the configured scheduling window
     *       (e.g., too early/late in day). Typically not urgent; task may be scheduled on a different day.</li>
     *   <li><strong>CALENDAR_OVERLAP:</strong> Slot overlaps an existing calendar event or user-held time.
     *       Scheduling must find an alternative time or defer the task.</li>
     *   <li><strong>PREREQUISITE_BLOCKED:</strong> Task has a prerequisite that has not been completed.
     *       Task cannot be scheduled until the prerequisite is done. Check task hierarchy and dependencies.</li>
     *   <li><strong>NO_MATCHING_GAP:</strong> No available time gap is large enough to fit the required slot duration.
     *       The scheduling window is fully occupied or fragmented. May need to adjust task duration or window times.</li>
     * </ul>
     */
    public enum ReasonCode {
        /** Slot falls outside the configured scheduling window (e.g., too early/late in day). */
        OUTSIDE_WINDOW,

        /** Slot overlaps a blocked calendar interval (e.g., another event, user hold). */
        CALENDAR_OVERLAP,

        /** Slot cannot be scheduled because a prerequisite task has not been completed. */
        PREREQUISITE_BLOCKED,

        /** No available time gap large enough to fit the slot's required duration. */
        NO_MATCHING_GAP
    }
}
