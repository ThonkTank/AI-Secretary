package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.NonNull;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

/** Complete, immutable input for one focus-card render. */
public final class FocusCardUiModel {
    @NonNull public final FocusTaskUiModel task;
    @NonNull public final DayPalette palette;
    @NonNull public final FocusStepLimit stepLimit;
    @NonNull public final RepetitionInputState repetitionInput;
    @NonNull public final TodayFeatureState.Reorder reorder;

    public FocusCardUiModel(@NonNull FocusTaskUiModel task,
                     @NonNull DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState repetitionInput,
                     TodayFeatureState.Reorder reorder) {
        this.task = task;
        this.palette = palette;
        this.stepLimit = stepLimit == null ? FocusStepLimit.AUTO : stepLimit;
        this.repetitionInput = repetitionInput == null
                ? RepetitionInputState.idle() : repetitionInput;
        if (reorder == null) throw new IllegalArgumentException("Reorder state is required");
        this.reorder = reorder;
    }
}
