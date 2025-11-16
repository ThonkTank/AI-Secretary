package com.secretary.core.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Centralized app preference management using SharedPreferences.
 *
 * Provides type-safe access to app configuration settings including:
 * - Network log server access (enable/disable network binding)
 * - IP whitelisting for secure remote access
 *
 * Thread-safe: SharedPreferences handles synchronization internally.
 */
object AppPreferences {

    private const val PREFS_NAME = "ai_secretary_prefs"

    // Keys
    private const val KEY_NETWORK_LOGS_ENABLED = "network_logs_enabled"
    private const val KEY_WHITELISTED_IP = "whitelisted_ip"

    private lateinit var prefs: SharedPreferences

    /**
     * Initialize preferences with application context.
     * Must be called before any other methods (typically in Application.onCreate() or MainActivity).
     */
    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if network log access is enabled.
     *
     * @return true if HTTP log server should bind to all network interfaces (0.0.0.0),
     *         false if localhost-only (127.0.0.1) - default for security
     */
    fun isNetworkLogsEnabled(): Boolean {
        return prefs.getBoolean(KEY_NETWORK_LOGS_ENABLED, false) // Default: localhost only
    }

    /**
     * Enable or disable network log access.
     *
     * @param enabled true to allow network access (0.0.0.0), false for localhost-only
     */
    fun setNetworkLogsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NETWORK_LOGS_ENABLED, enabled).apply()
    }

    /**
     * Get whitelisted IP address for network log access.
     *
     * @return whitelisted IP address (e.g., "192.168.1.100"), or null if not set
     */
    fun getWhitelistedIp(): String? {
        return prefs.getString(KEY_WHITELISTED_IP, null)
    }

    /**
     * Set whitelisted IP address for network log access.
     * Only this IP will be allowed to access logs when network mode is enabled.
     *
     * @param ip IP address to whitelist (e.g., "192.168.1.100")
     */
    fun setWhitelistedIp(ip: String) {
        prefs.edit().putString(KEY_WHITELISTED_IP, ip).apply()
    }

    /**
     * Clear whitelisted IP address.
     * After clearing, all IPs on the network will be allowed (if network logs enabled).
     */
    fun clearWhitelistedIp() {
        prefs.edit().remove(KEY_WHITELISTED_IP).apply()
    }

    /**
     * Check if IP whitelisting is active.
     *
     * @return true if a whitelist IP is configured
     */
    fun hasWhitelistedIp(): Boolean {
        return getWhitelistedIp() != null
    }
}
