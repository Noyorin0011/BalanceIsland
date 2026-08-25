package com.noyorin.balanceisland.experimental

import android.content.Context
import android.content.Intent
import androidx.core.content.edit

data class ExperimentalPlanUsage(
    val planType: String,
    val primaryRemaining: Int?,
    val primaryResetAtSeconds: Long?,
    val primaryWindowSeconds: Long?,
    val secondaryRemaining: Int?,
    val secondaryResetAtSeconds: Long?,
    val secondaryWindowSeconds: Long?,
    val updatedAtMillis: Long
)

data class ExperimentalPlanResetEvent(
    val kind: ExperimentalPlanWindowKind,
    val remaining: Int?,
    val resetAtSeconds: Long
)

enum class ExperimentalPlanWindowKind {
    FIVE_HOUR,
    WEEKLY,
    UNKNOWN
}

object ExperimentalPlanWindowClassifier {
    fun classify(windowSeconds: Long?): ExperimentalPlanWindowKind {
        if (windowSeconds == null) return ExperimentalPlanWindowKind.UNKNOWN
        return when (windowSeconds) {
            in FOUR_HOURS_SECONDS..SIX_HOURS_SECONDS -> ExperimentalPlanWindowKind.FIVE_HOUR
            in SIX_DAYS_SECONDS..EIGHT_DAYS_SECONDS -> ExperimentalPlanWindowKind.WEEKLY
            else -> ExperimentalPlanWindowKind.UNKNOWN
        }
    }

    private const val FOUR_HOURS_SECONDS = 4L * 60L * 60L
    private const val SIX_HOURS_SECONDS = 6L * 60L * 60L
    private const val SIX_DAYS_SECONDS = 6L * 24L * 60L * 60L
    private const val EIGHT_DAYS_SECONDS = 8L * 24L * 60L * 60L
}

enum class ExperimentalPlanReadError {
    AUTH,
    RATE_LIMIT,
    NETWORK,
    HTTP,
    PARSE
}

object ExperimentalPlanReadErrorClassifier {
    fun fromHttpStatus(statusCode: Int): ExperimentalPlanReadError = when (statusCode) {
        0 -> ExperimentalPlanReadError.NETWORK
        401, 403 -> ExperimentalPlanReadError.AUTH
        429 -> ExperimentalPlanReadError.RATE_LIMIT
        else -> ExperimentalPlanReadError.HTTP
    }
}

object ExperimentalPlanAutoRefreshPolicy {
    const val INITIAL_DELAY_MILLIS = 1_500L
    const val INTERVAL_MILLIS = 5L * 60L * 1_000L

    fun shouldPauseAfter(error: ExperimentalPlanReadError): Boolean =
        error == ExperimentalPlanReadError.AUTH || error == ExperimentalPlanReadError.RATE_LIMIT

    fun nextDelayMillis(lastUpdatedAtMillis: Long, nowMillis: Long, immediateIfStale: Boolean): Long {
        if (!immediateIfStale) return INTERVAL_MILLIS
        val elapsed = (nowMillis - lastUpdatedAtMillis).coerceAtLeast(0L)
        return when {
            lastUpdatedAtMillis == 0L || elapsed >= INTERVAL_MILLIS -> INITIAL_DELAY_MILLIS
            else -> (INTERVAL_MILLIS - elapsed).coerceAtLeast(INITIAL_DELAY_MILLIS)
        }
    }
}

object ExperimentalPlanResetDetector {
    fun detect(
        previous: ExperimentalPlanUsage?,
        current: ExperimentalPlanUsage
    ): List<ExperimentalPlanResetEvent> {
        if (previous == null) return emptyList()
        return buildList {
            detectWindow(
                previousResetAtSeconds = previous.primaryResetAtSeconds,
                currentResetAtSeconds = current.primaryResetAtSeconds,
                currentWindowSeconds = current.primaryWindowSeconds,
                currentRemaining = current.primaryRemaining,
                currentUpdatedAtMillis = current.updatedAtMillis
            )?.let(::add)
            detectWindow(
                previousResetAtSeconds = previous.secondaryResetAtSeconds,
                currentResetAtSeconds = current.secondaryResetAtSeconds,
                currentWindowSeconds = current.secondaryWindowSeconds,
                currentRemaining = current.secondaryRemaining,
                currentUpdatedAtMillis = current.updatedAtMillis
            )?.let(::add)
        }
    }

    private fun detectWindow(
        previousResetAtSeconds: Long?,
        currentResetAtSeconds: Long?,
        currentWindowSeconds: Long?,
        currentRemaining: Int?,
        currentUpdatedAtMillis: Long
    ): ExperimentalPlanResetEvent? {
        val previousReset = previousResetAtSeconds ?: return null
        val currentReset = currentResetAtSeconds ?: return null
        val readAtSeconds = currentUpdatedAtMillis / 1_000L
        if (previousReset > readAtSeconds + DUE_TIME_GRACE_SECONDS) return null
        if (currentReset - previousReset < MIN_RESET_ADVANCE_SECONDS) return null
        return ExperimentalPlanResetEvent(
            kind = ExperimentalPlanWindowClassifier.classify(currentWindowSeconds),
            remaining = currentRemaining,
            resetAtSeconds = currentReset
        )
    }

    private const val DUE_TIME_GRACE_SECONDS = 60L
    private const val MIN_RESET_ADVANCE_SECONDS = 15L * 60L
}

