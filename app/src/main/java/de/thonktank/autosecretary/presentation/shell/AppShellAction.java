package de.thonktank.autosecretary.presentation.shell;

import de.thonktank.autosecretary.NavigationDestination;

/** Closed input boundary for the temporary legacy app shell. */
public abstract class AppShellAction {
    private AppShellAction() { }

    public static final class DestinationSelected extends AppShellAction {
        public final NavigationDestination destination;
        private DestinationSelected(NavigationDestination destination) {
            if (destination == null)
                throw new IllegalArgumentException("Shell destination is required");
            this.destination = destination;
        }
    }

    public static AppShellAction destinationSelected(NavigationDestination destination) {
        return new DestinationSelected(destination);
    }
}
