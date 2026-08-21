package de.thonktank.autosecretary.domain.repository;

import java.time.LocalDate;
import java.util.List;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;

/** Read-only capability required to assemble the Today dashboard domain model. */
public interface DashboardReadRepository {
    List<Task> allTasks();
    List<Occurrence> openOccurrences();
    List<Occurrence> completedOccurrences(LocalDate date);
    List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds);
    List<RewardBooking> rewardBookings(List<String> occurrenceIds);
    List<TaskScheduleEntry> scheduleEntries();
    List<ComboProgress> combos();
    int xp();
}
