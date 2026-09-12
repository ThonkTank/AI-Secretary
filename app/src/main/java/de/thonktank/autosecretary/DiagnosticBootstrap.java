package de.thonktank.autosecretary;

import androidx.annotation.Keep;

/** Process-only contract entered by the dedicated signed runner before Application/providers. */
@Keep
public final class DiagnosticBootstrap {
    private static volatile boolean diagnosing;
    private static boolean normalStartup;

    private DiagnosticBootstrap() { }

    /** Kept ABI: historical APKs without this method cannot promise an early diagnostic guard. */
    public static synchronized int enterForDiagnosis(int requestedVersion) {
        if (requestedVersion != 1 || normalStartup) {
            throw new IllegalStateException("UNSUPPORTED_DIAGNOSTIC_PROTOCOL: version="
                    + requestedVersion + ", normalStartup=" + normalStartup);
        }
        diagnosing = true;
        return 1;
    }

    public static boolean isDiagnosing() { return diagnosing; }

    static synchronized boolean beginNormalStartup() {
        if (diagnosing) return false;
        normalStartup = true;
        return true;
    }

    public static void requireNormalDatabaseAccess() {
        if (diagnosing) {
            throw new IllegalStateException("Business database access during diagnostic bootstrap");
        }
    }
}
