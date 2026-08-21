package de.thonktank.autosecretary.presentation.today;

import androidx.annotation.NonNull;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.TaskSnapshot;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

/** Complete, immutable input for one focus-card render. */
public final class FocusCardUiModel {
    @NonNull public final TaskSnapshot task;
    public final boolean allowDefer;
    @NonNull public final DayPalette palette;
    @NonNull public final FocusStepLimit stepLimit;
    @NonNull public final RepetitionInputState repetitionInput;

    public FocusCardUiModel(@NonNull TaskSnapshot task, boolean allowDefer,
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
