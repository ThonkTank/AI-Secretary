package controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

/**
 * Verwaltet den Claude API-Key in SharedPreferences.
 * Der Key wird Base64-encoded gespeichert (einfache Obfuscation, keine echte Verschlüsselung).
 */
public class ApiKeyManager {

    private static final String PREF_NAME = "secretary";
    private static final String KEY_API_KEY = "claude_api_key_b64";

    /**
     * Speichert den API-Key (Base64-encoded).
     */
    public static void saveApiKey(Context context, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            clearApiKey(context);
            return;
        }
        String encoded = Base64.encodeToString(apiKey.getBytes(), Base64.NO_WRAP);
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, encoded)
            .apply();
    }

    /**
     * Lädt den API-Key (dekodiert).
     * @return API-Key oder null wenn nicht gespeichert.
     */
    public static String getApiKey(Context context) {
        String encoded = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, null);
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.decode(encoded, Base64.NO_WRAP);
            return new String(decoded);
        } catch (IllegalArgumentException e) {
            // Ungültiges Base64
            return null;
        }
    }

    /**
     * Prüft ob ein API-Key gespeichert ist.
     */
    public static boolean hasApiKey(Context context) {
        return getApiKey(context) != null;
    }

    /**
     * Löscht den gespeicherten API-Key.
     */
    public static void clearApiKey(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_KEY)
            .apply();
    }

    /**
     * Validiert ein API-Key Format (basic check).
     * Anthropic Keys beginnen mit "sk-ant-".
     */
    public static boolean isValidFormat(String apiKey) {
        return apiKey != null && apiKey.startsWith("sk-ant-") && apiKey.length() > 20;
    }
}
