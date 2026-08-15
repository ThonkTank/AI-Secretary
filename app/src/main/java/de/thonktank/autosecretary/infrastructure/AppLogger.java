package de.thonktank.autosecretary.infrastructure;

public interface AppLogger {
    void info(String tag, String message);
    void error(String tag, String message, Throwable error);
}
