package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only explainability projection for one reusable training template. */
public final class TrainingContext {
    public final String templateId;
    public final TrainingAssistantState state;
    public final TrainingLoadRequest openLoadRequest;
    public final TrainingAdjustment latestAdjustment;
    public final List<TrainingHistoryEntry> history;
    public final boolean canUndo;

    public TrainingContext(String templateId, TrainingAssistantState state,
                           TrainingLoadRequest openLoadRequest,
                           TrainingAdjustment latestAdjustment,
                           List<TrainingHistoryEntry> history, boolean canUndo) {
        if (templateId == null || templateId.isEmpty() || state == null || history == null)
            throw new IllegalArgumentException("Complete training context is required");
        this.templateId = templateId;
        this.state = state;
        this.openLoadRequest = openLoadRequest;
        this.latestAdjustment = latestAdjustment;
        this.history = Collections.unmodifiableList(new ArrayList<>(history));
        this.canUndo = canUndo;
    }
}
