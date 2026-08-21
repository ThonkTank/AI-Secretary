package de.thonktank.autosecretary.domain.today;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable reorder input captured at one repository transaction boundary. */
public final class TodayOccurrenceSnapshot {
    public final Occurrence occurrence;
    public final List<OccurrenceStep> steps;
    final OccurrenceStep resolvedTarget;

    public TodayOccurrenceSnapshot(Occurrence occurrence, List<OccurrenceStep> steps,
                                   OccurrenceStep resolvedTarget) {
        this.occurrence = occurrence;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.resolvedTarget = resolvedTarget;
    }
}
