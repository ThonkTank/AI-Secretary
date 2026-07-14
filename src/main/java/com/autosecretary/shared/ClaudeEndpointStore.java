package com.autosecretary.shared;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores the optional base URL for Claude Messages API calls (plain, non-secret). Defaults to the
 * official Anthropic endpoint; the user can point it at a self-hosted subscription proxy that speaks
 * the same Messages API, without any code change. Shares the prefs file with {@link ClaudeApiKeyStore}.
 */
public final class ClaudeEndpointStore {
    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";

    private static final String PREF_NAME = "budget_secure_settings";
    private static final String KEY_BASE_URL = "claude_base_url";

    private final SharedPreferences preferences;

    public ClaudeEndpointStore(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the configured base URL, or {@link #DEFAULT_BASE_URL} if none is set. */
    public String getBaseUrl() {
        String stored = preferences.getString(KEY_BASE_URL, null);
        return (stored == null || stored.isBlank()) ? DEFAULT_BASE_URL : stored.trim();
    }

    /** Persists a custom base URL; null/blank resets to the default endpoint. */
    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            preferences.edit().remove(KEY_BASE_URL).apply();
        } else {
            preferences.edit().putString(KEY_BASE_URL, baseUrl.trim()).apply();
        }
    }
}
