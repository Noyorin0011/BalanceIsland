package com.noyorin.balanceisland.service

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.noyorin.balanceisland.quicksettings.BalanceQuickSettingsTileService

/** Persists user intent separately from the service process' current state. */
class ServiceRuntimePreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun autoRestartEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_RESTART, false)

    fun setAutoRestartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESTART, enabled).apply()
        notifyTile()
    }

    fun desiredRunning(): Boolean = prefs.getBoolean(KEY_DESIRED_RUNNING, false)

    fun setDesiredRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_DESIRED_RUNNING, running).apply()
        notifyTile()
    }

    fun serviceRunning(): Boolean = prefs.getBoolean(KEY_SERVICE_RUNNING, false)

    fun setServiceRunning(running: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()
        notifyTile()
    }

    private fun notifyTile() {
        TileService.requestListeningState(
            appContext,
            ComponentName(appContext, BalanceQuickSettingsTileService::class.java)
        )
    }

    companion object {
        private const val PREFS_NAME = "service_runtime"
        private const val KEY_AUTO_RESTART = "auto_restart_enabled"
        private const val KEY_DESIRED_RUNNING = "desired_running"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}
