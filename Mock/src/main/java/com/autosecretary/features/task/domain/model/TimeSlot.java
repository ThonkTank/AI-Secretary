package com.autosecretary.features.task.domain.model;

/**
 * Canonical buckets for this experiment.
 *
 * <h2>Who uses this type</h2>
 * Config, capacity, assignment, planning, and persisted bucket plans.
 *
 * <h2>Why enum</h2>
 * Stable shared key across modules; avoids string drift and makes API contracts explicit.
 */
public enum TimeSlot {
    MORGEN,
    VORMITTAG,
    MITTAG,
    NACHMITTAG,
    ABEND
}
