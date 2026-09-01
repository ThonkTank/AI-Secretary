package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.util.List;

/** Reusable templates, materialized steps, ordering and their atomic set results. */
public interface StepRepository {
    void insertTemplates(List<TaskStepTemplate> steps);
    void updateTemplate(TaskStepTemplate template);
    void deleteTemplates(TaskId taskId);
    void deleteTemplate(String id);
    List<TaskStepTemplate> templates(TaskId taskId);
    TaskStepTemplate findTemplate(String id);
    List<TaskStepTemplate> templatesFor(List<TaskId> taskIds);
    void insertOccurrenceSteps(List<OccurrenceStep> steps);
    OccurrenceStep findOccurrenceStep(String id);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    List<OccurrenceStep> occurrenceStepsFor(List<String> occurrenceIds);
    void updateOccurrenceStep(OccurrenceStep step);
    void deleteOccurrenceStep(String id);
    void updateOccurrenceStepPositions(List<TodayStepPositionUpdate> updates);
}
