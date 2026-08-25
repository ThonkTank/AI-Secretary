package de.thonktank.autosecretary.timer;

public final class AndroidTimerClock implements TimerClock {
    @Override public long elapsedRealtime() {
        return android.os.SystemClock.elapsedRealtime();
    }

    @Override public long epochMillis() {
        return System.currentTimeMillis();
    }
}
