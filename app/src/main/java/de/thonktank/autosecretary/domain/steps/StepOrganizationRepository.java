package de.thonktank.autosecretary.domain.steps;

import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.repository.TransactionalRepository;

import java.util.List;

/** Minimal persistence port for management-time step transfers. */
public interface StepOrganizationRepository extends TransactionalRepository {
    Task findTask(TaskId id);
    TaskStepTemplate findTemplate(String id);
    List<TaskStepTemplate> templates(TaskId taskId);
    void insertTemplates(List<TaskStepTemplate> steps);
    List<Occurrence> openOccurrences(TaskId taskId);
    Occurrence openOccurrence(TaskId taskId, TaskSlot slot);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    void updateOccurrenceStep(OccurrenceStep step);
    void assignRewardBookings(String occurrenceStepId, String occurrenceId);
    ComboProgress combo(String ownerId);
    void putCombo(ComboProgress combo);
}
