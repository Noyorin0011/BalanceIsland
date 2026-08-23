package com.noyorin.balanceisland.data

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Per-account balance override, warning threshold and notification cadence. */
class AccountSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(credentialId: String): AccountBalanceSettings {
        val raw = prefs.getString(settingsKey(credentialId), null)
            ?: return AccountBalanceSettings(credentialId)
        return runCatching {
            val json = JSONObject(raw)
            AccountBalanceSettings(
                credentialId = credentialId,
                alertEnabled = json.optBoolean("alertEnabled", true),
                warningLine = json.optDouble("warningLine", DEFAULT_WARNING_LINE)
                    .coerceAtLeast(MIN_POSITIVE_VALUE),
                dropStep = json.optDouble("dropStep", DEFAULT_DROP_STEP)
                    .coerceAtLeast(MIN_POSITIVE_VALUE),
                manualBalance = if (json.isNull("manualBalance")) {
                    null
                } else {
                    json.optDouble("manualBalance").takeIf { it.isFinite() && it >= 0.0 }
                }
            )
        }.getOrElse { AccountBalanceSettings(credentialId) }
    }

    fun save(settings: AccountBalanceSettings) {
        require(settings.warningLine > 0.0) { "警告线必须大于 0" }
        require(settings.dropStep > 0.0) { "下降提醒步长必须大于 0" }
        require(settings.manualBalance == null || settings.manualBalance >= 0.0) {
            "手动余额不能小于 0"
        }
        val json = JSONObject()
            .put("alertEnabled", settings.alertEnabled)
            .put("warningLine", settings.warningLine)
            .put("dropStep", settings.dropStep)
            .put("manualBalance", settings.manualBalance ?: JSONObject.NULL)
        prefs.edit()
            .putString(settingsKey(settings.credentialId), json.toString())
            .remove(alertKey(settings.credentialId))
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
                lastLevel = json.optInt("lastLevel", 0)
            )
        }.getOrNull()
    }

    fun saveAlertState(credentialId: String, state: BalanceAlertState) {
        val json = JSONObject()
            .put("lastNotifiedAmount", state.lastNotifiedAmount ?: JSONObject.NULL)
            .put("lastLevel", state.lastLevel)
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
        private const val MIN_POSITIVE_VALUE = 0.01
    }
}
