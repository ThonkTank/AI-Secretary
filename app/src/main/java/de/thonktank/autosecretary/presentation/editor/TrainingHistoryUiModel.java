package de.thonktank.autosecretary.presentation.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable editor-only history for one persisted training step. */
public final class TrainingHistoryUiModel {
    public final String templateId;
    public final List<String> entries;
    public final boolean canUndo;

    public TrainingHistoryUiModel(String templateId, List<String> entries, boolean canUndo) {
        if (templateId == null || templateId.isEmpty() || entries == null)
            throw new IllegalArgumentException("Complete training history is required");
        this.templateId = templateId;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        this.canUndo = canUndo;
    }
}
