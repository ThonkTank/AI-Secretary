package de.thonktank.autosecretary;

import androidx.annotation.NonNull;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

/** Complete, immutable input for one focus-card render. */
final class FocusCardUiModel {
    @NonNull final TaskSnapshot task;
    final boolean allowDefer;
    @NonNull final DayPalette palette;
    @NonNull final FocusStepLimit stepLimit;
    @NonNull final RepetitionInputState repetitionInput;

    FocusCardUiModel(@NonNull TaskSnapshot task, boolean allowDefer,
                     @NonNull DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState repetitionInput) {
        this.task = task;
        this.allowDefer = allowDefer;
        this.palette = palette;
        this.stepLimit = stepLimit == null ? FocusStepLimit.AUTO : stepLimit;
        this.repetitionInput = repetitionInput == null
                ? RepetitionInputState.idle() : repetitionInput;
    }
}
