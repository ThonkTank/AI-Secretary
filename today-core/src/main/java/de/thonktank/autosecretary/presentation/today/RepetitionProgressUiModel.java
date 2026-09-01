package de.thonktank.autosecretary.presentation.today;

import de.thonktank.autosecretary.domain.model.ResistanceLoad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Presentation-ready repetition slots for a set-based or single-value step. */
public final class RepetitionProgressUiModel {
    public enum Kind { SETS, SINGLE }

    public final Kind kind;
    public final int slotCount;
    public final int plannedRepetitions;
    public final List<Integer> repetitions;
    public final ResistanceLoad plannedLoad;
    public final int targetRir;

    private RepetitionProgressUiModel(Kind kind, int slotCount,
                                      int plannedRepetitions,
                                      List<Integer> repetitions,
                                      ResistanceLoad plannedLoad, int targetRir) {
        if (slotCount <= 0 || plannedRepetitions <= 0)
            throw new IllegalArgumentException("Repetition progress needs positive targets");
        if (kind == Kind.SINGLE && slotCount != 1)
            throw new IllegalArgumentException("Single repetition progress has one slot");
        if (repetitions.size() > slotCount)
            throw new IllegalArgumentException("Actual repetitions exceed available slots");
        for (Integer value : repetitions)
            if (value == null || value < 0)
                throw new IllegalArgumentException("Actual repetitions must not be negative");
        this.kind = kind;
        this.slotCount = slotCount;
        this.plannedRepetitions = plannedRepetitions;
        this.repetitions = Collections.unmodifiableList(new ArrayList<>(repetitions));
        this.plannedLoad = plannedLoad == null ? ResistanceLoad.unspecified() : plannedLoad;
        if (targetRir < 0 || targetRir > 5)
            throw new IllegalArgumentException("Target RIR must be between zero and five");
        this.targetRir = targetRir;
    }

    public static RepetitionProgressUiModel sets(int sets, int repetitions,
                                                  List<Integer> actual) {
        return new RepetitionProgressUiModel(Kind.SETS, sets, repetitions, actual,
                ResistanceLoad.unspecified(), 2);
    }

    public static RepetitionProgressUiModel trainingSets(int sets, int repetitions,
                                                          List<Integer> actual,
                                                          ResistanceLoad load, int targetRir) {
        return new RepetitionProgressUiModel(Kind.SETS, sets, repetitions, actual,
                load, targetRir);
    }

    public static RepetitionProgressUiModel single(int repetitions,
                                                    List<Integer> actual) {
        return new RepetitionProgressUiModel(Kind.SINGLE, 1, repetitions, actual,
                ResistanceLoad.unspecified(), 2);
    }

    public int nextSlotNumber() { return repetitions.size() + 1; }
    public boolean showsBars() { return kind == Kind.SETS; }
    public boolean detailedTraining() {
        return kind == Kind.SETS && plannedLoad.mode != ResistanceLoad.Mode.UNSPECIFIED;
    }
}
