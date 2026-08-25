package de.thonktank.autosecretary.presentation.options;

@FunctionalInterface
public interface OptionsActionSink {
    void emit(OptionsAction action);
}
