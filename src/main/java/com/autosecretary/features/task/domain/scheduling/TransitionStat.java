package com.autosecretary.features.task.domain.scheduling;

/**
 * Domain-level representation of a learned task transition (A → B) used by the scheduler
 * to boost tasks that historically follow the previously scheduled task.
 *
 * <p><strong>Purpose:</strong> Captures observed patterns in task execution sequences. When task A
 * is scheduled, tasks that historically follow A receive a scoring boost, improving the likelihood
 * of scheduling them next (promoting natural workflow patterns).
 *
 * <p><strong>Usage:</strong> Loaded by {@link TaskTransitionStatLoader} and consulted by the slot
 * scoring algorithm in {@code TaskScorer}. If a task appears as the {@code toTaskId} of a transition
 * and the current slot's task matches {@code fromTaskId}, the candidate placement is boosted.
 *
 * <p><strong>Weight semantics:</strong> Higher weight indicates a stronger learned pattern. Typically
 * represents the frequency of transitions (A → B observed N times). Weight is applied as a multiplicative
 * or additive bonus in the scoring function. A weight of 0 or absence of a transition has no effect.
 *
 * <p><strong>Optional feature:</strong> Transition stats are optional; absence of transitions degrades
 * gracefully (scheduling proceeds without the boost).
 *
 * @param fromTaskId Source task UUID in the learned transition
 * @param toTaskId Target task UUID in the learned transition
 * @param weight Non-negative integer representing the strength of the learned pattern
 *
 * @see TaskTransitionStatLoader
 * @see com.autosecretary.features.task.domain.internal.scheduling.TaskScorer
 */
public record TransitionStat(String fromTaskId, String toTaskId, int weight) {
}
