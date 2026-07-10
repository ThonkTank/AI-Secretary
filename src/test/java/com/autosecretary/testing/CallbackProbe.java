package com.autosecretary.testing;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class CallbackProbe<T> {
    private final AtomicBoolean called = new AtomicBoolean();
    private final AtomicReference<T> value = new AtomicReference<>();

    public Runnable runnable() {
        return () -> called.set(true);
    }

    public Consumer<T> consumer() {
        return result -> {
            value.set(result);
            called.set(true);
        };
    }

    public T value() {
        assertTrue("callback was not called", called.get());
        return value.get();
    }

    public void assertCalled() {
        assertTrue("callback was not called", called.get());
    }
}
