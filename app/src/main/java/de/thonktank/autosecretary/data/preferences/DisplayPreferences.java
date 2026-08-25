package de.thonktank.autosecretary.data.preferences;

import de.thonktank.autosecretary.domain.model.ComboPolicy;

/** Immutable display preferences consumed by dashboard presentation state. */
public final class DisplayPreferences {
    public final UiThemeMode themeMode;
    public final FocusStepLimit focusStepLimit;
    public final int restTimerDefaultSeconds;
    public final ComboPolicy comboPolicy;

    public DisplayPreferences(UiThemeMode themeMode, FocusStepLimit focusStepLimit) {
        this(themeMode, focusStepLimit, UiPreferences.DEFAULT_REST_TIMER_SECONDS);
    }

    public DisplayPreferences(UiThemeMode themeMode, FocusStepLimit focusStepLimit,
                              int restTimerDefaultSeconds) {
        this(themeMode, focusStepLimit, restTimerDefaultSeconds, ComboPolicy.defaults());
    }

    public DisplayPreferences(UiThemeMode themeMode, FocusStepLimit focusStepLimit,
                              int restTimerDefaultSeconds, ComboPolicy comboPolicy) {
        if (themeMode == null || focusStepLimit == null)
            throw new IllegalArgumentException("Display preferences are required");
        if (restTimerDefaultSeconds < 1)
            throw new IllegalArgumentException("Rest timer default must be positive");
        this.themeMode = themeMode;
        this.focusStepLimit = focusStepLimit;
        this.restTimerDefaultSeconds = restTimerDefaultSeconds;
        this.comboPolicy = comboPolicy == null ? ComboPolicy.defaults() : comboPolicy;
    }
}
