package de.thonktank.autosecretary.domain.schedule;

public enum ScheduleMoveResult {
    MOVED,
    NOT_FOUND,
    REJECTED_INACTIVE_TASK,
    REJECTED_DUPLICATE_SLOT,
    REJECTED_TODAY_SLOT_OCCUPIED
}
