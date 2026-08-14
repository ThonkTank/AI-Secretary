package com.autosecretary.platform;

import android.content.Context;

import com.autosecretary.application.ai.AiConsentPort;

public final class PreferencesAiConsentGateway implements AiConsentPort {
    private static final String PREFERENCES = "local_ai";
    private static final String ACCEPTED = "gemma_terms_accepted";
    private final Context context;

    public PreferencesAiConsentGateway(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public boolean accepted() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(ACCEPTED, false);
    }

    @Override public void accept() {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putBoolean(ACCEPTED, true).apply();
    }
}
