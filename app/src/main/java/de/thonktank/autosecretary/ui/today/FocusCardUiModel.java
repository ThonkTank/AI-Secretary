package de.thonktank.autosecretary.ui.today;


import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;

/** Complete, immutable input for one focus-card render. */
public final class FocusCardUiModel {
    public final FocusTaskUiModel task;
    public final DayPalette palette;
    public final FocusStepLimit stepLimit;
    public final RepetitionInputState repetitionInput;
    public final TodayFeatureState.Reorder reorder;

    public FocusCardUiModel(FocusTaskUiModel task,
                     DayPalette palette, FocusStepLimit stepLimit,
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
