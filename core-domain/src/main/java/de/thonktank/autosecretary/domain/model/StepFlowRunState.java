package de.thonktank.autosecretary.domain.model;

public enum StepFlowRunState {
    PENDING_START,
    WAITING_RESOURCE,
    OFFERED,
    WAITING_TIME,
    COMPLETED,
    CANCELLED;

    public boolean active() { return this != COMPLETED && this != CANCELLED; }
}
