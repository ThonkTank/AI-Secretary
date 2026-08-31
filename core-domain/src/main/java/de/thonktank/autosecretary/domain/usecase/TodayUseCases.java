package de.thonktank.autosecretary.domain.usecase;

/** Focused application commands and queries for today's execution. */
public final class TodayUseCases {
    public final DeferTask defer;
    public final ToggleStep toggleStep;
    public final AdvanceTodayStep advanceTodayStep;
    public final MoveTodayStep moveTodayStep;
    public final RecordRepetitionResult recordRepetitionResult;
    public final CorrectRepetitionResult correctRepetitionResult;
    public final RecordSetResult recordSetResult;
    public final CorrectSetResult correctSetResult;
    public final FinishStepForToday finishStepForToday;
    public final CompleteOccurrence complete;
    public final CompleteRemainingSteps completeRemainingSteps;
    public final HarvestOccurrence harvest;
    public final UndoOccurrence undoOccurrence;
    public final ApplyComboDecay applyComboDecay;
    public final SettlePreviousPartialOccurrences settlePreviousPartialOccurrences;
    public final CloseOngoingTask closeOngoing;
    public final MaterializeDueOccurrences materializeDue;
    public final LoadDashboard loadDashboard;

    public TodayUseCases(DeferTask defer, ToggleStep toggleStep,
                         AdvanceTodayStep advanceTodayStep, MoveTodayStep moveTodayStep,
                         RecordRepetitionResult recordRepetitionResult,
                         CorrectRepetitionResult correctRepetitionResult,
                         RecordSetResult recordSetResult, CorrectSetResult correctSetResult,
                         FinishStepForToday finishStepForToday, CompleteOccurrence complete,
                         CompleteRemainingSteps completeRemainingSteps,
                         HarvestOccurrence harvest, UndoOccurrence undoOccurrence,
                         ApplyComboDecay applyComboDecay,
                         SettlePreviousPartialOccurrences settlePreviousPartialOccurrences,
                         CloseOngoingTask closeOngoing,
                         MaterializeDueOccurrences materializeDue,
                         LoadDashboard loadDashboard) {
        this.defer = defer;
        this.toggleStep = toggleStep;
        this.advanceTodayStep = advanceTodayStep;
        this.moveTodayStep = moveTodayStep;
        this.recordRepetitionResult = recordRepetitionResult;
        this.correctRepetitionResult = correctRepetitionResult;
        this.recordSetResult = recordSetResult;
        this.correctSetResult = correctSetResult;
        this.finishStepForToday = finishStepForToday;
        this.complete = complete;
        this.completeRemainingSteps = completeRemainingSteps;
        this.harvest = harvest;
        this.undoOccurrence = undoOccurrence;
        this.applyComboDecay = applyComboDecay;
        this.settlePreviousPartialOccurrences = settlePreviousPartialOccurrences;
        this.closeOngoing = closeOngoing;
        this.materializeDue = materializeDue;
        this.loadDashboard = loadDashboard;
    }
}
