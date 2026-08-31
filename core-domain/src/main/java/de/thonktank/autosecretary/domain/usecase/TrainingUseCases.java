package de.thonktank.autosecretary.domain.usecase;

/** Focused application commands and explainability query for adaptive training. */
public final class TrainingUseCases {
    public final UndoLatestTrainingAdjustment undoLatestTrainingAdjustment;
    public final ResolveTrainingLoadRequest resolveTrainingLoadRequest;
    public final LoadTrainingContext loadTrainingContext;

    public TrainingUseCases(UndoLatestTrainingAdjustment undoLatestTrainingAdjustment,
                            ResolveTrainingLoadRequest resolveTrainingLoadRequest,
                            LoadTrainingContext loadTrainingContext) {
        this.undoLatestTrainingAdjustment = undoLatestTrainingAdjustment;
        this.resolveTrainingLoadRequest = resolveTrainingLoadRequest;
        this.loadTrainingContext = loadTrainingContext;
    }
}
