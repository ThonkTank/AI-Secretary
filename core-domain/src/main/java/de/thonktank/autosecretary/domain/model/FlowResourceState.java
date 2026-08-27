package de.thonktank.autosecretary.domain.model;

public enum FlowResourceState {
    PLANNED,
    RESERVED,
    ACTIVE,
    RELEASED;

    public boolean consumesCapacity() { return this == RESERVED || this == ACTIVE; }
}
