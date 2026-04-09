package com.autosecretary.features.task.domain.model;

import java.util.List;

/**
 * Placeholder task contract for planning.
 *
 * <p>Future APIs will consume a flat list of {@code TaskNode} entries while preserving
 * parent/child relationships for co-planning rules.
 */
public interface TaskNode {

    /** @return stable task id. */
    String taskId();

    /**
     * @return optional parent id; {@code null} for root tasks.
     */
    String parentTaskId();

    /**
     * @return direct child task ids; used for "plan child with parent" linking.
     */
    List<String> childTaskIds();

    /**
     * @return base score before parent-score propagation.
     */
    double baseScore();

    /**
     * @return expected effort duration in minutes.
     */
    int effortMinutes();
}
