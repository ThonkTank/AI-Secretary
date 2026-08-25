package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.presentation.shell.AppShellScreenState;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayScreenState;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.timer.TimerManager;

import java.util.Collections;

public final class TodayScreenStateFixtures {
    private TodayScreenStateFixtures() { }

    public static AppShellScreenState shell(NavigationDestination navigation, DayPalette palette) {
        return new AppShellScreenState(navigation, palette);
    }

    public static TodayScreenState today(TodayUiModel today) {
        return today(today, FocusStepLimit.AUTO, TimerManager.Snapshot.empty());
    }

    public static TodayScreenState today(TodayUiModel today, FocusStepLimit focusStepLimit,
                                  TimerManager.Snapshot timers) {
        return new TodayScreenState(TodayFeatureState.idle(today), false,
                Collections.emptySet(), RepetitionInputState.idle(), focusStepLimit,
                timers, new RewardEffectQueue().snapshot(), Collections.emptyList());
    }
}
