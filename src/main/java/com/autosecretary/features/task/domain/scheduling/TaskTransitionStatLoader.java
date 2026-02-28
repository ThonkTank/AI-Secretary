package com.autosecretary.features.task.domain.scheduling;

import java.util.List;

/**
 * Domain contract for loading task-transition statistics used by the scheduler
 * to boost tasks that historically follow the previously scheduled task.
 * Abstracts the data-layer DAO so the domain does not depend on Room.
 */
public interface TaskTransitionStatLoader {
    /**
     * Loads all recorded task-transition statistics from the data layer.
     *
     * <p>Called once per generation run by {@code DefaultTaskSlotGenerator} (after {@code reset()},
     * before scoring begins). The returned list is passed to {@code TaskScorer.setTransitionStats()},
     * which indexes the stats for O(1) lookup during slot scoring.
     *
     * @return list of transition stats; an empty list is valid and disables the follow-up boost
     *         entirely (graceful degradation — scheduling proceeds without learned-pattern boosts)
     */
    List<TransitionStat> load();
}
