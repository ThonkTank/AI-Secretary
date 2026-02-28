package com.autosecretary.features.task.domain.scheduling;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable result of slot generation for a day or window.
 *
 * <p><strong>Semantics:</strong> Generation is best-effort. {@code createdSlots} counts successfully
 * placed slots. {@code conflicts} accumulates all constraints that prevented other slots from being placed.
 * Both can be non-empty (e.g., some tasks placed successfully, others blocked by conflicts).
 *
 * <p>A {@code createdSlots} count of zero does not indicate failure — it may simply mean:
 * <ul>
 *   <li>All applicable tasks were already scheduled on earlier days</li>
 *   <li>No tasks are ready to schedule (prerequisites not met)</li>
 *   <li>All tasks encountered conflicts (see {@code conflicts} for details)</li>
 * </ul>
 *
 * <p>Conflicts are informational and used for:
 * <ul>
 *   <li>Reporting scheduling issues to the user via the UI</li>
 *   <li>Debugging scheduling decisions (why didn't task X get scheduled?)</li>
 *   <li>Potential rescheduling logic (e.g., adjust window times, resolve prerequisites)</li>
 * </ul>
 *
 * @param createdSlots Number of task slots successfully generated for the period
 * @param conflicts List of constraints that prevented slot placement; may be empty if all tasks succeeded
 *
 * @see TaskSlotGenerator
 * @see SchedulingConflict
 */
public record TaskSlotGenerationResult(int createdSlots, List<SchedulingConflict> conflicts) {
    /**
     * Canonical constructor ensuring {@code conflicts} is never null.
     */
    public TaskSlotGenerationResult {
        conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
    }
}
