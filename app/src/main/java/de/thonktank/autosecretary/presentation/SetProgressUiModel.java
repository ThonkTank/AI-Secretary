package de.thonktank.autosecretary.presentation;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Set-specific data needed by dashboard progress controls. */
public final class SetProgressUiModel {
    public final int plannedSets;
    public final int plannedRepetitions;
    @NonNull public final String note;
    @NonNull public final List<Integer> actualRepetitions;

    public SetProgressUiModel(int plannedSets, int plannedRepetitions,
                              @NonNull String note,
                              @NonNull List<Integer> actualRepetitions) {
        if (plannedSets <= 0 || plannedRepetitions <= 0)
            throw new IllegalArgumentException("Set progress needs positive targets");
        this.plannedSets = plannedSets;
        this.plannedRepetitions = plannedRepetitions;
        this.note = note;
        this.actualRepetitions = Collections.unmodifiableList(
                new ArrayList<>(actualRepetitions));
    }

    public int nextSetNumber() { return actualRepetitions.size() + 1; }
}
