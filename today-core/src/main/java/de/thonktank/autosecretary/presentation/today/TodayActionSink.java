package de.thonktank.autosecretary.presentation.today;

@FunctionalInterface
public interface TodayActionSink {
    void emit(TodayAction action);
}
