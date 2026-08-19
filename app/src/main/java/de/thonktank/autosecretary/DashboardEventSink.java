package de.thonktank.autosecretary;

/** Single event boundary shared by dashboard views and their controller. */
@FunctionalInterface
public interface DashboardEventSink {
    void emit(DashboardEvent event);
}
