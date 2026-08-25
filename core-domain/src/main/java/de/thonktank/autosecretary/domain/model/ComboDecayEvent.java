package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** Immutable proof that one owner/date decay opportunity has already been evaluated. */
public final class ComboDecayEvent {
    public final String ownerId;
    public final LocalDate eventOn;
    public final String bookingId;

    public ComboDecayEvent(String ownerId, LocalDate eventOn, String bookingId) {
        if (ownerId == null || ownerId.trim().isEmpty() || eventOn == null)
            throw new IllegalArgumentException("Combo decay event needs an owner and date");
        this.ownerId = ownerId;
        this.eventOn = eventOn;
        this.bookingId = bookingId == null || bookingId.trim().isEmpty() ? null : bookingId;
    }
}
