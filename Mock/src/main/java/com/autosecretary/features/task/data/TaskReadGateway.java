package com.autosecretary.features.task.data;

import com.autosecretary.features.task.domain.model.TaskNode;
import java.time.LocalDate;
import java.util.List;

/**
 * Read boundary for planning-facing task projections.
 *
 * <h2>Who calls this</h2>
 * Nightly planning orchestrator before slot-assignment, scoring, and plan assembly.
 *
 * <h2>Why this exists</h2>
 * Defines an explicit read path for task candidates so application call-chains do not rely on
 * implicit in-memory sources.
 */
public interface TaskReadGateway {

    /**
     * Loads the planning candidate set for a given day as {@link TaskNode} projections.
     */
    List<TaskNode> readPlanningCandidates(LocalDate day);
}
