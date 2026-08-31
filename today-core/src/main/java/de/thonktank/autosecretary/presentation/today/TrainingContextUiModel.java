package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;
import de.thonktank.autosecretary.domain.model.TrainingDecision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Localized, bounded training assistant content for one Today row. */
public final class TrainingContextUiModel {
    public final String templateId;
    public final String statusLabel;
    public final String latestAdjustmentLabel;
    public final TrainingDecision.LoadDirection openDirection;
    public final ResistanceLoad openCurrentLoad;
    public final List<String> historyLabels;
    public final boolean canUndo;

    public TrainingContextUiModel(String templateId, String statusLabel,
                                  String latestAdjustmentLabel,
                                  TrainingDecision.LoadDirection openDirection,
                                  ResistanceLoad openCurrentLoad,
                                  List<String> historyLabels, boolean canUndo) {
        if (templateId == null || templateId.isEmpty() || statusLabel == null
                || latestAdjustmentLabel == null || historyLabels == null
                || (openDirection == null) != (openCurrentLoad == null))
            throw new IllegalArgumentException("Complete training UI context is required");
        this.templateId = templateId;
        this.statusLabel = statusLabel;
        this.latestAdjustmentLabel = latestAdjustmentLabel;
        this.openDirection = openDirection;
        this.openCurrentLoad = openCurrentLoad;
        this.historyLabels = Collections.unmodifiableList(new ArrayList<>(historyLabels));
        this.canUndo = canUndo;
    }

    public boolean hasOpenLoadRequest() { return openDirection != null; }
}
