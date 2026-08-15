package de.thonktank.autosecretary.infrastructure;

import android.util.Log;

public final class AndroidAppLogger implements AppLogger {
    @Override public void info(String tag, String message) {
        Log.i(tag, message);
    }

    @Override public void error(String tag, String message, Throwable error) {
        Log.e(tag, message, error);
    }
}
