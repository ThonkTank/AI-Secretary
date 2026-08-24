package de.thonktank.autosecretary;

import java.util.Collections;
import java.util.List;

/** Release variant: presentation tracing has no storage, logging or runtime listener work. */
public final class PresentationTrace {
    private PresentationTrace() { }

    public interface Listener { void onEvent(Event event); }

    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    public static final class Event {
        public final long sequence = 0L;
        public final long uptimeMillis = 0L;
        public final String thread = "";
        public final String owner = "";
        public final String kind = "";
        public final String detail = "";

        private Event() { }

        @Override public String toString() { return ""; }
    }

    public static boolean enabled() { return false; }

    public static void emit(String owner, String kind, String detail) { }

    public static Subscription subscribe(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Trace listener is required");
        return () -> { };
    }

    public static List<Event> snapshot() { return Collections.emptyList(); }

    public static String describe() { return ""; }

    public static void clear() { }
}
