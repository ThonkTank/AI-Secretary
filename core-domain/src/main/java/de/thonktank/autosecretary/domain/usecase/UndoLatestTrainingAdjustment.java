package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.TaskStepTemplate;
import de.thonktank.autosecretary.domain.model.TrainingAdjustment;
import de.thonktank.autosecretary.domain.model.TrainingAssistantState;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;

/** Restores the latest adjustment only while its after-state is still current. */
public final class UndoLatestTrainingAdjustment {
    private final TrainingRepository repository;

    public UndoLatestTrainingAdjustment(TrainingRepository repository) {
        this.repository = repository;
    }

    public boolean execute(String templateId) {
        return repository.inTransaction(() -> {
            TaskStepTemplate template = repository.findTemplate(templateId);
            TrainingAdjustment adjustment = repository.latestTrainingAdjustment(templateId);
            if (template == null || adjustment == null
                    || adjustment.state != TrainingAdjustment.State.APPLIED
                    || !template.amount.equals(adjustment.after)
                    || !template.trainingAssistant.load.equals(adjustment.afterLoad)) return false;
            repository.updateTrainingTemplate(template.withTraining(adjustment.before,
                    template.trainingAssistant.withLoad(adjustment.beforeLoad),
                    TrainingAssistantState.calibrating()));
            repository.updateTrainingAdjustment(adjustment.undone());
            return true;
        });
    }
}
