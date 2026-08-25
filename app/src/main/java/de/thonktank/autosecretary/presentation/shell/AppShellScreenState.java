package de.thonktank.autosecretary.presentation.shell;

import de.thonktank.autosecretary.DayPalette;
import de.thonktank.autosecretary.NavigationDestination;

/** Atomic state for top-level selection and shared legacy-shell appearance. */
public final class AppShellScreenState {
    public final NavigationDestination navigation;
    public final DayPalette palette;

    public AppShellScreenState(NavigationDestination navigation, DayPalette palette) {
        if (navigation == null || palette == null)
            throw new IllegalArgumentException("Complete shell state is required");
        this.navigation = navigation;
        this.palette = palette;
    }

    public AppShellScreenState withNavigation(NavigationDestination value) {
        return new AppShellScreenState(value, palette);
    }

    public AppShellScreenState withPalette(DayPalette value) {
        return new AppShellScreenState(navigation, value);
    }
}
