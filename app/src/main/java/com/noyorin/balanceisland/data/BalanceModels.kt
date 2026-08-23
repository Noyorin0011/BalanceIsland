package com.noyorin.balanceisland.data

enum class BalanceCapability {
    DIRECT_BALANCE,
    USAGE_OR_LIMIT,
    KEY_CHECK_ONLY
}

enum class Provider(
    val displayName: String,
    val defaultCurrency: String,
    val keyLabel: String,
    val keyPlaceholder: String,
    val balanceCapability: BalanceCapability
) {
    DEEPSEEK(
        "DeepSeek", "CNY", "DeepSeek API Key", "sk-...", BalanceCapability.DIRECT_BALANCE
    ),
    OPENAI(
        "OpenAI", "USD", "OpenAI API Key", "sk-proj-... / sk-admin-...", BalanceCapability.USAGE_OR_LIMIT
    ),
    OPENROUTER(
        "OpenRouter", "USD", "OpenRouter Management Key", "sk-or-...", BalanceCapability.DIRECT_BALANCE
    ),
    SILICONFLOW(
        "SiliconFlow", "CNY", "SiliconFlow API Key", "sk-...", BalanceCapability.DIRECT_BALANCE
    ),
    MOONSHOT(
        "Kimi / Moonshot", "CNY", "Moonshot API Key", "sk-...", BalanceCapability.KEY_CHECK_ONLY
    ),
    ANTHROPIC(
        "Anthropic", "USD", "Anthropic API Key", "sk-ant-...", BalanceCapability.KEY_CHECK_ONLY
    ),
    GEMINI(
        "Google Gemini", "USD", "Gemini API Key", "AIza...", BalanceCapability.KEY_CHECK_ONLY
    ),
    XAI(
        "xAI / Grok", "USD", "xAI API Key", "xai-...", BalanceCapability.KEY_CHECK_ONLY
    )
}

enum class SnapshotStatus {
    OK,
    WARNING,
    CRITICAL,
    ERROR,
    NOT_CONFIGURED
}

data class ApiCredential(
    val id: String,
    val provider: Provider,
    val label: String,
    val apiKey: String,
    val keySuffix: String
)

data class CredentialSummary(
    val id: String,
    val provider: Provider,
    val label: String,
    val keySuffix: String
) {
    val displayLabel: String
        get() = label.ifBlank { "••••$keySuffix" }
}

data class BalanceSnapshot(
    val provider: Provider,
    val credentialId: String,
    val accountLabel: String,
    val keySuffix: String,
    val primaryText: String,
    val secondaryText: String,
    val balanceAmount: Double?,
    val currencyCode: String,
    val isManualBalance: Boolean,
    val status: SnapshotStatus,
    val updatedAtEpochMillis: Long
) {
    val accountDisplayLabel: String
        get() = accountLabel.ifBlank { "••••$keySuffix" }

    companion object {
        fun waiting(context: android.content.Context, credential: ApiCredential): BalanceSnapshot {
            val strings = com.noyorin.balanceisland.localization.AppLanguagePreferences.wrap(context)
            return BalanceSnapshot(
            provider = credential.provider,
            credentialId = credential.id,
            accountLabel = credential.label,
            keySuffix = credential.keySuffix,
            primaryText = strings.getString(com.noyorin.balanceisland.R.string.snapshot_waiting),
            secondaryText = strings.getString(com.noyorin.balanceisland.R.string.snapshot_tap_refresh),
            balanceAmount = null,
            currencyCode = credential.provider.defaultCurrency,
            isManualBalance = false,
            status = SnapshotStatus.NOT_CONFIGURED,
            updatedAtEpochMillis = 0L
            )
        }
    }
}

data class AccountBalanceSettings(
    val credentialId: String,
    val alertEnabled: Boolean = true,
    val warningLine: Double = 20.0,
    val dropStep: Double = 5.0,
    val manualBalance: Double? = null
)

data class BalanceAlertState(
    val lastNotifiedAmount: Double?,
    val lastLevel: Int
)
