package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.StepAmountKind;
import de.thonktank.autosecretary.domain.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

public final class EditStepProgress {
    private final TaskRepository repository;
    private final Clock clock;
    private final RewardEngine rewards;
    public EditStepProgress(TaskRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock; rewards = new RewardEngine(repository, clock);
    }

    public RewardResult execute(String stepId, List<Integer> repetitions) {
        final RewardResult[] result = {RewardResult.none()};
        repository.inTransaction(() -> {
            OccurrenceStep current = repository.findOccurrenceStep(stepId);
            if (current == null || current.amountKind != StepAmountKind.SETS_REPS) return;
            Occurrence occurrence = repository.findOccurrence(current.occurrenceId);
            if (occurrence == null || occurrence.state != OccurrenceState.OPEN) return;
            List<Integer> checked = new ArrayList<>();
            for (Integer value : repetitions) {
                if (value == null || value <= 0)
                    throw new IllegalArgumentException("Confirmed repetitions must be positive");
                checked.add(value);
            }
            if (current.plannedSets != null && checked.size() > current.plannedSets)
                throw new IllegalArgumentException("Confirmed set count exceeds planned sets");
            boolean nextDone = current.plannedSets != null && checked.size() == current.plannedSets;
            if (current.done && !nextDone) {
                ComboProgress combo = repository.combo(current.comboOwnerId);
                if (combo != null) repository.putCombo(combo.undo(current.comboPointDelta, clock.today()));
                repository.updateOccurrenceStep(copy(current, checked, false, 0, 0));
                result[0] = new RewardResult(current.earnedXp, RewardResult.Target.VESSEL, true);
            } else if (!current.done && nextDone) {
                repository.updateOccurrenceStep(copy(current, checked, true, 0, 0));
                result[0] = rewards.completeStep(occurrence, repository.findOccurrenceStep(current.id));
            } else repository.updateOccurrenceStep(copy(current, checked, nextDone,
                    current.earnedXp, current.comboPointDelta));
        });
        return result[0];
    }

    private static OccurrenceStep copy(OccurrenceStep value, List<Integer> repetitions,
                                       boolean done, int xp, int delta) {
        return new OccurrenceStep(value.id, value.occurrenceId, value.position, value.text,
                done, value.amountKind, value.plannedSets, value.plannedReps,
                value.plannedDurationSeconds, value.note, repetitions, value.comboOwnerId,
                xp, delta);
    }
}
