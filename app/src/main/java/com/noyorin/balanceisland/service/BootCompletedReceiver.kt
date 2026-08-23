package com.noyorin.balanceisland.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val runtime = ServiceRuntimePreferences(context)
        if (runtime.autoRestartEnabled() && runtime.desiredRunning() &&
            Settings.canDrawOverlays(context)
        ) {
            IslandOverlayService.restart(context)
        }
    }
}
