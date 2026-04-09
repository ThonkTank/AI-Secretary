package com.autosecretary.features.task.application.api;

import com.autosecretary.features.task.domain.model.TaskNode;
import java.util.List;
import java.util.Map;

/**
 * Capability placeholder: task scoring, independent from time-slot assignment.
 *
 * <p>Planned behavior:
 * <ul>
 *   <li>Compute base scores for all tasks.</li>
 *   <li>Propagate parent score to children: effectiveChildScore = child + parent.</li>
 *   <li>Return a score map that can be reused by independent assignment/planning flows.</li>
 * </ul>
 */
public interface TaskScoringApi {

    /**
     * Computes effective scores without assigning tasks to specific time slots.
     *
     * @param tasks all tasks to evaluate
     * @return mapping taskId -> effective score
     */
    Map<String, Double> computeEffectiveScores(List<TaskNode> tasks);
}
