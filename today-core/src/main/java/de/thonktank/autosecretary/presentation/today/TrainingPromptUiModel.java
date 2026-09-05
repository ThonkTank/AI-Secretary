package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.TrainingDecision;

/** Minimal actionable training question rendered by the Today focus row. */
public final class TrainingPromptUiModel {
    public final String templateId;
    public final TrainingDecision.LoadDirection direction;
    public final ResistanceLoad currentLoad;

    public TrainingPromptUiModel(String templateId,
                                 TrainingDecision.LoadDirection direction,
                                 ResistanceLoad currentLoad) {
        if (templateId == null || templateId.isEmpty()
                || direction == null || currentLoad == null)
            throw new IllegalArgumentException("Complete training prompt is required");
        this.templateId = templateId;
        this.direction = direction;
        this.currentLoad = currentLoad;
    }
}
