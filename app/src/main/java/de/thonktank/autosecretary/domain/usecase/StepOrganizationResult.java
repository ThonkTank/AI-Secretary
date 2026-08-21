package de.thonktank.autosecretary.domain.usecase;

public enum StepOrganizationResult {
    MOVED,
    SWAPPED,
    UNCHANGED,
    NOT_FOUND,
    REJECTED_INACTIVE_TASK
}
