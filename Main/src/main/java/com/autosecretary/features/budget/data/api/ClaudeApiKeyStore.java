package com.autosecretary.features.budget.data.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Securely stores and retrieves Claude API keys using Android Keystore.
 *
 * <h2>Security Model</h2>
 * Uses AES-256 encryption with GCM (Galois/Counter Mode) authenticated encryption.
 * The encryption key is generated and stored in the Android Keystore (trusted execution environment),
 * so the plaintext key never touches SharedPreferences or is readable via traditional methods.
 *
 * <h2>Usage</h2>
 * <ul>
 *   <li>{@link #saveApiKey(String)}: Encrypts and stores (AES-256-GCM with random IV)</li>
 *   <li>{@link #getApiKey()}: Decrypts and returns; returns null if key is lost or unavailable</li>
 *   <li>{@link #clearApiKey()}: Wipes stored key from preferences</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * Distinguishes between two failure modes:
 * <ul>
 *   <li><b>Permanent failure</b> (GeneralSecurityException): Key is lost (e.g., factory reset, key invalidation).
 *       The stored ciphertext is automatically wiped and null is returned.</li>
 *   <li><b>Temporary failure</b> (IOException): Keystore is temporarily unavailable.
 *       Stored ciphertext is preserved; null is returned for retry later.</li>
 * </ul>
 *
 * @see <a href="https://developer.android.com/training/articles/keystore">Android Keystore Documentation</a>
 */
public class ClaudeApiKeyStore {
    private static final String TAG = "ClaudeApiKeyStore";
    private static final String PREF_NAME = "budget_secure_settings";
    private static final String KEY_API_KEY = "claude_api_key_enc";
    private static final String KEY_IV = "claude_api_key_iv";
    private static final String KEY_ALIAS = "autosecretary_claude_api";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int GCM_TAG_SIZE_BITS = 128;

    private final SharedPreferences preferences;

    public ClaudeApiKeyStore(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Encrypts and stores an API key.
     *
     * @param apiKey The plaintext API key to store. If null or empty, the stored key is cleared.
     * @throws IllegalStateException if encryption fails (unrecoverable security error)
     */
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            clearApiKey();
            return;
        }

        try {
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            String encryptedB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            String ivB64 = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
            preferences.edit()
                    .putString(KEY_API_KEY, encryptedB64)
                    .putString(KEY_IV, ivB64)
                    .apply();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("API-Key konnte nicht sicher gespeichert werden", e);
        }
    }

    /**
     * Retrieves the stored API key.
     *
     * @return The plaintext API key, or null if:
     *   <ul>
     *     <li>No key has been saved yet</li>
     *     <li>The key was permanently lost (Android Keystore reset/key invalidation).
     *         The stored ciphertext is automatically wiped.</li>
     *     <li>The Android Keystore is temporarily unavailable.
     *         The stored ciphertext is preserved for retry later.</li>
     *   </ul>
     */
    public String getApiKey() {
        String encryptedB64 = preferences.getString(KEY_API_KEY, null);
        String ivB64 = preferences.getString(KEY_IV, null);
        if (encryptedB64 == null || ivB64 == null) {
            return null;
        }

        try {
            byte[] encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(GCM_TAG_SIZE_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Key is permanently unreadable (e.g. after factory reset or key invalidation) — wipe.
            Log.w(TAG, "API-Key-Entschlüsselung dauerhaft fehlgeschlagen, Schlüssel wird gelöscht", e);
            clearApiKey();
            return null;
        } catch (IOException e) {
            // Keystore temporarily unavailable — do not wipe, just return null.
            Log.w(TAG, "Android Keystore vorübergehend nicht verfügbar", e);
            return null;
        }
    }

    public void clearApiKey() {
        preferences.edit().remove(KEY_API_KEY).remove(KEY_IV).apply();
    }

    private SecretKey getOrCreateSecretKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry entry) {
            return entry.getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}
