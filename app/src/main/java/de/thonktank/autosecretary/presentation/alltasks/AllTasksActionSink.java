package de.thonktank.autosecretary.presentation.alltasks;

/** Java-compatible input boundary for the Compose management screen. */
@FunctionalInterface
public interface AllTasksActionSink {
    void emit(AllTasksAction action);
}
