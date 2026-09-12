package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import de.thonktank.autosecretary.ui.today.ProductActivitySession;

/** A fresh ordinary Application, used only to accept recovery of isolated native fixtures. */
public final class NormalDiagnosticRecoveryInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            DiagnosticFixtures.requireIsolated(getTargetContext());
            DiagnosticFixtures.check(!DiagnosticBootstrap.isDiagnosing(), "Diagnostic mode survived process restart");
            try (ProductActivitySession activity = new ProductActivitySession(this)) {
                activity.launch();
                DiagnosticFixtures.awaitNormalRecovery(getTargetContext());
            }
            result.putString("stream", "\nOK (1 normal diagnostic recovery)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e("DiagnosticNormal", "Normal lifecycle recovery failed", failure);
            result.putString("stream", "\nFAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
