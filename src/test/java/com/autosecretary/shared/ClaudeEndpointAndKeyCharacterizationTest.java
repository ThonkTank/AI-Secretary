package com.autosecretary.shared;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertNull;

import com.autosecretary.testing.AutoSecretaryRobolectricTest;

import org.junit.Test;

/**
 * Protects the endpoint-override resolution and the API-key store's prefs location (the latter
 * guards that moving {@link ClaudeApiKeyStore} to {@code shared/} kept the same SharedPreferences
 * file, so an existing user key survives the move).
 */
public final class ClaudeEndpointAndKeyCharacterizationTest extends AutoSecretaryRobolectricTest {

    /** Invariant: a configured base URL (not api.anthropic.com) is what the client targets. */
    @Test
    public void resolveUrlUsesConfiguredBaseUrlAndAppendsMessagesPath() {
        assertEquals("https://api.anthropic.com/v1/messages", ClaudeMessagesClient.resolveUrl(null));
        assertEquals("https://api.anthropic.com/v1/messages", ClaudeMessagesClient.resolveUrl("  "));
        assertEquals("https://proxy.local/v1/messages", ClaudeMessagesClient.resolveUrl("https://proxy.local"));
        // Trailing slash is normalised so the path is not duplicated.
        assertEquals("https://proxy.local/v1/messages", ClaudeMessagesClient.resolveUrl("https://proxy.local/"));
    }

    /** Invariant: the endpoint store defaults to Anthropic and round-trips a custom override. */
    @Test
    public void endpointStoreDefaultsAndOverrides() {
        Context context = ApplicationProvider.getApplicationContext();
        ClaudeEndpointStore store = new ClaudeEndpointStore(context);

        assertEquals(ClaudeEndpointStore.DEFAULT_BASE_URL, store.getBaseUrl());

        store.setBaseUrl("https://proxy.local");
        assertEquals("https://proxy.local", new ClaudeEndpointStore(context).getBaseUrl());

        store.setBaseUrl("  ");
        assertEquals(ClaudeEndpointStore.DEFAULT_BASE_URL, store.getBaseUrl());
    }

    /** Invariant: the model store defaults to sonnet-5, round-trips an override, and blank resets. */
    @Test
    public void modelStoreDefaultsAndOverrides() {
        Context context = ApplicationProvider.getApplicationContext();
        ClaudeModelStore store = new ClaudeModelStore(context);

        assertEquals(ClaudeModelStore.DEFAULT_MODEL, store.getModel());
        assertEquals("claude-sonnet-5", ClaudeModelStore.DEFAULT_MODEL);

        store.setModel("claude-opus-4-8");
        assertEquals("claude-opus-4-8", new ClaudeModelStore(context).getModel());

        store.setModel("  ");
        assertEquals(ClaudeModelStore.DEFAULT_MODEL, store.getModel());
    }

    /** Invariant: the model store shares the {@code budget_secure_settings} prefs file. */
    @Test
    public void modelStoreUsesSharedPrefsFile() {
        Context context = ApplicationProvider.getApplicationContext();
        new ClaudeModelStore(context).setModel("claude-custom-9");
        SharedPreferences prefs = context.getSharedPreferences("budget_secure_settings", Context.MODE_PRIVATE);
        assertEquals("claude-custom-9", prefs.getString("claude_model", null));
    }

    /**
     * Invariant: after the move to {@code shared/}, the API-key store still targets the
     * {@code budget_secure_settings} prefs file — the contract that preserves an existing user key.
     * (Android Keystore encryption is unavailable under the test runtime, so this exercises the
     * graceful decrypt-failure path, which reads and wipes that exact file.)
     */
    @Test
    public void apiKeyStoreReadsAndWipesTheUnchangedPrefsFile() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("budget_secure_settings", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("claude_api_key_enc", "stale-ciphertext")
                .putString("claude_api_key_iv", "stale-iv")
                .apply();

        // Decryption cannot succeed (no keystore), so the store returns null and wipes the entries
        // from budget_secure_settings — proving it reads that file.
        assertNull(new ClaudeApiKeyStore(context).getApiKey());
        assertNull(prefs.getString("claude_api_key_enc", null));
        assertNull(prefs.getString("claude_api_key_iv", null));
    }
}
