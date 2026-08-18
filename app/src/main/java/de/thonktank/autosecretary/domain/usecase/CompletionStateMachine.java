package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;

import java.time.LocalDate;

/** Pure state transitions for completion and reopening. */
public final class CompletionStateMachine {
    public OccurrenceStep completeStep(Occurrence occurrence, OccurrenceStep step) {
        requireOpen(occurrence);
        if (step == null || !occurrence.id.equals(step.occurrenceId))
            throw new IllegalStateException("Step does not belong to the open occurrence");
        return step.complete();
    }

    public OccurrenceStep reopenStep(Occurrence occurrence, OccurrenceStep step) {
        requireOpen(occurrence);
        if (step == null || !occurrence.id.equals(step.occurrenceId) || !step.done)
            throw new IllegalStateException("Only a completed step can be reopened");
        return step.reopen();
    }

    public Occurrence completeOccurrence(Occurrence occurrence, LocalDate completedOn) {
        requireOpen(occurrence);
        return occurrence.complete(completedOn);
    }

    public Occurrence reopenOccurrence(Occurrence occurrence) {
        if (occurrence == null || occurrence.state != OccurrenceState.COMPLETED)
            throw new IllegalStateException("Only a completed occurrence can be reopened");
        return occurrence.reopen();
    }

    private static void requireOpen(Occurrence occurrence) {
        if (occurrence == null || occurrence.state != OccurrenceState.OPEN)
            throw new IllegalStateException("Completion requires an open occurrence");
    }
}
