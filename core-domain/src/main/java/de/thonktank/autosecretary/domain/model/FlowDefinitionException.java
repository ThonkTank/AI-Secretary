package de.thonktank.autosecretary.domain.model;

/** A user-correctable problem in a task's step flow. */
public final class FlowDefinitionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public FlowDefinitionException(String message) { super(message); }
}
