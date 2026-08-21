package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.today.TodayStepPositionUpdate;

import java.util.List;

/** Focused persistence port for ordering execution snapshots without touching templates/results. */
public interface TodayStepOrderRepository extends TransactionalRepository {
    Occurrence findOccurrence(String id);
    OccurrenceStep findOccurrenceStep(String id);
    List<OccurrenceStep> occurrenceSteps(String occurrenceId);
    void updateOccurrenceStepPositions(List<TodayStepPositionUpdate> updates);
}
