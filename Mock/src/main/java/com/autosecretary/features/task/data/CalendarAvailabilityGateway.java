package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.TimeSlot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Reads blocked calendar time used for capacity calculation.
 *
 * <h2>Who calls this</h2>
 * Nightly planning chain (before capacity computation).
 *
 * <h2>External APIs implementation will access</h2>
 * Platform/device calendar providers and optionally app-local hold blocks.
 *
 * <h2>Why both methods exist</h2>
 * Some backends can provide bucket-aggregated blockers directly; others only raw intervals.
 * Both return shapes are directly useful to downstream callers.
 */
public interface CalendarAvailabilityGateway {

    /** Raw blocked intervals for day. */
    List<Interval> readBlockedIntervals(LocalDate day);

    /** Optional pre-aggregated blocked minutes per bucket for optimized pipelines. */
    Map<TimeSlot, Integer> readBlockedMinutesPerBucket(LocalDate day);

    /** Half-open blocked interval [start, end). */
    record Interval(LocalDateTime start, LocalDateTime end) {}
}
