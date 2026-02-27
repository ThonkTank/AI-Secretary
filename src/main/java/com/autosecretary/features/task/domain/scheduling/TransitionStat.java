package com.autosecretary.features.task.domain.scheduling;

/**
 * Domain-level representation of a learned task transition (A → B) used by the scheduler
 * to boost tasks that historically follow the previously scheduled task.
 */
public record TransitionStat(String fromTaskId, String toTaskId, int weight) {
}
