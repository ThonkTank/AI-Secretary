package com.autosecretary.features.task.domain.scheduling;

/**
 * Global scheduling tuning, resolved once per generation run.
 *
 * <p>The buffer values shape the placement math only — persisted slot times stay the real
 * task times, and fixed (TERMIN) pinning ignores the buffers entirely (two back-to-back
 * appointments are never a conflict; the buffers constrain <em>movable</em> tasks).
 *
 * <p>The balance values are <em>soft</em> daily constraints: exceeding the work budget
 * divides a placement's score (it can still win when nothing else fits or the task is
 * urgent/high-priority), and unmet leisure quota boosts leisure placements. {@code 0}
 * disables the respective constraint.
 *
 * @param slotPauseMinutes        minimum pause between consecutive movable task slots; a
 *                                prerequisite's larger {@code minGapMinutes} subsumes it
 * @param appointmentLeadMinutes  free lead time kept before appointments (device calendar
 *                                events and TERMIN slots) for preparation/travel
 * @param maxWorkMinutesPerDay    soft cap on non-leisure task minutes per day (0 = off)
 * @param minLeisureMinutesPerDay leisure minutes per day below which leisure placements
 *                                are boosted (0 = off)
 */
public record SchedulingTuning(int slotPauseMinutes,
                               int appointmentLeadMinutes,
                               int maxWorkMinutesPerDay,
                               int minLeisureMinutesPerDay) {

    /** No buffers, no balance constraints — plain back-to-back placement (pre-tuning behaviour). */
    public static final SchedulingTuning NONE = new SchedulingTuning(0, 0, 0, 0);
}
