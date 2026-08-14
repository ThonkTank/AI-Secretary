package com.autosecretary.domain;

public record PlanConflict(WorkItem workItem, String occurrenceKey, Reason reason, String detail) {
    public enum Reason { NO_CAPACITY, AFTER_DEADLINE, OUTSIDE_HORIZON }
}
