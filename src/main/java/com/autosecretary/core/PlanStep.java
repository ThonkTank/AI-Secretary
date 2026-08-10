package com.autosecretary.core;

/** One independently completable child inside a routine's current occurrence. */
public record PlanStep(String id, String title, boolean completed) {
}
