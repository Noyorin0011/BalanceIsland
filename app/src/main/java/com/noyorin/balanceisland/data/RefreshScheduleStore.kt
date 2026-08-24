package com.noyorin.balanceisland.data

import android.content.Context
import kotlin.math.pow

/** Persists per-account refresh timing and rate-limit backoff across process restarts. */
class RefreshScheduleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldAttempt(
        credentialId: String,
        intervalMinutes: Int,
        force: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (rateLimitUntil(credentialId) > now) return false
        val lastAttempt = prefs.getLong(lastAttemptKey(credentialId), 0L)
        if (force) return now - lastAttempt >= MIN_FORCE_SPACING_MS
        val nextScheduled = prefs.getLong(nextScheduledKey(credentialId), 0L)
        return now >= nextScheduled
    }

    fun isRateLimited(credentialId: String, now: Long = System.currentTimeMillis()): Boolean =
        rateLimitUntil(credentialId) > now

    fun rateLimitUntil(credentialId: String): Long =
        prefs.getLong(rateLimitUntilKey(credentialId), 0L)

    fun markAttempt(credentialId: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(lastAttemptKey(credentialId), now).apply()
    }

    fun recordSuccess(
        credentialId: String,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putLong(nextScheduledKey(credentialId), now + intervalMinutes.minutesToMillis())
            .remove(rateLimitUntilKey(credentialId))
            .remove(backoffLevelKey(credentialId))
            .apply()
    }

    fun recordFailure(
        credentialId: String,
        intervalMinutes: Int,
        now: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putLong(nextScheduledKey(credentialId), now + intervalMinutes.minutesToMillis())
            .apply()
    }

    fun recordRateLimit(
        credentialId: String,
        intervalMinutes: Int,
        retryAfterMillis: Long?,
        now: Long = System.currentTimeMillis()
    ): Long {
        val nextLevel = (prefs.getInt(backoffLevelKey(credentialId), 0) + 1)
            .coerceAtMost(MAX_BACKOFF_LEVEL)
        val exponentialDelay = (
            intervalMinutes.minutesToMillis() * 2.0.pow(nextLevel.toDouble())
        ).toLong()
        val delay = maxOf(
            MIN_RATE_LIMIT_BACKOFF_MS,
            retryAfterMillis ?: 0L,
            exponentialDelay
        ).coerceAtMost(MAX_RATE_LIMIT_BACKOFF_MS)
        val retryAt = now + delay
        prefs.edit()
            .putInt(backoffLevelKey(credentialId), nextLevel)
            .putLong(rateLimitUntilKey(credentialId), retryAt)
            .putLong(nextScheduledKey(credentialId), retryAt)
            .apply()
        return retryAt
    }

    fun remove(credentialId: String) {
        prefs.edit()
            .remove(lastAttemptKey(credentialId))
            .remove(nextScheduledKey(credentialId))
            .remove(rateLimitUntilKey(credentialId))
            .remove(backoffLevelKey(credentialId))
            .apply()
    }

    private fun Int.minutesToMillis(): Long = this * 60_000L
    private fun lastAttemptKey(id: String) = "last_attempt_$id"
    private fun nextScheduledKey(id: String) = "next_scheduled_$id"
    private fun rateLimitUntilKey(id: String) = "rate_limit_until_$id"
    private fun backoffLevelKey(id: String) = "backoff_level_$id"

    companion object {
        private const val PREFS_NAME = "refresh_schedule"
        private const val MIN_FORCE_SPACING_MS = 30_000L
        private const val MIN_RATE_LIMIT_BACKOFF_MS = 60_000L
        private const val MAX_RATE_LIMIT_BACKOFF_MS = 24 * 60 * 60_000L
        private const val MAX_BACKOFF_LEVEL = 7
    }
}
