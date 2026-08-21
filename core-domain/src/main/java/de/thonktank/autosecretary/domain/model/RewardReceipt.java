package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of one ledger transaction. Direction is expressed by signed deltas. */
public final class RewardReceipt {
    public enum Target { NONE, VESSEL, HEAD }

    public final String transactionId;
    public final List<RewardBooking> bookings;
    public final int xp;
    public final int comboPointDelta;
    public final Target target;

    private RewardReceipt(String transactionId, List<RewardBooking> bookings, Target target) {
        this.transactionId = transactionId == null ? "" : transactionId;
        this.bookings = Collections.unmodifiableList(new ArrayList<>(bookings));
        this.target = target == null ? Target.NONE : target;
        int xpTotal = 0;
        int comboTotal = 0;
        for (RewardBooking booking : bookings) {
            if (matches(booking.target, this.target)) {
                xpTotal += booking.xpDelta;
                comboTotal += booking.comboPointDelta;
            }
        }
        this.xp = xpTotal;
        this.comboPointDelta = comboTotal;
    }

    public static RewardReceipt of(String transactionId, List<RewardBooking> bookings,
                                   Target target) {
        if (bookings == null || bookings.isEmpty()) return none();
        if (transactionId == null || transactionId.trim().isEmpty())
            throw new IllegalArgumentException("Reward receipt needs a transaction id");
        for (RewardBooking booking : bookings)
            if (!transactionId.equals(booking.transactionId))
                throw new IllegalArgumentException("Receipt cannot mix reward transactions");
        return new RewardReceipt(transactionId, bookings, target);
    }

    public static RewardReceipt none() {
        return new RewardReceipt("", Collections.emptyList(), Target.NONE);
    }

    private static boolean matches(RewardBooking.Target bookingTarget, Target receiptTarget) {
        return receiptTarget == Target.VESSEL && bookingTarget == RewardBooking.Target.VESSEL
                || receiptTarget == Target.HEAD && bookingTarget == RewardBooking.Target.HEAD;
    }
}
