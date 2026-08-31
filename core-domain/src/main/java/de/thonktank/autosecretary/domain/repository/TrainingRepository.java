package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;

import java.time.LocalDate;

/** Narrow persistence capability for detailed training logs and adaptation audit. */
public interface TrainingRepository extends TransactionalRepository {
    TaskStepTemplate findTemplate(String id);
    void updateTrainingTemplate(TaskStepTemplate template);
    double effectiveSetsSince(TrainingMuscleGroup muscle, LocalDate start, LocalDate end);
    void insertTrainingAdjustment(TrainingAdjustment adjustment);
    TrainingAdjustment latestTrainingAdjustment(String templateId);
    void updateTrainingAdjustment(TrainingAdjustment adjustment);
}
