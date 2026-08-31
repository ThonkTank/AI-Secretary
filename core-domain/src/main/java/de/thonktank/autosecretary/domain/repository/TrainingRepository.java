package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingLoadRequest;
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup;

import java.time.LocalDate;
import java.util.List;

/** Narrow persistence capability for detailed training logs and adaptation audit. */
public interface TrainingRepository {
    TaskStepTemplate findTemplate(String id);
    void updateTrainingTemplate(TaskStepTemplate template);
    double effectiveSetsSince(TrainingMuscleGroup muscle, LocalDate start, LocalDate end);
    void insertTrainingAdjustment(TrainingAdjustment adjustment);
    TrainingAdjustment latestTrainingAdjustment(String templateId);
    List<TrainingAdjustment> recentTrainingAdjustments(String templateId, int limit);
    void updateTrainingAdjustment(TrainingAdjustment adjustment);
    long nextTrainingAuditOrder();
    void insertTrainingLoadRequest(TrainingLoadRequest request);
    TrainingLoadRequest openTrainingLoadRequest(String templateId);
    List<TrainingLoadRequest> recentTrainingLoadRequests(String templateId, int limit);
    void updateTrainingLoadRequest(TrainingLoadRequest request);
}
