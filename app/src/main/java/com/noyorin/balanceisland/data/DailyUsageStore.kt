package com.noyorin.balanceisland.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Estimates daily spend for providers that expose balance but no usage-report API.
 * The day's earliest known balance is persisted across process death; observed top-ups are offset.
 */
class DailyUsageStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun record(credentialId: String, currentBalance: Double): Double {
        val todayDate = LocalDate.now()
        val today = todayDate.toString()
        val key = keyFor(credentialId)
        val previous = prefs.getString(key, null)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        val previousDate = previous?.optString("date")
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val sameDay = previousDate == todayDate
        val previousBalance = if (previous?.has("lastBalance") == true) {
            previous.optDouble("lastBalance").takeIf(Double::isFinite)
        } else null
        val mayCarryAcrossMidnight = previousDate == todayDate.minusDays(1)
        val migratedOpeningBalance = previousBalance?.plus(
            previous?.optDouble("usedToday", 0.0)?.takeIf(Double::isFinite) ?: 0.0
        )
        val openingBalance = when {
            sameDay -> previous?.optDouble("openingBalance", Double.NaN)
                ?.takeUnless(Double::isNaN)
                ?: migratedOpeningBalance
                ?: currentBalance
            mayCarryAcrossMidnight -> previousBalance ?: currentBalance
            else -> currentBalance
        }
        val topUpsBefore = if (sameDay) {
            previous?.optDouble("observedTopUps", 0.0)?.takeIf(Double::isFinite) ?: 0.0
        } else 0.0
        val observedTopUp = if ((sameDay || mayCarryAcrossMidnight) && previousBalance != null) {
            (currentBalance - previousBalance).coerceAtLeast(0.0)
        } else 0.0
        val observedTopUps = topUpsBefore + observedTopUp
        val usedToday = (openingBalance + observedTopUps - currentBalance).coerceAtLeast(0.0)

        prefs.edit().putString(
            key,
            JSONObject()
                .put("date", today)
                .put("openingBalance", openingBalance)
                .put("lastBalance", currentBalance)
                .put("observedTopUps", observedTopUps)
                .put("usedToday", usedToday)
                .toString()
        ).apply()
        return usedToday
    }

    fun remove(credentialId: String) {
        prefs.edit().remove(keyFor(credentialId)).apply()
    }

    private fun keyFor(credentialId: String) = "daily_$credentialId"

    companion object {
        private const val PREFS_NAME = "daily_usage_tracking"
    }
}
