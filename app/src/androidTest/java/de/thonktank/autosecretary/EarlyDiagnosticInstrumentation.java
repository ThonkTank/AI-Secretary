package de.thonktank.autosecretary;

import android.app.Application;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

/** Framework-only early hook; does not link the target's classes into historical probe runs. */
public abstract class EarlyDiagnosticInstrumentation extends Instrumentation {
    private boolean entered;

    @Override public Application newApplication(ClassLoader loader, String className, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            if (!"de.thonktank.autosecretary.AutoSecretaryApplication".equals(className)) {
                throw new IllegalStateException("Unexpected application class: " + className);
            }
            Class<?> contract = Class.forName("de.thonktank.autosecretary.DiagnosticBootstrap", true, loader);
            Object version = contract.getMethod("enterForDiagnosis", int.class).invoke(null, 1);
            if (!Integer.valueOf(1).equals(version)) {
                throw new IllegalStateException("Unexpected diagnostic contract: " + version);
            }
            entered = true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            InstantiationException rejected = new InstantiationException(
                    "UNSUPPORTED_DIAGNOSTIC_PROTOCOL: target cannot enter contract 1 before providers");
            rejected.initCause(failure);
            Bundle result = new Bundle();
            result.putInt("diagnosticProtocol", 1);
            result.putString("diagnosticUnsupported", "Target cannot enter contract 1 before providers");
            result.putString("stream", "\nUNSUPPORTED_DIAGNOSTIC_PROTOCOL\n");
            finish(Activity.RESULT_CANCELED, result);
            throw rejected;
        }
        return super.newApplication(loader, className, context);
    }

    protected final void requireEarlyEntry() {
        if (!entered) throw new IllegalStateException("Diagnostic entry did not precede Application");
    }
}
