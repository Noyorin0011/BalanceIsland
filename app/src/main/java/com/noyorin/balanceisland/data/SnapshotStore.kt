package com.noyorin.balanceisland.data

import android.content.Context
import org.json.JSONObject

class SnapshotStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: BalanceSnapshot) {
        val json = JSONObject()
            .put("provider", snapshot.provider.name)
            .put("credentialId", snapshot.credentialId)
            .put("accountLabel", snapshot.accountLabel)
            .put("keySuffix", snapshot.keySuffix)
            .put("primaryText", snapshot.primaryText)
            .put("secondaryText", snapshot.secondaryText)
            .put("balanceAmount", snapshot.balanceAmount ?: JSONObject.NULL)
            .put("currencyCode", snapshot.currencyCode)
            .put("isManualBalance", snapshot.isManualBalance)
            .put("status", snapshot.status.name)
            .put("updatedAt", snapshot.updatedAtEpochMillis)
            .put("todayUsedAmount", snapshot.todayUsedAmount ?: JSONObject.NULL)
            .put("todayUsageIsEstimated", snapshot.todayUsageIsEstimated)
        prefs.edit().putString(keyFor(snapshot.credentialId), json.toString()).apply()
    }

    fun get(credential: ApiCredential): BalanceSnapshot {
        val raw = prefs.getString(keyFor(credential.id), null)
            ?: legacySnapshot(credential)
            ?: return BalanceSnapshot.waiting(appContext, credential)
        return runCatching {
            val json = JSONObject(raw)
            BalanceSnapshot(
                provider = credential.provider,
                credentialId = credential.id,
                accountLabel = credential.label,
                keySuffix = credential.keySuffix,
                primaryText = json.getString("primaryText"),
                secondaryText = json.getString("secondaryText"),
                balanceAmount = if (json.isNull("balanceAmount")) null
                else json.optDouble("balanceAmount").takeIf(Double::isFinite),
                currencyCode = json.optString(
                    "currencyCode",
                    credential.provider.defaultCurrency
                ),
                isManualBalance = json.optBoolean("isManualBalance", false),
                status = SnapshotStatus.valueOf(json.getString("status")),
                updatedAtEpochMillis = json.optLong("updatedAt", 0L),
                todayUsedAmount = if (json.isNull("todayUsedAmount")) null
                else json.optDouble("todayUsedAmount").takeIf(Double::isFinite),
                todayUsageIsEstimated = json.optBoolean("todayUsageIsEstimated", false)
            )
        }.getOrElse { BalanceSnapshot.waiting(appContext, credential) }
    }

    fun remove(credentialId: String) {
        prefs.edit().remove(keyFor(credentialId)).apply()
    }

    private fun legacySnapshot(credential: ApiCredential): String? {
        if (!credential.id.startsWith("legacy_")) return null
        return prefs.getString("snapshot_${credential.provider.name.lowercase()}", null)
    }

    private fun keyFor(credentialId: String) = "snapshot_$credentialId"

    companion object {
        private const val PREFS_NAME = "balance_snapshots"
    }
}
