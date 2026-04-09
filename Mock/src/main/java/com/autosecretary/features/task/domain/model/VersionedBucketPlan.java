package com.autosecretary.features.task.domain.model;

/**
 * Persisted plan snapshot with storage revision metadata for optimistic-locking writes.
 */
public record VersionedBucketPlan(BucketPlan plan, long revision) {
}
