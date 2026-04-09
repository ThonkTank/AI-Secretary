package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.TaskNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Computes rankable planning scores for tasks.
 *
 * <h2>Who calls this</h2>
 * Nightly planning orchestrator before bucket fill.
 *
 * <h2>How output is consumed</h2>
 * {@link TaskPlanningApi} uses score values directly for greedy ordering inside buckets.
 *
 * <h2>Why these parameters</h2>
 * <ul>
 *   <li>{@code tasks}: full candidate set, so scorer can evaluate relative urgency/constraints.</li>
 *   <li>{@code planningDay}: many Main-like factors are day-dependent (deadline, repetition state).</li>
 * </ul>
 *
 * <h2>External dependencies this type will need (in implementation)</h2>
 * Completion/repetition history, budget-eligibility source, and optional transition-pattern source.
 * They are needed so callers get final usable scores, not partial sub-signals.
 */
public interface TaskScoringApi {

    /**
     * @return map taskId -> final score ready for direct planner consumption.
     */
    Map<String, Double> computeDailyScores(List<TaskNode> tasks, LocalDate planningDay);
}
