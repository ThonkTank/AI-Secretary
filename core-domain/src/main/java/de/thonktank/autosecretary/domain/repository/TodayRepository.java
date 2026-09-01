package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.LocalDate;
import java.util.List;

/** Occurrences, materialization state, rewards, combos and obligations. */
public interface TodayRepository {
    void insertOccurrence(Occurrence occurrence);
    void updateOccurrence(Occurrence occurrence);
    void deleteOccurrence(String id);
    Occurrence findOccurrence(String id);
    Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn, TaskSlot slot);
    List<Occurrence> openOccurrences(TaskId taskId, LocalDate scheduledOn);
    List<Occurrence> openOccurrences(TaskId taskId);
    Occurrence openOccurrence(TaskId taskId, TaskSlot slot);
    Occurrence openOccurrence(TaskId taskId);
    List<Occurrence> openOccurrences();
    List<Occurrence> openOccurrences(TaskSlot slot);
    List<Occurrence> allOccurrences();
    List<Occurrence> occurrences(TaskId taskId);
    Occurrence earliestOpenOccurrence(TaskId taskId);
    Occurrence latestCompletedOccurrence(TaskId taskId);
    List<Occurrence> completedOccurrences(LocalDate date);
    void assignRewardBookings(String occurrenceStepId, String occurrenceId);
    int xp();
    void setXp(int xp);
    ComboProgress combo(String ownerId);
    void putCombo(ComboProgress combo);
    List<ComboProgress> combos();
    void insertRewardBooking(RewardBooking booking);
    List<RewardBooking> rewardBookings(String occurrenceId);
    List<RewardBooking> rewardBookings(List<String> occurrenceIds);
    List<ComboObligation> comboObligations();
    void insertComboObligations(List<ComboObligation> obligations);
    void updateComboObligation(ComboObligation obligation);
    ComboDecayEvent comboDecayEvent(String ownerId, LocalDate eventOn);
    void insertComboDecayEvent(ComboDecayEvent event);
}
