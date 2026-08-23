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
import com.noyorin.balanceisland.ui.MainActivity
import java.util.Locale
import kotlin.math.abs

/** Emits threshold-crossing and fixed-drop balance notifications without exposing API keys. */
class BalanceAlertNotifier(private val context: Context) {
    private val settingsStore = AccountSettingsStore(context)

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
                "余额已低于警告线 ${money(settings.warningLine)}"
            } else {
                "余额接近警告线（警告线 +50%）"
            }
        }

        val reference = previous?.lastNotifiedAmount
        if (reference != null && amount <= reference - settings.dropStep) {
            reasons += "较上次提醒下降 ${money(reference - amount)}"
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
        if (shouldNotify) notify(snapshot, reasons.joinToString("；"), settings.dropStep)
    }

    private fun notify(snapshot: BalanceSnapshot, reason: String, dropStep: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        createChannel()

        val title = "${snapshot.provider.displayName} · ${snapshot.accountDisplayLabel} 额度警告"
        val content = "${snapshot.primaryText} · $reason"
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
                "API 额度警告",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "余额接近警告线、越过警告线或按设定步长下降时提醒"
            }
        )
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    companion object {
        private const val CHANNEL_ID = "balance_threshold_alerts"
        private const val NEAR_LINE_MULTIPLIER = 1.5
        private const val LEVEL_NORMAL = 0
        private const val LEVEL_NEAR = 1
        private const val LEVEL_CRITICAL = 2
    }
}
