package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.BucketWindow;
import com.autosecretary.features.task.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.Map;

/**
 * Provides effective bucket windows (defaults + user overrides).
 *
 * <h2>Who calls this</h2>
 * Nightly planning chain before capacity calculation.
 *
 * <h2>External APIs implementation will access</h2>
 * App settings/config store.
 *
 * <h2>Why return map by TimeSlot</h2>
 * Capacity/planner can consume directly without index-based coupling.
 */
public interface BucketWindowConfigGateway {

    /** Returns one effective window per bucket for a planning day. */
    Map<TimeSlot, BucketWindow> readBucketWindowsForDay(LocalDate day);
}
