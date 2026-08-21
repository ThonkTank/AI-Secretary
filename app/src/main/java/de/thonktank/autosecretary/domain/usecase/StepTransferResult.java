package de.thonktank.autosecretary.domain.usecase;

/** Explicit business outcome of moving or swapping a stable step definition. */
public enum StepTransferResult {
    DEFINITION_AND_TODAY_MOVED,
    DEFINITION_ONLY_FOR_FUTURE,
    STEPS_SWAPPED,
    UNCHANGED,
    NOT_FOUND,
    REJECTED_ARCHIVED_TASK,
    REJECTED_OCCUPIED_TARGET,
    REJECTED_INVALID_POSITION_SEQUENCE
}
