package com.autosecretary.features.task.domain;

import java.util.List;

/**
 * Domain contract for loading task-transition statistics used by the scheduler
 * to boost tasks that historically follow the previously scheduled task.
 * Abstracts the data-layer DAO so the domain does not depend on Room.
 */
public interface TaskTransitionStatLoader {
    List<TransitionStat> load();
}
