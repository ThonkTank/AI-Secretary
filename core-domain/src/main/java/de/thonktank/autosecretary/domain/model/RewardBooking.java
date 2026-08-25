package de.thonktank.autosecretary.domain.model;

import java.time.LocalDate;

/** Immutable ledger entry for one signed XP/combo change. */
public final class RewardBooking {
    public enum Kind { STEP_EARNED, ROUTINE_HARVEST, SINGLE_COMPLETION, CONDITION_COMPLETION,
        COMBO_DECAY, LEGACY_STEP, LEGACY_COMPLETION, REVERSAL }
    public enum Target { VESSEL, HEAD }

    public final String id;
    public final String transactionId;
    public final String occurrenceId;
    public final String occurrenceStepId;
    public final String ownerId;
    public final Kind kind;
    public final Target target;
    public final int xpDelta;
    public final int comboPointDelta;
    public final LocalDate bookedOn;
    public final String reversesBookingId;

    public RewardBooking(String id, String transactionId, String occurrenceId,
                         String occurrenceStepId, String ownerId, Kind kind, Target target,
                         int xpDelta, int comboPointDelta, LocalDate bookedOn,
                         String reversesBookingId) {
        if (blank(id) || blank(transactionId) || blank(occurrenceId) || blank(ownerId)
                || kind == null || target == null || bookedOn == null)
            throw new IllegalArgumentException("Reward booking identity and classification are required");
        if (xpDelta == 0 && comboPointDelta == 0)
            throw new IllegalArgumentException("Reward booking needs a non-zero delta");
        if (kind == Kind.REVERSAL && blank(reversesBookingId))
            throw new IllegalArgumentException("Reversal needs its original booking");
        if (kind != Kind.REVERSAL && reversesBookingId != null)
            throw new IllegalArgumentException("Only reversals may reference an original booking");
        this.id = id;
        this.transactionId = transactionId;
        this.occurrenceId = occurrenceId;
        this.occurrenceStepId = blank(occurrenceStepId) ? null : occurrenceStepId;
        this.ownerId = ownerId;
        this.kind = kind;
        this.target = target;
        this.xpDelta = xpDelta;
        this.comboPointDelta = comboPointDelta;
        this.bookedOn = bookedOn;
        this.reversesBookingId = blank(reversesBookingId) ? null : reversesBookingId;
    }

    public RewardBooking reverse(String reversalId, String reversalTransactionId, LocalDate date) {
        if (kind == Kind.REVERSAL) throw new IllegalStateException("A reversal cannot be reversed");
        return new RewardBooking(reversalId, reversalTransactionId, occurrenceId,
                occurrenceStepId, ownerId, Kind.REVERSAL, target, -xpDelta,
                -comboPointDelta, date, id);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
