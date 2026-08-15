package de.thonktank.autosecretary.update.application;

/** Lifecycle-owned execution port for update I/O. */
public interface UpdateExecutor {
    void execute(Runnable task);
    void close();
}
