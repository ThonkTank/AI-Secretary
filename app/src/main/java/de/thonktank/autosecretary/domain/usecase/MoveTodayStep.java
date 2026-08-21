package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.repository.TaskRepository;

/** Persists an execution-only step order without changing reusable templates. */
public final class MoveTodayStep {
    private final TaskRepository repository;

    public MoveTodayStep(TaskRepository repository) {
        this.repository = repository;
    }

    public boolean execute(String stepId, String beforeStepId) {
        if (stepId == null || stepId.isEmpty())
            throw new IllegalArgumentException("Step identity is required");
        return repository.inTransaction(() ->
                TodayStepOrder.move(repository, stepId, beforeStepId));
    }
}
