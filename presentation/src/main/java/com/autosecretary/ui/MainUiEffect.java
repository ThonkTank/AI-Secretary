package com.autosecretary.ui;

/** Transient, identified effects kept out of idempotent screen rendering. */
public sealed interface MainUiEffect permits MainUiEffect.Completion, MainUiEffect.Error {
    long id();

    record Completion(long id) implements MainUiEffect { }

    record Error(long id, String message) implements MainUiEffect {
        public Error {
            if (message == null || message.isBlank()) message = "Unbekannter Fehler";
        }
    }
}
