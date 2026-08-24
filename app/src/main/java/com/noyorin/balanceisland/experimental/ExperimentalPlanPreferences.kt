package com.noyorin.balanceisland.experimental

import android.content.Context

data class ExperimentalPlanUsage(
    val planType: String,
    val primaryRemaining: Int?,
    val primaryResetAtSeconds: Long?,
    val secondaryRemaining: Int?,
    val secondaryResetAtSeconds: Long?,
    val updatedAtMillis: Long
)

class ExperimentalPlanPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun consentAccepted(): Boolean = prefs.getInt(KEY_CONSENT_VERSION, 0) == CONSENT_VERSION

    fun acceptConsent() {
        prefs.edit().putInt(KEY_CONSENT_VERSION, CONSENT_VERSION).apply()
    }

    fun usage(): ExperimentalPlanUsage? {
        if (!prefs.contains(KEY_UPDATED_AT)) return null
        return ExperimentalPlanUsage(
            planType = prefs.getString(KEY_PLAN_TYPE, "") ?: "",
            primaryRemaining = nullableInt(KEY_PRIMARY_REMAINING),
            primaryResetAtSeconds = nullableLong(KEY_PRIMARY_RESET_AT),
            secondaryRemaining = nullableInt(KEY_SECONDARY_REMAINING),
            secondaryResetAtSeconds = nullableLong(KEY_SECONDARY_RESET_AT),
            updatedAtMillis = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun saveUsage(usage: ExperimentalPlanUsage) {
        prefs.edit().apply {
            putString(KEY_PLAN_TYPE, usage.planType)
            putNullableInt(KEY_PRIMARY_REMAINING, usage.primaryRemaining)
            putNullableLong(KEY_PRIMARY_RESET_AT, usage.primaryResetAtSeconds)
            putNullableInt(KEY_SECONDARY_REMAINING, usage.secondaryRemaining)
            putNullableLong(KEY_SECONDARY_RESET_AT, usage.secondaryResetAtSeconds)
            putLong(KEY_UPDATED_AT, usage.updatedAtMillis)
        }.apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
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
        private const val KEY_SECONDARY_REMAINING = "secondary_remaining"
        private const val KEY_SECONDARY_RESET_AT = "secondary_reset_at"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
