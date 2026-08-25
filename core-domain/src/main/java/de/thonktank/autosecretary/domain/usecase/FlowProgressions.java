package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.SystemMomentSource;
import de.thonktank.autosecretary.domain.repository.FlowExecutionRepository;

final class FlowProgressions {
    static FlowProgression create(Object repository, Clock clock) {
        return repository instanceof FlowExecutionRepository
                ? new FlowRuntimeCoordinator((FlowExecutionRepository) repository, clock,
                new SystemMomentSource(), new UuidGenerator()) : FlowProgression.NONE;
    }

    private FlowProgressions() { }
}
