package de.thonktank.autosecretary.timer;

public interface TimerScheduler {
    /** Returns true when Android accepted an exact wake-up alarm. */
    boolean schedule(TimerSession session);
    void cancel(TimerSession session);
    boolean exactAlarmsAvailable();
}
