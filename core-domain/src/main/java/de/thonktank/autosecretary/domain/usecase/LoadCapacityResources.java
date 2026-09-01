package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.repository.FlowRepository;

import java.util.List;

/** Loads the shared capacity catalog for an unsaved task editor draft. */
public final class LoadCapacityResources {
    private final FlowRepository repository;

    public LoadCapacityResources(FlowRepository repository) {
        this.repository = repository;
    }

    public List<CapacityResource> execute() { return repository.capacityResources(); }
}
