package de.thonktank.autosecretary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded debug-only trace and signal source for presentation transitions. */
public final class PresentationTrace {
    private static final int CAPACITY = 256;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>(CAPACITY);
    private static final CopyOnWriteArrayList<Listener> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private PresentationTrace() { }

    public interface Listener { void onEvent(Event event); }

    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    public static final class Event {
        public final long sequence;
        public final long uptimeMillis;
        public final String thread;
        public final String owner;
        public final String kind;
        public final String detail;

        private Event(long sequence, long uptimeMillis, String thread, String owner,
                      String kind, String detail) {
            this.sequence = sequence;
            this.uptimeMillis = uptimeMillis;
            this.thread = thread;
            this.owner = owner;
            this.kind = kind;
            this.detail = detail;
        }

        @Override public String toString() {
            return sequence + " @" + uptimeMillis + " [" + thread + "] "
                    + owner + '/' + kind + " " + detail;
        }
    }

    public static boolean enabled() { return true; }

    public static void emit(String owner, String kind, String detail) {
        Event event = new Event(SEQUENCE.incrementAndGet(), System.nanoTime() / 1_000_000L,
                Thread.currentThread().getName(), value(owner), value(kind), value(detail));
        synchronized (LOCK) {
            while (EVENTS.size() >= CAPACITY) EVENTS.removeFirst();
            EVENTS.addLast(event);
        }
        for (Listener listener : LISTENERS) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) { }
        }
    }

    public static Subscription subscribe(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Trace listener is required");
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static List<Event> snapshot() {
        synchronized (LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(EVENTS));
        }
    }

    public static String describe() {
        StringBuilder result = new StringBuilder();
        for (Event event : snapshot()) result.append(event).append('\n');
        return result.toString();
    }

    public static void clear() {
        synchronized (LOCK) {
            EVENTS.clear();
        }
    }

    private static String value(String value) { return value == null ? "" : value; }
}
