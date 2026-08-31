package de.thonktank.autosecretary.domain.schedule;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry;
import de.thonktank.autosecretary.domain.model.TaskSlot;

import java.time.LocalDate;
import java.util.List;

/** Minimal persistence port owned by the scheduling slice. */
public interface TaskScheduleRepository {
    Task findTask(TaskId id);
    TaskScheduleEntry findScheduleEntry(String id);
    List<TaskScheduleEntry> scheduleEntries();
    List<TaskScheduleEntry> scheduleEntries(TaskId taskId);
    List<TaskScheduleEntry> scheduleEntries(TaskSlot slot);
    void putScheduleEntries(List<TaskScheduleEntry> entries);
    void deleteScheduleEntry(String id);
    Occurrence openOccurrence(TaskId taskId, TaskSlot slot);
    List<Occurrence> openOccurrences(TaskSlot slot);
    Occurrence findOccurrence(TaskId taskId, LocalDate scheduledOn, TaskSlot slot);
    void updateOccurrence(Occurrence occurrence);
}
