package de.thonktank.autosecretary.presentation.shell;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.NavigationDestination;

/** Atomic state for top-level selection and shared legacy-shell appearance. */
public final class AppShellScreenState {
    public final NavigationDestination navigation;
    public final DayPalette palette;
    public final long nowEpochMillis;

    public AppShellScreenState(NavigationDestination navigation, DayPalette palette) {
        this(navigation, palette, 0L);
    }

    public AppShellScreenState(NavigationDestination navigation, DayPalette palette,
                               long nowEpochMillis) {
        if (navigation == null || palette == null)
            throw new IllegalArgumentException("Complete shell state is required");
        if (nowEpochMillis < 0L)
            throw new IllegalArgumentException("Presentation time is required");
        this.navigation = navigation;
        this.palette = palette;
        this.nowEpochMillis = nowEpochMillis;
    }

    public AppShellScreenState withNavigation(NavigationDestination value) {
        return new AppShellScreenState(value, palette, nowEpochMillis);
    }

    public AppShellScreenState withPalette(DayPalette value) {
        return new AppShellScreenState(navigation, value, nowEpochMillis);
    }

    public AppShellScreenState withAppearance(DayPalette value, long epochMillis) {
        return new AppShellScreenState(navigation, value, epochMillis);
    }
}
