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

    public FocusCardUiModel(@NonNull FocusTaskUiModel task,
                     @NonNull DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState repetitionInput) {
        this.task = task;
        this.palette = palette;
        this.stepLimit = stepLimit == null ? FocusStepLimit.AUTO : stepLimit;
        this.repetitionInput = repetitionInput == null
                ? RepetitionInputState.idle() : repetitionInput;
    }
}
