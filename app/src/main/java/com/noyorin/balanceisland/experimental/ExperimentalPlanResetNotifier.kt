package com.noyorin.balanceisland.experimental

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import com.noyorin.balanceisland.ui.MainActivity

class ExperimentalPlanResetNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val strings get() = AppLanguagePreferences.wrap(appContext)

    fun notify(events: List<ExperimentalPlanResetEvent>) {
        if (events.isEmpty() || !canNotify()) return
        createChannel()
        val content = events.joinToString(strings.getString(R.string.list_separator)) { event ->
            val window = strings.getString(event.kind.labelResource())
            event.remaining?.let {
                strings.getString(R.string.experimental_reset_notification_window, window, it)
            } ?: strings.getString(R.string.experimental_reset_notification_window_unknown, window)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            NotificationManagerCompat.from(appContext).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(strings.getString(R.string.experimental_reset_notification_title))
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            )
        } catch (_: SecurityException) {
            // The notification permission may be revoked between the check and notify().
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                strings.getString(R.string.experimental_reset_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = strings.getString(R.string.experimental_reset_channel_description)
            }
        )
    }

    private fun ExperimentalPlanWindowKind.labelResource(): Int = when (this) {
        ExperimentalPlanWindowKind.FIVE_HOUR -> R.string.experimental_overlay_label_five_hour
        ExperimentalPlanWindowKind.WEEKLY -> R.string.experimental_overlay_label_weekly
        ExperimentalPlanWindowKind.UNKNOWN -> R.string.experimental_overlay_label_generic
    }

    companion object {
        private const val CHANNEL_ID = "experimental_plan_reset_alerts"
        private const val NOTIFICATION_ID = 0x504C414E
    }
}
