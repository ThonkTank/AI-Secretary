package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.BucketPlan;
import java.time.LocalDate;

/**
 * Persistence boundary for daily bucket plans.
 *
 * <h2>Who calls this</h2>
 * - Nightly planning chain writes initial draft.
 * - Completion-driven replan overwrites same-day plan.
 *
 * <h2>Why this split (save vs overwrite)</h2>
 * Makes intent explicit and simplifies idempotency handling in implementations.
 */
public interface PlanWriteGateway {

    /** Writes first nightly plan snapshot for day idempotently (keyed by day + idempotencyKey). */
    void saveDraftPlan(LocalDate day, BucketPlan plan, String idempotencyKey);

    /**
     * Replaces plan after dynamic duplicate cleanup and refill with optimistic locking.
     *
     * @param expectedRevision revision caller read before mutation; update must fail on mismatch.
     */
    void overwritePlan(LocalDate day, BucketPlan updatedPlan, long expectedRevision, String idempotencyKey);
}