data class ExperimentalPlanReadState(
    val lastAttemptAtMillis: Long,
    val lastError: ExperimentalPlanReadError?
)

class ExperimentalPlanPreferences(context: Context) {
    private val prefsContext = context.applicationContext
    private val prefs = prefsContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun consentAccepted(): Boolean = prefs.getInt(KEY_CONSENT_VERSION, 0) == CONSENT_VERSION

    fun acceptConsent() {
        prefs.edit { putInt(KEY_CONSENT_VERSION, CONSENT_VERSION) }
    }

    fun autoRefreshWhileOpenEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH_WHILE_OPEN, false)

    fun setAutoRefreshWhileOpenEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_REFRESH_WHILE_OPEN, enabled) }
    }

    fun resetNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_RESET_NOTIFICATIONS_ENABLED, false)

    fun setResetNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RESET_NOTIFICATIONS_ENABLED, enabled) }
    }

    fun usage(): ExperimentalPlanUsage? {
        if (!prefs.contains(KEY_UPDATED_AT)) return null
        return ExperimentalPlanUsage(
            planType = prefs.getString(KEY_PLAN_TYPE, "") ?: "",
            primaryRemaining = nullableInt(KEY_PRIMARY_REMAINING),
            primaryResetAtSeconds = nullableLong(KEY_PRIMARY_RESET_AT),
            primaryWindowSeconds = nullableLong(KEY_PRIMARY_WINDOW_SECONDS),
            secondaryRemaining = nullableInt(KEY_SECONDARY_REMAINING),
            secondaryResetAtSeconds = nullableLong(KEY_SECONDARY_RESET_AT),
            secondaryWindowSeconds = nullableLong(KEY_SECONDARY_WINDOW_SECONDS),
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun saveUsage(usage: ExperimentalPlanUsage): List<ExperimentalPlanResetEvent> {
        val resetEvents = ExperimentalPlanResetDetector.detect(previous = usage(), current = usage)
        prefs.edit {
            putString(KEY_PLAN_TYPE, usage.planType)
            putNullableInt(KEY_PRIMARY_REMAINING, usage.primaryRemaining)
            putNullableLong(KEY_PRIMARY_RESET_AT, usage.primaryResetAtSeconds)
            putNullableLong(KEY_PRIMARY_WINDOW_SECONDS, usage.primaryWindowSeconds)
            putNullableInt(KEY_SECONDARY_REMAINING, usage.secondaryRemaining)
            putNullableLong(KEY_SECONDARY_RESET_AT, usage.secondaryResetAtSeconds)
            putNullableLong(KEY_SECONDARY_WINDOW_SECONDS, usage.secondaryWindowSeconds)
            putLong(KEY_UPDATED_AT, usage.updatedAtMillis)
            putLong(KEY_LAST_ATTEMPT_AT, usage.updatedAtMillis)
            remove(KEY_LAST_ERROR)
        }
        notifyUsageChanged()
        return resetEvents
    }

    fun readState(): ExperimentalPlanReadState? {
        if (!prefs.contains(KEY_LAST_ATTEMPT_AT)) return null
        val error = prefs.getString(KEY_LAST_ERROR, null)?.let { value ->
            ExperimentalPlanReadError.entries.firstOrNull { it.name == value }
        }
        return ExperimentalPlanReadState(
            lastAttemptAtMillis = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L),
            lastError = error
        )
    }

    fun markReadAttempt() {
        prefs.edit {
            putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
            remove(KEY_LAST_ERROR)
        }
    }

    fun markReadFailure(error: ExperimentalPlanReadError) {
        prefs.edit {
            putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
            putString(KEY_LAST_ERROR, error.name)
        }
    }

    fun clearAll() {
        prefs.edit { clear() }
        notifyUsageChanged()
    }

    private fun notifyUsageChanged() {
        prefsContext.sendBroadcast(
            Intent(ACTION_PLAN_USAGE_CHANGED).setPackage(prefsContext.packageName)
        )
    }

    private fun nullableInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    private fun nullableLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null

    private fun android.content.SharedPreferences.Editor.putNullableInt(key: String, value: Int?) {
        if (value == null) remove(key) else putInt(key, value)
    }

    private fun android.content.SharedPreferences.Editor.putNullableLong(key: String, value: Long?) {
        if (value == null) remove(key) else putLong(key, value)
    }

    companion object {
        const val CONSENT_VERSION = 1
        private const val PREFS_NAME = "experimental_plan_usage"
        private const val KEY_CONSENT_VERSION = "consent_version"
        private const val KEY_PLAN_TYPE = "plan_type"
        private const val KEY_PRIMARY_REMAINING = "primary_remaining"
        private const val KEY_PRIMARY_RESET_AT = "primary_reset_at"
        private const val KEY_PRIMARY_WINDOW_SECONDS = "primary_window_seconds"
        private const val KEY_SECONDARY_REMAINING = "secondary_remaining"
        private const val KEY_SECONDARY_RESET_AT = "secondary_reset_at"
        private const val KEY_SECONDARY_WINDOW_SECONDS = "secondary_window_seconds"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_AUTO_REFRESH_WHILE_OPEN = "auto_refresh_while_open"
        private const val KEY_RESET_NOTIFICATIONS_ENABLED = "reset_notifications_enabled"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val KEY_LAST_ERROR = "last_error"
        const val ACTION_PLAN_USAGE_CHANGED =
            "com.noyorin.balanceisland.EXPERIMENTAL_PLAN_USAGE_CHANGED"
    }
}
