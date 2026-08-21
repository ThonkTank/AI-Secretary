package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.LocalDate;
import java.util.List;

/**
 * Persistence port for Today/execution use cases. Management use cases depend on their
 * focused schedule or step ports instead.
 */
public interface TaskRepository extends TaskDefinitionRepository {

    void insertOccurrence(Occurrence occurrence);
    Occurrence findOccurrence(String id);
    Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn, TaskSlot slot);
    List<Occurrence> openOccurrences(TaskId taskId, LocalDate scheduledOn);
    Occurrence openOccurrence(TaskId taskId);
    List<Occurrence> openOccurrences();
    List<Occurrence> allOccurrences();
    List<Occurrence> occurrences(TaskId taskId);
    Occurrence earliestOpenOccurrence(TaskId taskId);
    Occurrence latestCompletedOccurrence(TaskId taskId);
    List<Occurrence> completedOccurrences(LocalDate date);
    void updateOccurrence(Occurrence occurrence);
    List<Occurrence> openOccurrences(TaskId taskId);
    List<Occurrence> openOccurrences(TaskSlot slot);
    void insertOccurrenceSteps(List<OccurrenceStep> steps);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds);
    OccurrenceStep findOccurrenceStep(String id);
    void updateOccurrenceStep(OccurrenceStep step);

    int xp();
    void setXp(int xp);

    List<ComboProgress> combos();
    ComboProgress combo(String ownerId);
    void putCombo(ComboProgress combo);

    List<TaskScheduleEntry> scheduleEntries();

    void insertRewardBooking(RewardBooking booking);
    List<RewardBooking> rewardBookings(String occurrenceId);
    List<RewardBooking> rewardBookings(List<String> occurrenceIds);
}
