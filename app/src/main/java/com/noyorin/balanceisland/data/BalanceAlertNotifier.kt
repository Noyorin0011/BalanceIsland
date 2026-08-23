package com.noyorin.balanceisland.data

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
import java.util.Locale
import kotlin.math.abs

/** Emits threshold-crossing and fixed-drop balance notifications without exposing API keys. */
class BalanceAlertNotifier(private val context: Context) {
    private val settingsStore = AccountSettingsStore(context)
    private val strings get() = AppLanguagePreferences.wrap(context)

    fun evaluate(snapshot: BalanceSnapshot) {
        val amount = snapshot.balanceAmount ?: return
        if (!amount.isFinite() || snapshot.status == SnapshotStatus.ERROR) return
        val settings = settingsStore.get(snapshot.credentialId)
        if (!settings.alertEnabled) return

        val level = when {
            amount <= settings.warningLine -> LEVEL_CRITICAL
            amount <= settings.warningLine * NEAR_LINE_MULTIPLIER -> LEVEL_NEAR
            else -> LEVEL_NORMAL
        }
        val previous = settingsStore.getAlertState(snapshot.credentialId)
        val reasons = mutableListOf<String>()
        if (level > (previous?.lastLevel ?: LEVEL_NORMAL)) {
            reasons += if (level == LEVEL_CRITICAL) {
                text(R.string.alert_below_line, money(settings.warningLine))
            } else {
                text(R.string.alert_near_line)
            }
        }

        val reference = previous?.lastNotifiedAmount
        if (reference != null && amount <= reference - settings.dropStep) {
            reasons += text(R.string.alert_dropped, money(reference - amount))
        }

        val shouldResetReference = reference == null || amount > reference
        val shouldNotify = reasons.isNotEmpty()
        settingsStore.saveAlertState(
            snapshot.credentialId,
            BalanceAlertState(
                lastNotifiedAmount = if (shouldNotify || shouldResetReference) amount else reference,
                lastLevel = level
            )
        )
        if (shouldNotify) {
            notify(snapshot, reasons.joinToString(text(R.string.list_separator)), settings.dropStep)
        }
    }

    private fun notify(snapshot: BalanceSnapshot, reason: String, dropStep: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        createChannel()

        val title = text(
            R.string.alert_title,
            snapshot.provider.displayName,
            snapshot.accountDisplayLabel
        )
        val content = text(R.string.alert_content, snapshot.primaryText, reason)
        val pendingIntent = PendingIntent.getActivity(
            context,
            snapshot.credentialId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val bucket = ((snapshot.balanceAmount ?: 0.0) / dropStep).toInt()
        val notificationId = abs(snapshot.credentialId.hashCode() * 31 + bucket)
        notifications.notify(
            notificationId,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                text(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = text(R.string.alert_channel_description)
            }
        )
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun text(id: Int, vararg args: Any): String = strings.getString(id, *args)

    companion object {
        private const val CHANNEL_ID = "balance_threshold_alerts"
        private const val NEAR_LINE_MULTIPLIER = 1.5
        private const val LEVEL_NORMAL = 0
        private const val LEVEL_NEAR = 1
        private const val LEVEL_CRITICAL = 2
    }
}
