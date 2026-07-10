package com.autosecretary.testing;

import android.os.Looper;

import org.robolectric.Shadows;

public final class RobolectricDrain {
    private RobolectricDrain() {
    }

    public static void mainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
