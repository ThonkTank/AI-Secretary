package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.CapacityResource;
import de.thonktank.autosecretary.domain.repository.StepFlowDefinitionRepository;

/** Creates or edits a named capacity pool while preserving its stable identity. */
public final class SaveCapacityResource {
    private final StepFlowDefinitionRepository repository;
    private final IdGenerator ids;

    public SaveCapacityResource(StepFlowDefinitionRepository repository, IdGenerator ids) {
        this.repository = repository;
        this.ids = ids;
    }

    public CapacityResource execute(String id, String name, int capacity) {
        String identity = id == null || id.trim().isEmpty() ? ids.nextId() : id;
        CapacityResource resource = new CapacityResource(identity, name, capacity);
        repository.putCapacityResource(resource);
        return resource;
    }
}
