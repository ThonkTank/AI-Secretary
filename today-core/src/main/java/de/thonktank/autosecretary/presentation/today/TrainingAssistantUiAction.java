package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;

/** Typed boundary for the four interactions owned by the Today training-assistant panel. */
public abstract class TrainingAssistantUiAction {
    public final String templateId;

    private TrainingAssistantUiAction(String templateId) {
        if (templateId == null || templateId.trim().isEmpty())
            throw new IllegalArgumentException("Training template identity is required");
        this.templateId = templateId;
    }

    public static final class ApplyLoad extends TrainingAssistantUiAction {
        public final String rawLoad;
        public final ResistanceLoad.Mode currentMode;
        public final ResistanceLoad.Unit currentUnit;

        public ApplyLoad(String templateId, String rawLoad,
                         ResistanceLoad.Mode currentMode, ResistanceLoad.Unit currentUnit) {
            super(templateId);
            if (currentMode == null || currentUnit == null)
                throw new IllegalArgumentException("Current load kind and unit are required");
            this.rawLoad = rawLoad == null ? "" : rawLoad;
            this.currentMode = currentMode;
            this.currentUnit = currentUnit;
        }
    }

    public static final class NoHigherLoad extends TrainingAssistantUiAction {
        public NoHigherLoad(String templateId) { super(templateId); }
    }

    public static final class Later extends TrainingAssistantUiAction {
        public Later(String templateId) { super(templateId); }
    }

    public static final class Undo extends TrainingAssistantUiAction {
        public Undo(String templateId) { super(templateId); }
    }
}
