package com.noyorin.balanceisland.data

import android.content.Context
import android.content.Intent
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import org.json.JSONObject

/** Per-account balance override, warning threshold and notification cadence. */
class AccountSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val strings get() = AppLanguagePreferences.wrap(appContext)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(credentialId: String): AccountBalanceSettings {
        val raw = prefs.getString(settingsKey(credentialId), null)
            ?: return AccountBalanceSettings(credentialId)
        return runCatching {
            val json = JSONObject(raw)
            AccountBalanceSettings(
                credentialId = credentialId,
                refreshIntervalMinutes = json.optInt("refreshIntervalMinutes", 0)
                    .let { if (it == 0) 0 else it.coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES) },
                alertEnabled = json.optBoolean("alertEnabled", true),
                warningLine = json.optDouble("warningLine", DEFAULT_WARNING_LINE)
                    .coerceAtLeast(MIN_POSITIVE_VALUE),
                dropStep = json.optDouble("dropStep", DEFAULT_DROP_STEP)
                    .coerceAtLeast(MIN_POSITIVE_VALUE),
                manualBalance = if (json.isNull("manualBalance")) {
                    null
                } else {
                    json.optDouble("manualBalance").takeIf { it.isFinite() && it >= 0.0 }
                },
                anomalyEnabled = json.optBoolean("anomalyEnabled", false),
                anomalyThreshold = json.optDouble("anomalyThreshold", DEFAULT_ANOMALY_THRESHOLD)
                    .takeIf(Double::isFinite)
                    ?.coerceAtLeast(MIN_POSITIVE_VALUE)
                    ?: DEFAULT_ANOMALY_THRESHOLD,
                anomalyPercentThreshold = json.optDouble(
                    "anomalyPercentThreshold",
                    DEFAULT_ANOMALY_PERCENT_THRESHOLD
                ).takeIf(Double::isFinite)
                    ?.coerceAtLeast(MIN_POSITIVE_VALUE)
                    ?: DEFAULT_ANOMALY_PERCENT_THRESHOLD,
                anomalyMode = runCatching {
                    AnomalyMode.valueOf(
                        json.optString("anomalyMode", AnomalyMode.BOTH.name)
                    )
                }.getOrDefault(AnomalyMode.BOTH),
                anomalyCooldownMinutes = json.optInt(
                    "anomalyCooldownMinutes",
                    DEFAULT_ANOMALY_COOLDOWN_MINUTES
                ).coerceIn(MIN_COOLDOWN_MINUTES, MAX_COOLDOWN_MINUTES)
            )
        }.getOrElse { AccountBalanceSettings(credentialId) }
    }

    fun save(settings: AccountBalanceSettings) {
        require(
            settings.refreshIntervalMinutes == 0 ||
                settings.refreshIntervalMinutes in MIN_REFRESH_MINUTES..MAX_REFRESH_MINUTES
        ) { strings.getString(R.string.validation_refresh_interval) }
        require(settings.warningLine > 0.0) { strings.getString(R.string.validation_warning_positive) }
        require(settings.dropStep > 0.0) { strings.getString(R.string.validation_drop_positive) }
        require(settings.manualBalance == null || settings.manualBalance >= 0.0) {
            strings.getString(R.string.validation_manual_nonnegative)
        }
        require(settings.anomalyThreshold.isFinite() && settings.anomalyThreshold > 0.0) {
            strings.getString(R.string.validation_anomaly_positive)
        }
        require(
            settings.anomalyPercentThreshold.isFinite() &&
                settings.anomalyPercentThreshold > 0.0
        ) { strings.getString(R.string.validation_anomaly_positive) }
        require(settings.anomalyCooldownMinutes > 0) {
            strings.getString(R.string.validation_anomaly_cooldown_positive)
        }
        val json = JSONObject()
            .put("refreshIntervalMinutes", settings.refreshIntervalMinutes)
            .put("alertEnabled", settings.alertEnabled)
            .put("warningLine", settings.warningLine)
            .put("dropStep", settings.dropStep)
            .put("manualBalance", settings.manualBalance ?: JSONObject.NULL)
            .put("anomalyEnabled", settings.anomalyEnabled)
            .put("anomalyThreshold", settings.anomalyThreshold)
            .put("anomalyPercentThreshold", settings.anomalyPercentThreshold)
            .put("anomalyMode", settings.anomalyMode.name)
            .put(
                "anomalyCooldownMinutes",
                settings.anomalyCooldownMinutes.coerceIn(
                    MIN_COOLDOWN_MINUTES,
                    MAX_COOLDOWN_MINUTES
                )
            )
        prefs.edit()
            .putString(settingsKey(settings.credentialId), json.toString())
            .apply()
        notifyChanged()
    }

    fun getAlertState(credentialId: String): BalanceAlertState? {
        val raw = prefs.getString(alertKey(credentialId), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            BalanceAlertState(
                lastNotifiedAmount = if (json.isNull("lastNotifiedAmount")) null
                else json.optDouble("lastNotifiedAmount").takeIf(Double::isFinite),
                lastLevel = json.optInt("lastLevel", 0),
                lastSeenAmount = if (json.isNull("lastSeenAmount")) null
                else json.optDouble("lastSeenAmount").takeIf(Double::isFinite),
                lastAnomalyAtEpochMillis = if (json.isNull("lastAnomalyAtEpochMillis")) null
                else json.optLong("lastAnomalyAtEpochMillis").takeIf { it > 0L }
            )
        }.getOrNull()
    }

    fun saveAlertState(credentialId: String, state: BalanceAlertState) {
        val json = JSONObject()
            .put("lastNotifiedAmount", state.lastNotifiedAmount ?: JSONObject.NULL)
            .put("lastLevel", state.lastLevel)
            .put("lastSeenAmount", state.lastSeenAmount ?: JSONObject.NULL)
            .put(
                "lastAnomalyAtEpochMillis",
                state.lastAnomalyAtEpochMillis ?: JSONObject.NULL
            )
        prefs.edit().putString(alertKey(credentialId), json.toString()).apply()
    }

    fun remove(credentialId: String) {
        prefs.edit()
            .remove(settingsKey(credentialId))
            .remove(alertKey(credentialId))
            .apply()
        notifyChanged()
    }

    private fun settingsKey(id: String) = "account_settings_$id"
    private fun alertKey(id: String) = "account_alert_state_$id"

    private fun notifyChanged() {
        appContext.sendBroadcast(
            Intent(BalanceRepository.ACTION_BALANCE_UPDATED).setPackage(appContext.packageName)
        )
    }

    companion object {
        private const val PREFS_NAME = "account_balance_settings"
        private const val DEFAULT_WARNING_LINE = 20.0
        private const val DEFAULT_DROP_STEP = 5.0
        private const val DEFAULT_ANOMALY_THRESHOLD = 50.0
        private const val DEFAULT_ANOMALY_PERCENT_THRESHOLD = 50.0
        private const val DEFAULT_ANOMALY_COOLDOWN_MINUTES = 1440
        private const val MIN_POSITIVE_VALUE = 0.01
        private const val MIN_COOLDOWN_MINUTES = 1
        private const val MAX_COOLDOWN_MINUTES = 10_080
        private const val MIN_REFRESH_MINUTES = 1
        private const val MAX_REFRESH_MINUTES = 1_440
    }
}
