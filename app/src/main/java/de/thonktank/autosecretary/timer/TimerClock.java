package de.thonktank.autosecretary.timer;

public interface TimerClock {
    long elapsedRealtime();
    long epochMillis();
}
