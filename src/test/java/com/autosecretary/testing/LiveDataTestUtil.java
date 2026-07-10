package com.autosecretary.testing;

import androidx.lifecycle.LiveData;

public final class LiveDataTestUtil {
    private LiveDataTestUtil() {
    }

    public static <T> T valueOf(LiveData<T> liveData) {
        RobolectricDrain.mainLooper();
        return liveData.getValue();
    }
}
