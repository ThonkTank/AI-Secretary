package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.VersionedBucketPlan;
import java.time.LocalDate;

/**
 * Read boundary for persisted daily bucket plans.
 *
 * <h2>Who calls this</h2>
 * - Completion-driven replan chain (loads current day snapshot before mutation).
 * - UI/application projections that need the persisted plan view.
 *
 * <h2>Why this exists</h2>
 * Makes the plan read path explicit instead of passing in-memory plans through unrelated layers.
 */
public interface PlanReadGateway {

    /**
     * Returns persisted plan snapshot for day or {@code null} if no draft exists yet.
     */
    VersionedBucketPlan readPlanForDay(LocalDate day);
}
