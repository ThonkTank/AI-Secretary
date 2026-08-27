package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;

interface FlowProgression {
    FlowProgression NONE = new FlowProgression() { };

    default void onStepCompleted(Occurrence occurrence, OccurrenceStep step,
                                 Long chosenDelayMillis) { }
    default boolean canReopenStep(Occurrence occurrence, OccurrenceStep step) { return true; }
    default void onStepReopened(Occurrence occurrence, OccurrenceStep step) { }
    default void onOccurrenceHarvested(Occurrence occurrence) { }
    default boolean canReopenOccurrence(Occurrence occurrence) { return true; }
    default void onOccurrenceReopened(Occurrence occurrence) { }
}
