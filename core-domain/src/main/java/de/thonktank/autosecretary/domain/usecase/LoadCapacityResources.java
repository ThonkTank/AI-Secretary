package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;

import java.util.List;

/** Loads the shared capacity catalog for an unsaved task editor draft. */
public final class LoadCapacityResources {
    private final StepFlowDefinitionRepository repository;

    public LoadCapacityResources(StepFlowDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<CapacityResource> execute() { return repository.capacityResources(); }
}
