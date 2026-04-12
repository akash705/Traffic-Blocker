package com.akash.apptrafficblocker.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Multi-app support ---

    /** Set of blocked package names */
    var targetPackages: Set<String>
        get() = prefs.getStringSet(KEY_TARGET_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_TARGET_PACKAGES, value).apply()

    /** Map of packageName -> appName, stored as "pkg|name" entries */
    var targetAppNames: Map<String, String>
        get() {
            val entries = prefs.getStringSet(KEY_TARGET_APP_NAMES, emptySet()) ?: emptySet()
            return entries.mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        }
        set(value) {
            val entries = value.map { "${it.key}|${it.value}" }.toSet()
            prefs.edit().putStringSet(KEY_TARGET_APP_NAMES, entries).apply()
        }

    fun addTargetApp(packageName: String, appName: String) {
        targetPackages = targetPackages + packageName
        targetAppNames = targetAppNames + (packageName to appName)
    }

    fun removeTargetApp(packageName: String) {
        targetPackages = targetPackages - packageName
        targetAppNames = targetAppNames - packageName
    }

    fun isAppSelected(packageName: String): Boolean {
        return packageName in targetPackages
    }

    // --- Legacy single-app compat (used by BootReceiver) ---

    @Deprecated("Use targetPackages instead", replaceWith = ReplaceWith("targetPackages"))
    var targetPackage: String?
        get() = targetPackages.firstOrNull()
        set(value) {
            if (value != null) {
                targetPackages = setOf(value)
            } else {
                targetPackages = emptySet()
            }
        }

    @Deprecated("Use targetAppNames instead")
    var targetAppName: String?
        get() = targetAppNames.values.firstOrNull()
        set(_) { /* no-op for compat */ }

    // --- Other settings ---

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var pollIntervalMs: Long
        get() = prefs.getLong(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_POLL_INTERVAL, value).apply()

    var showBlockLog: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BLOCK_LOG, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BLOCK_LOG, value).apply()

    /** Per-app blocking mode: "block_all" or "block_domains", stored as "pkg|mode" entries */
    var appBlockingModes: Map<String, String>
        get() {
            val entries = prefs.getStringSet(KEY_APP_BLOCKING_MODES, emptySet()) ?: emptySet()
            return entries.mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }.toMap()
        }
        set(value) {
            val entries = value.map { "${it.key}|${it.value}" }.toSet()
            prefs.edit().putStringSet(KEY_APP_BLOCKING_MODES, entries).apply()
        }

    /** Apps that should have ALL network blocked when not in foreground */
    var backgroundBlockingApps: Set<String>
        get() = prefs.getStringSet(KEY_BACKGROUND_BLOCKING_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BACKGROUND_BLOCKING_APPS, value).apply()

    fun isBackgroundBlockingEnabled(packageName: String): Boolean =
        packageName in backgroundBlockingApps

    fun setBackgroundBlocking(packageName: String, enabled: Boolean) {
        backgroundBlockingApps = if (enabled) {
            backgroundBlockingApps + packageName
        } else {
            backgroundBlockingApps - packageName
        }
    }

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var upstreamDns: String
        get() = prefs.getString(KEY_UPSTREAM_DNS, DNS_GOOGLE) ?: DNS_GOOGLE
        set(value) = prefs.edit().putString(KEY_UPSTREAM_DNS, value).apply()

    fun getAppBlockingMode(packageName: String): String {
        return appBlockingModes[packageName] ?: MODE_BLOCK_ALL
    }

    fun setAppBlockingMode(packageName: String, mode: String) {
        appBlockingModes = appBlockingModes + (packageName to mode)
    }

    companion object {
        private const val PREFS_NAME = "app_traffic_blocker_prefs"
        private const val KEY_TARGET_PACKAGES = "pref_target_packages"
        private const val KEY_TARGET_APP_NAMES = "pref_target_app_names"
        private const val KEY_SERVICE_ENABLED = "pref_service_enabled"
        private const val KEY_AUTO_START = "pref_block_on_launch"
        private const val KEY_POLL_INTERVAL = "pref_poll_interval"
        private const val KEY_SHOW_BLOCK_LOG = "pref_show_block_log"
        private const val KEY_APP_BLOCKING_MODES = "pref_app_blocking_modes"
        private const val KEY_BACKGROUND_BLOCKING_APPS = "pref_background_blocking_apps"
        private const val KEY_THEME_MODE = "pref_theme_mode"
        private const val KEY_UPSTREAM_DNS = "pref_upstream_dns"
        const val DEFAULT_POLL_INTERVAL = 1000L
        const val MODE_BLOCK_ALL = "block_all"
        const val MODE_BLOCK_DOMAINS = "block_domains"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val DNS_GOOGLE = "8.8.8.8"
        const val DNS_CLOUDFLARE = "1.1.1.1"
        const val DNS_QUAD9 = "9.9.9.9"
        /** Maps primary DNS IP to its secondary. */
        val DNS_SECONDARY = mapOf(
            DNS_GOOGLE to "8.8.4.4",
            DNS_CLOUDFLARE to "1.0.0.1",
            DNS_QUAD9 to "149.112.112.112"
        )
    }
}
