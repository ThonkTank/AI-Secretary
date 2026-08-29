package de.thonktank.autosecretary;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

/**
 * Minimal cross-installation runner. It deliberately avoids AndroidX/JUnit because an
 * instrumentation APK shares the target application's class loader after adb install -r; test
 * framework dependencies must therefore not become a hidden release-APK ABI requirement.
 */
public final class UpgradeProbeInstrumentation extends Instrumentation {
    private static final String TAG = "UpgradeProbe";

    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments == null ? Bundle.EMPTY : new Bundle(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        String phase = arguments.getString("upgradePhase", "");
        try {
            if ("seed".equals(phase)) {
                UpgradePersistenceProbe.seed(getTargetContext(), getContext(), this);
            } else if ("verify".equals(phase)) {
                UpgradePersistenceProbe.verify(getTargetContext(), getContext(), this);
            } else {
                throw new AssertionError("Unknown upgrade phase: " + phase);
            }
            result.putString("stream", "\nOK (1 probe)\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "Upgrade probe failed in phase " + phase, failure);
            result.putString("shortMsg", failure.toString());
            result.putString("stream", "\nFAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
