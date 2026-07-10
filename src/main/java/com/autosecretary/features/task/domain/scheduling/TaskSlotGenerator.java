package com.autosecretary.features.task.domain.scheduling;

import com.autosecretary.features.task.domain.model.Task;

import java.time.LocalDate;
import java.util.List;

/**
 * Public domain contract for multi-day task slot generation.
 *
 * <p><strong>Purpose:</strong> Orchestrates the generation of executable task slots (scheduled time blocks)
 * across one or more days. Called by {@code RegenerateScheduleUseCase} and widget generation to create the
 * day's schedule.
 *
 * <p><strong>Execution flow for a multi-day window:</strong>
 * <ol>
 *   <li>Create a fresh {@link TaskPlanningState} instance</li>
 *   <li>Call {@link #recordPreservedSlots} to register existing slots (avoids duplication)</li>
 *   <li>For each day in the window, call {@link #generateSlotsForDay} to place new slots</li>
 *   <li>Call {@link #recordScheduledSlotsForDay} at the end of each day to update state for next day</li>
 * </ol>
 * <p>Alternatively, call {@link #generateSlotsForWindow} for convenience (handles the loop internally).
 *
 * <p><strong>Implementation:</strong> Default implementation in {@code DefaultTaskSlotGenerator}
 * ({@code domain/internal/scheduling/}). Evaluates candidate placement times using a composite scoring algorithm,
 * considers calendar conflicts and scheduling windows, and respects task prerequisites and hierarchy.
 *
 * @see TaskPlanningState
 * @see TaskSlotGenerationResult
 * @see SchedulingConflict
 * @see com.autosecretary.features.task.domain.internal.scheduling.DefaultTaskSlotGenerator
 */
public interface TaskSlotGenerator {
    /**
     * Registers existing slots for a date range to avoid duplicate generation.
     *
     * <p>Called at the start of multi-day regeneration to bookkeep slots that already exist
     * (e.g., from previous generations not yet completed). Prevents the generator from creating
     * duplicate slots on subsequent calls.
     *
     * @param tasks List of root tasks to process (traverses children via task relations)
     * @param startInclusive First day (inclusive) to scan for existing slots
     * @param endExclusive First day to exclude from scan
     * @param state Cross-day planning state; updated to record all found slots
     */
    void recordPreservedSlots(List<Task> tasks, LocalDate startInclusive, LocalDate endExclusive, TaskPlanningState state);

    /**
     * Generates task slots for a single day.
     *
     * <p>Attempts to place all applicable tasks on the given day, respecting scheduling windows,
     * calendar conflicts, prerequisites, and constraints from earlier days (via {@code state}).
     *
     * <p>Slot placement is best-effort: some tasks may succeed while others encounter conflicts.
     * See {@link TaskSlotGenerationResult} for semantics.
     *
     * @param tasks List of root tasks to schedule (child tasks included via hierarchy)
     * @param day The day on which to generate slots
     * @param state Cross-day planning state; updated with newly scheduled slots and conflicts
     * @return Generation result with count of created slots and any conflicts encountered
     *
     * @see TaskSlotGenerationResult
     * @see SchedulingConflict
     */
    TaskSlotGenerationResult generateSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state);

    /**
     * Generates task slots across a multi-day window (convenience method).
     *
     * <p>Equivalent to calling {@link #generateSlotsForDay} once per day, accumulating results.
     * Internally handles state management and per-day bookkeeping.
     *
     * @param tasks List of root tasks to schedule
     * @param startDay First day to generate slots for
     * @param days Number of consecutive days to generate (must be {@code >= 1})
     * @param state Cross-day planning state; accumulates across all days
     * @return Aggregated result with total created slots and all conflicts across the window
     *
     * @see #generateSlotsForDay
     */
    TaskSlotGenerationResult generateSlotsForWindow(List<Task> tasks, LocalDate startDay, int days, TaskPlanningState state);

    /**
     * Records newly scheduled slots from today's generation to plan state.
     *
     * <p>Called after each day's generation completes. Updates state so that subsequent days
     * are aware of today's placements (for distribution, spacing, prerequisite ordering, etc.).
     *
     * <p>Must be called for each day after {@link #generateSlotsForDay} before calling it again
     * for the next day (unless using {@link #generateSlotsForWindow}, which handles this internally).
     *
     * @param tasks List of root tasks (must match the tasks passed to {@code generateSlotsForDay})
     * @param day The day on which slots were scheduled
     * @param state Cross-day planning state; updated to reflect scheduled slots for the day
     */
    void recordScheduledSlotsForDay(List<Task> tasks, LocalDate day, TaskPlanningState state);
}
