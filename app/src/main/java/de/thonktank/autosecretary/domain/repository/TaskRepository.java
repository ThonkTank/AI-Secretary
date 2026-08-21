package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends TaskDefinitionRepository {

    void insertOccurrence(Occurrence occurrence);
    void updateOccurrence(Occurrence occurrence);
    Occurrence findOccurrence(String id);
    Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn, TaskSlot slot);
    List<Occurrence> openOccurrences(TaskId taskId, LocalDate scheduledOn);
    List<Occurrence> openOccurrences(TaskId taskId);
    Occurrence openOccurrence(TaskId taskId, TaskSlot slot);
    Occurrence openOccurrence(TaskId taskId);
    List<Occurrence> openOccurrences();
    List<Occurrence> allOccurrences();
    List<Occurrence> occurrences(TaskId taskId);
    Occurrence earliestOpenOccurrence(TaskId taskId);
    Occurrence latestCompletedOccurrence(TaskId taskId);
    List<Occurrence> completedOccurrences(LocalDate date);
    void insertOccurrenceSteps(List<OccurrenceStep> steps);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds);
    OccurrenceStep findOccurrenceStep(String id);
    void updateOccurrenceStep(OccurrenceStep step);
    void assignRewardBookings(String occurrenceStepId, String occurrenceId);

    int xp();
    void setXp(int xp);

    ComboProgress combo(String ownerId);
    void putCombo(ComboProgress combo);
    List<ComboProgress> combos();

    void insertRewardBooking(RewardBooking booking);
    List<RewardBooking> rewardBookings(String occurrenceId);
    List<RewardBooking> rewardBookings(List<String> occurrenceIds);
}
