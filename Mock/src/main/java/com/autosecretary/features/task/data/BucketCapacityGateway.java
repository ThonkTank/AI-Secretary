package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.BucketWindow;
import com.autosecretary.features.task.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Derives free minutes per bucket from configured windows and calendar blockers.
 *
 * <h2>Who calls this</h2>
 * Nightly planning chain after window + calendar reads.
 *
 * <h2>Why this exists separately</h2>
 * Keeps temporal math out of planner so planner consumes final capacity budgets directly.
 */
public interface BucketCapacityGateway {

    /**
     * @return map slot -> free minutes, already adjusted for overlaps and non-negative clamping.
     */
    Map<TimeSlot, Integer> computeAvailableMinutes(LocalDate day,
                                                   Map<TimeSlot, BucketWindow> windows,
                                                   List<CalendarAvailabilityGateway.Interval> blockedIntervals);
}
