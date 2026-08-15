package de.thonktank.autosecretary.update.application;

/** Creates one executor for each update ViewModel lifecycle. */
public interface UpdateExecutorFactory {
    UpdateExecutor create();
}
