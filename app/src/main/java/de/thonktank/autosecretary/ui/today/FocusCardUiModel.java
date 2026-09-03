package de.thonktank.autosecretary.ui.today;


import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.RepetitionInputState;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.presentation.today.FocusStepListUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.timer.TimerManager;

/** Complete, immutable input for one focus-card render. */
public final class FocusCardUiModel {
    public final FocusTaskUiModel task;
    public final FocusStepListUiModel steps;
    public final DayPalette palette;
    public final FocusStepLimit stepLimit;
    public final RepetitionInputState repetitionInput;
    public final TodayFeatureState.Reorder reorder;
    public final TimerManager.Snapshot timers;

    public FocusCardUiModel(FocusTaskUiModel task, FocusStepListUiModel steps,
                     DayPalette palette, FocusStepLimit stepLimit,
                     RepetitionInputState repetitionInput,
                     TodayFeatureState.Reorder reorder,
                     TimerManager.Snapshot timers) {
        if (task == null || steps == null || palette == null || reorder == null)
            throw new IllegalArgumentException("Complete focus-card state is required");
        if (!task.occurrenceId().equals(steps.occurrenceId))
            throw new IllegalArgumentException("Focus task and projected rows must match");
        this.task = task;
        this.steps = steps;
        this.palette = palette;
        this.stepLimit = stepLimit == null ? FocusStepLimit.AUTO : stepLimit;
        this.repetitionInput = repetitionInput == null
                ? RepetitionInputState.idle() : repetitionInput;
        this.reorder = reorder;
        this.timers = timers == null ? TimerManager.Snapshot.empty() : timers;
    }
}
