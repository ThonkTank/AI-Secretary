package de.thonktank.autosecretary.domain.model;

/** Selects the calendar event that may reduce an unresolved combo obligation. */
public enum ComboDecayTrigger {
    MISSED_OCCURRENCE,
    DAILY_OVERDUE,
    NEXT_SCHEDULED_OCCURRENCE
}
