package com.autosecretary.shared;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the Claude model id used for all Messages API calls (assistant chat and budget PDF import).
 * Plain, non-secret; defaults to {@link #DEFAULT_MODEL}. The user picks a curated model or enters a
 * custom id in the assistant screen. Shares the prefs file with {@link ClaudeApiKeyStore} and
 * {@link ClaudeEndpointStore}.
 */
public final class ClaudeModelStore {
    public static final String DEFAULT_MODEL = "claude-sonnet-5";

    private static final String PREF_NAME = "budget_secure_settings";
    private static final String KEY_MODEL = "claude_model";

    private final SharedPreferences preferences;

    public ClaudeModelStore(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the configured model id, or {@link #DEFAULT_MODEL} if none is set. */
    public String getModel() {
        String stored = preferences.getString(KEY_MODEL, null);
        return (stored == null || stored.isBlank()) ? DEFAULT_MODEL : stored.trim();
    }

    /** Persists a model id; null/blank resets to the default model. */
    public void setModel(String model) {
        if (model == null || model.isBlank()) {
            preferences.edit().remove(KEY_MODEL).apply();
        } else {
            preferences.edit().putString(KEY_MODEL, model.trim()).apply();
        }
    }
}
