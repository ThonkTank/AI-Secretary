package com.autosecretary.features.task.domain.model;

/**
 * One assignment row in a bucket plan.
 *
 * <h2>Who consumes this</h2>
 * Persistence, UI rendering, and dynamic refill logic.
 *
 * <h2>Why these fields</h2>
 * - {@code taskId}: stable join key.
 * - {@code score}: debug/explainability for ranking.
 * - {@code plannedMinutes}: direct capacity accounting value.
 * - {@code source}: lineage (nightly seed vs dynamic refill).
 */
public record BucketPlanEntry(String taskId, double score, int plannedMinutes, String source) {
}
