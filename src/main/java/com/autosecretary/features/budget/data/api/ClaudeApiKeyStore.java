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
 * Verwaltet Claude API-Keys verschlüsselt via Android Keystore.
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
