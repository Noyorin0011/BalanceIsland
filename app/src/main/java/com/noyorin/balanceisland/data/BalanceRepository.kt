package com.noyorin.balanceisland.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit

class BalanceRepository(private val context: Context) {
    private val keys = SecureKeyStore(context)
    private val snapshots = SnapshotStore(context)
    private val accountSettings = AccountSettingsStore(context)
    private val alertNotifier = BalanceAlertNotifier(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun refreshAll(): List<BalanceSnapshot> = withContext(Dispatchers.IO) {
        val rawResult = keys.credentials().map(::refreshOne)
        rawResult.forEach(snapshots::save)
        val result = rawResult.map(::applyUserSettings)
        result.forEach(alertNotifier::evaluate)
        context.sendBroadcast(Intent(ACTION_BALANCE_UPDATED).setPackage(context.packageName))
        result
    }

    fun cached(): List<BalanceSnapshot> = keys.credentials()
        .map(snapshots::get)
        .map(::applyUserSettings)

    fun removeCached(credentialId: String) = snapshots.remove(credentialId)

    private fun refreshOne(credential: ApiCredential): BalanceSnapshot {
        return runCatching {
            when (credential.provider) {
                Provider.DEEPSEEK -> fetchDeepSeek(credential)
                Provider.OPENAI -> fetchOpenAi(credential)
                Provider.OPENROUTER -> fetchOpenRouter(credential)
                Provider.SILICONFLOW -> fetchSiliconFlow(credential)
                Provider.MOONSHOT -> verifyBearerKey(
                    credential,
                    "https://api.moonshot.cn/v1/models"
                )
                Provider.ANTHROPIC -> verifyAnthropicKey(credential)
                Provider.GEMINI -> verifyBearerKey(
                    credential,
                    "https://generativelanguage.googleapis.com/v1beta/openai/models"
                )
                Provider.XAI -> verifyBearerKey(credential, "https://api.x.ai/v1/models")
            }
        }.getOrElse { throwable ->
            BalanceSnapshot(
                provider = credential.provider,
                credentialId = credential.id,
                accountLabel = credential.label,
                keySuffix = credential.keySuffix,
                primaryText = "查询失败",
                secondaryText = readableError(throwable),
                balanceAmount = null,
                currencyCode = credential.provider.defaultCurrency,
                isManualBalance = false,
                status = SnapshotStatus.ERROR,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }

    private fun fetchDeepSeek(credential: ApiCredential): BalanceSnapshot {
        val json = getJson(
            url = "https://api.deepseek.com/user/balance",
            bearerToken = credential.apiKey
        )
        val infos = json.optJSONArray("balance_infos")
            ?: throw ApiException("响应中没有余额信息")
        if (infos.length() == 0) throw ApiException("账户没有可用币种")

        val selected = (0 until infos.length())
            .map { infos.getJSONObject(it) }
            .firstOrNull { it.optString("currency") == "CNY" }
            ?: infos.getJSONObject(0)

        val currency = selected.optString("currency", "CNY")
        val total = selected.optString("total_balance", "0").toDoubleOrNull() ?: 0.0
        val granted = selected.optString("granted_balance", "0").toDoubleOrNull() ?: 0.0
        val toppedUp = selected.optString("topped_up_balance", "0").toDoubleOrNull() ?: 0.0
        val available = json.optBoolean("is_available", total > 0)
        val sign = if (currency == "CNY") "¥" else "$"

        return BalanceSnapshot(
            provider = Provider.DEEPSEEK,
            credentialId = credential.id,
            accountLabel = credential.label,
            keySuffix = credential.keySuffix,
            primaryText = "$sign${money(total)}",
            secondaryText = "充值 $sign${money(toppedUp)} · 赠送 $sign${money(granted)}",
            balanceAmount = total,
            currencyCode = currency,
            isManualBalance = false,
            status = if (available) SnapshotStatus.OK else SnapshotStatus.WARNING,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun fetchOpenAi(credential: ApiCredential): BalanceSnapshot {
        val now = System.currentTimeMillis() / 1000
        val monthStart = LocalDate.now(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toEpochSecond()
        val costsUrl = "https://api.openai.com/v1/organization/costs".toHttpUrl()
            .newBuilder()
            .addQueryParameter("start_time", monthStart.toString())
            .addQueryParameter("end_time", now.toString())
            .addQueryParameter("bucket_width", "1d")
            .addQueryParameter("limit", "31")
            .build()

        val costsJson = getJson(costsUrl.toString(), credential.apiKey)
        var spent = 0.0
        val buckets = costsJson.optJSONArray("data")
            ?: throw ApiException("响应中没有消费数据")
        for (i in 0 until buckets.length()) {
            val results = buckets.getJSONObject(i).optJSONArray("results") ?: continue
            for (j in 0 until results.length()) {
                spent += results.getJSONObject(j)
                    .optJSONObject("amount")
                    ?.optDouble("value", 0.0) ?: 0.0
            }
        }

        val spendLimit = runCatching {
            getJson("https://api.openai.com/v1/organization/spend_limit", credential.apiKey)
        }.getOrNull()
        val limit = spendLimit?.optDouble("threshold_amount", Double.NaN)
            ?.takeUnless(Double::isNaN)
            ?.div(100.0)

        return if (limit != null) {
            val remaining = (limit - spent).coerceAtLeast(0.0)
            BalanceSnapshot(
                provider = Provider.OPENAI,
                credentialId = credential.id,
                accountLabel = credential.label,
                keySuffix = credential.keySuffix,
                primaryText = "$${money(remaining)} 可用",
                secondaryText = "本月已用 $${money(spent)} / 上限 $${money(limit)}",
                balanceAmount = remaining,
                currencyCode = "USD",
                isManualBalance = false,
                status = if (remaining <= limit * 0.1) SnapshotStatus.WARNING else SnapshotStatus.OK,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        } else {
            BalanceSnapshot(
                provider = Provider.OPENAI,
                credentialId = credential.id,
                accountLabel = credential.label,
                keySuffix = credential.keySuffix,
                primaryText = "本月 $${money(spent)}",
                secondaryText = "未设置可读取的硬消费上限",
                balanceAmount = null,
                currencyCode = "USD",
                isManualBalance = false,
                status = SnapshotStatus.OK,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        }
    }

    private fun fetchOpenRouter(credential: ApiCredential): BalanceSnapshot {
        val data = getJson(
            "https://openrouter.ai/api/v1/credits",
            credential.apiKey
        ).optJSONObject("data") ?: throw ApiException("响应中没有额度数据")
        val purchased = data.number("total_credits")
        val used = data.number("total_usage")
        val remaining = (purchased - used).coerceAtLeast(0.0)
        return BalanceSnapshot(
            provider = credential.provider,
            credentialId = credential.id,
            accountLabel = credential.label,
            keySuffix = credential.keySuffix,
            primaryText = "$${money(remaining)}",
            secondaryText = "累计充值 $${money(purchased)} · 已用 $${money(used)}",
            balanceAmount = remaining,
            currencyCode = "USD",
            isManualBalance = false,
            status = SnapshotStatus.OK,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun fetchSiliconFlow(credential: ApiCredential): BalanceSnapshot {
        val data = getJson(
            "https://api.siliconflow.cn/v1/user/info",
            credential.apiKey
        ).optJSONObject("data") ?: throw ApiException("响应中没有账户数据")
        val total = data.numberOrNull("totalBalance")
            ?: data.numberOrNull("total_balance")
            ?: data.numberOrNull("balance")
            ?: throw ApiException("响应中没有可识别的余额字段")
        val charged = data.numberOrNull("chargeBalance")
            ?: data.numberOrNull("charge_balance")
        val gifted = data.numberOrNull("balance")
        return BalanceSnapshot(
            provider = credential.provider,
            credentialId = credential.id,
            accountLabel = credential.label,
            keySuffix = credential.keySuffix,
            primaryText = "¥${money(total)}",
            secondaryText = buildString {
                if (charged != null) append("充值 ¥${money(charged)}")
                if (gifted != null) {
                    if (isNotEmpty()) append(" · ")
                    append("赠送 ¥${money(gifted)}")
                }
                if (isEmpty()) append("官方账户总余额")
            },
            balanceAmount = total,
            currencyCode = "CNY",
            isManualBalance = false,
            status = SnapshotStatus.OK,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
    }

    private fun verifyBearerKey(credential: ApiCredential, url: String): BalanceSnapshot {
        getJson(url, credential.apiKey)
        return verifiedKeySnapshot(credential)
    }

    private fun verifyAnthropicKey(credential: ApiCredential): BalanceSnapshot {
        getJson(
            url = "https://api.anthropic.com/v1/models",
            headers = mapOf(
                "x-api-key" to credential.apiKey,
                "anthropic-version" to "2023-06-01"
            )
        )
        return verifiedKeySnapshot(credential)
    }

    private fun verifiedKeySnapshot(credential: ApiCredential) = BalanceSnapshot(
        provider = credential.provider,
        credentialId = credential.id,
        accountLabel = credential.label,
        keySuffix = credential.keySuffix,
        primaryText = "Key 可用",
        secondaryText = "官方未向普通 API Key 提供剩余余额，请设置手动余额",
        balanceAmount = null,
        currencyCode = credential.provider.defaultCurrency,
        isManualBalance = false,
        status = SnapshotStatus.OK,
        updatedAtEpochMillis = System.currentTimeMillis()
    )

    private fun applyUserSettings(raw: BalanceSnapshot): BalanceSnapshot {
        val settings = accountSettings.get(raw.credentialId)
        val amount = settings.manualBalance ?: raw.balanceAmount
        val status = when {
            !settings.alertEnabled -> if (settings.manualBalance != null) SnapshotStatus.OK else raw.status
            amount == null -> raw.status
            amount <= settings.warningLine -> SnapshotStatus.CRITICAL
            amount <= settings.warningLine * NEAR_LINE_MULTIPLIER -> SnapshotStatus.WARNING
            else -> raw.status
        }
        if (settings.manualBalance == null) return raw.copy(status = status)

        return raw.copy(
            primaryText = "${currencySymbol(raw.currencyCode)}${money(settings.manualBalance)}",
            secondaryText = "手动余额 · 接口状态：${raw.primaryText}",
            balanceAmount = settings.manualBalance,
            isManualBalance = true,
            status = status
        )
    }

    private fun currencySymbol(code: String): String = when (code.uppercase(Locale.ROOT)) {
        "CNY", "RMB" -> "¥"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "$"
    }

    private fun getJson(url: String, bearerToken: String): JSONObject {
        return getJson(url, mapOf("Authorization" to "Bearer $bearerToken"))
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw ApiException(
                    if (message.isBlank()) "HTTP ${response.code}" else "HTTP ${response.code}: $message"
                )
            }
            if (body.isBlank()) throw ApiException("服务返回空响应")
            JSONObject(body)
        }
    }

    private fun readableError(throwable: Throwable): String = when (throwable) {
        is ApiException -> throwable.message ?: "API 返回错误"
        is IOException -> "网络连接失败，请稍后重试"
        else -> throwable.message?.take(100) ?: "未知错误"
    }

    private fun money(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun JSONObject.number(name: String): Double = numberOrNull(name)
        ?: throw ApiException("响应中没有 $name")

    private fun JSONObject.numberOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private class ApiException(message: String) : Exception(message)

    companion object {
        private const val NEAR_LINE_MULTIPLIER = 1.5
        const val ACTION_BALANCE_UPDATED = "com.noyorin.balanceisland.BALANCE_UPDATED"
    }
}
