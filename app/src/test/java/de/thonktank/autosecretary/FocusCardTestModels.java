package de.thonktank.autosecretary;

import java.util.Collections;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.domain.model.XpProgress;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.timer.TimerManager;
import de.thonktank.autosecretary.ui.today.FocusCardUiModel;

/** Complete focus-card inputs used by Android rendering tests. */
final class FocusCardTestModels {
    private FocusCardTestModels() { }

    static FocusCardUiModel of(FocusTaskUiModel task, DayPalette palette) {
        return of(task, palette, FocusStepLimit.AUTO, RepetitionInputState.idle(),
                TimerManager.Snapshot.empty());
    }

    static FocusCardUiModel of(FocusTaskUiModel task, DayPalette palette,
                               FocusStepLimit limit, RepetitionInputState input) {
        return of(task, palette, limit, input, TimerManager.Snapshot.empty());
    }

    static FocusCardUiModel of(FocusTaskUiModel task, DayPalette palette,
                               FocusStepLimit limit, RepetitionInputState input,
                               TimerManager.Snapshot timers) {
        TodayUiModel today = new TodayUiModel(new XpProgress(0), task,
                Collections.emptyList(), Collections.emptyList());
        return of(TodayFeatureState.idle(today), palette, limit, input, timers);
    }

    static FocusCardUiModel of(TodayFeatureState state, DayPalette palette,
                               FocusStepLimit limit, RepetitionInputState input,
                               TimerManager.Snapshot timers) {
        return new FocusCardUiModel(state.today.focus, state.focus, palette, limit, input,
                state.reorder, timers);
    }
}
