package de.thonktank.autosecretary.domain.repository;

import java.util.List;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;

/** Persistence capability for XP, combo progress and immutable reward bookings. */
public interface RewardLedgerRepository {
    int xp();
    void setXp(int xp);
    List<ComboProgress> combos();
    ComboProgress combo(String ownerId);
    void putCombo(ComboProgress combo);
    void insertRewardBooking(RewardBooking booking);
    List<RewardBooking> rewardBookings(String occurrenceId);
    List<RewardBooking> rewardBookings(List<String> occurrenceIds);
}
