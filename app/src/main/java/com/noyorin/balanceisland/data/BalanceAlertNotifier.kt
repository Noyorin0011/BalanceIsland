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
import com.noyorin.balanceisland.display.BalanceTextFormatter
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

        val now = System.currentTimeMillis()
        val anomaly = anomalyChange(settings, previous, amount, now)
        val shouldResetReference = reference == null || amount > reference
        val shouldNotify = reasons.isNotEmpty()
        settingsStore.saveAlertState(
            snapshot.credentialId,
            BalanceAlertState(
                lastNotifiedAmount = if (shouldNotify || shouldResetReference) amount else reference,
                lastLevel = level,
                lastSeenAmount = amount,
                lastAnomalyAtEpochMillis = if (anomaly != null) {
                    now
                } else {
                    previous?.lastAnomalyAtEpochMillis
                }
            )
        )
        if (shouldNotify) {
            notify(snapshot, reasons.joinToString(text(R.string.list_separator)), settings.dropStep)
        }
        if (anomaly != null) notifyAnomaly(snapshot, anomaly.first, anomaly.second)
    }

    private fun anomalyChange(
        settings: AccountBalanceSettings,
        previous: BalanceAlertState?,
        amount: Double,
        now: Long
    ): Pair<Double, Double>? {
        if (!settings.anomalyEnabled) return null
        val lastSeen = previous?.lastSeenAmount ?: return null
        val delta = amount - lastSeen
        if (delta == 0.0) return null
        val overAbsolute = abs(delta) >= settings.anomalyThreshold
        val overPercent = if (lastSeen > 0.0) {
            abs(delta) >= lastSeen * settings.anomalyPercentThreshold / 100.0
        } else {
            overAbsolute
        }
        val exceedsThreshold = when (settings.anomalyMode) {
            AnomalyMode.ABSOLUTE -> overAbsolute
            AnomalyMode.PERCENT -> overPercent
            AnomalyMode.BOTH -> overAbsolute || overPercent
        }
        if (!exceedsThreshold) return null
        val cooldownMs = settings.anomalyCooldownMinutes * 60_000L
        val inCooldown = previous.lastAnomalyAtEpochMillis?.let {
            val elapsed = now - it
            elapsed in 0 until cooldownMs
        } ?: false
        return if (inCooldown) null else lastSeen to delta
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

    private fun notifyAnomaly(snapshot: BalanceSnapshot, lastSeen: Double, delta: Double) {
        if (!canNotify()) return
        createChannel()
        val title = text(
            R.string.anomaly_title,
            snapshot.provider.displayName,
            snapshot.accountDisplayLabel
        )
        val content = text(
            R.string.anomaly_content,
            BalanceTextFormatter.amount(snapshot.currencyCode, lastSeen),
            signedAmount(snapshot.currencyCode, delta),
            BalanceTextFormatter.amount(snapshot.currencyCode, snapshot.balanceAmount ?: return)
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            snapshot.credentialId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationId = (snapshot.credentialId.hashCode() * 31 + ANOMALY_SALT) and Int.MAX_VALUE
        try {
            NotificationManagerCompat.from(context).notify(
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
        } catch (_: SecurityException) {
            // The notification permission may be revoked between the check and this call.
        }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
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
    private fun signedAmount(currencyCode: String, value: Double): String =
        (if (value >= 0.0) "+" else "-") +
            BalanceTextFormatter.amount(currencyCode, abs(value))
    private fun text(id: Int, vararg args: Any): String = strings.getString(id, *args)

    companion object {
        private const val CHANNEL_ID = "balance_threshold_alerts"
        private const val NEAR_LINE_MULTIPLIER = 1.5
        private const val LEVEL_NORMAL = 0
        private const val LEVEL_NEAR = 1
        private const val LEVEL_CRITICAL = 2
        private const val ANOMALY_SALT = 0x41A7
    }
}
