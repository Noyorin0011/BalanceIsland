package com.noyorin.balanceisland.quicksettings

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import com.noyorin.balanceisland.service.IslandOverlayService
import com.noyorin.balanceisland.service.ServiceRuntimePreferences

class BalanceQuickSettingsTileService : TileService() {
    private val runtime by lazy { ServiceRuntimePreferences(this) }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (runtime.desiredRunning() || runtime.serviceRunning()) {
            IslandOverlayService.stop(this)
            updateTile()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        IslandOverlayService.start(this)
        updateTile()
    }

    private fun updateTile() {
        val strings = AppLanguagePreferences.wrap(this)
        qsTile?.apply {
            val active = runtime.desiredRunning() || runtime.serviceRunning()
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = strings.getString(R.string.quick_settings_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = strings.getString(if (active) R.string.tile_running else R.string.tile_stopped)
            }
            updateTile()
        }
    }
}
