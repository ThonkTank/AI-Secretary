package de.thonktank.autosecretary.presentation.navigation;

/** Explicit application boundary for navigation requested by a screen owner or host entry. */
@FunctionalInterface
public interface AppNavigator {
    void navigate(AppDestination destination);
}
