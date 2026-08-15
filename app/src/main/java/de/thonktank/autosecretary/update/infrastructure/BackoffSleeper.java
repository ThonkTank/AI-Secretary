package de.thonktank.autosecretary.update.infrastructure;

interface BackoffSleeper {
    void sleep(long millis) throws InterruptedException;
}
