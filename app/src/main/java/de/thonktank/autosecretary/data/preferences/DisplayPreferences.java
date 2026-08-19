package de.thonktank.autosecretary.data.preferences;

/** Immutable display preferences consumed by dashboard presentation state. */
public final class DisplayPreferences {
    public final UiThemeMode themeMode;
    public final FocusStepLimit focusStepLimit;

    public DisplayPreferences(UiThemeMode themeMode, FocusStepLimit focusStepLimit) {
        if (themeMode == null || focusStepLimit == null)
            throw new IllegalArgumentException("Display preferences are required");
        this.themeMode = themeMode;
        this.focusStepLimit = focusStepLimit;
    }
}
