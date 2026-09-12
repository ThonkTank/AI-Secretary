package de.thonktank.autosecretary;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/** Isolated fixture runner. Production diagnoses never enter this component. */
public final class DiagnosticFixtureInstrumentation extends EarlyDiagnosticInstrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            requireEarlyEntry();
            DiagnosticFixtures.requireIsolated(getTargetContext());
            String phase = arguments.getString("fixturePhase", "");
            if (phase.equals("seed")) {
                DiagnosticFixtures.seed(getTargetContext(), getContext(),
                        Integer.parseInt(arguments.getString("fixtureSchema", "")));
            } else if (phase.equals("assert")) {
                DiagnosticFixtures.assertUnchanged(getTargetContext());
                String pid = arguments.getString("fixturePid", "");
                if (!pid.isEmpty()) DiagnosticFixtures.assertNativeEvents(getTargetContext(), Integer.parseInt(pid));
            } else throw new IllegalArgumentException("Unknown isolated fixture phase: " + phase);
            result.putString("stream", "\nOK (1 diagnostic fixture)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e("DiagnosticFixture", "Isolated lifecycle assertion failed", failure);
            result.putString("stream", "\nFAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
