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
    val keyHelp: String,
    val balanceCapability: BalanceCapability
) {
    DEEPSEEK(
        "DeepSeek", "CNY", "DeepSeek API Key", "sk-...",
        "直接读取账户余额。", BalanceCapability.DIRECT_BALANCE
    ),
    OPENAI(
        "OpenAI", "USD", "OpenAI Admin API Key", "sk-admin-...",
        "Costs 需要组织 Owner 创建的 Admin Key。", BalanceCapability.USAGE_OR_LIMIT
    ),
    OPENROUTER(
        "OpenRouter", "USD", "OpenRouter Management Key", "sk-or-...",
        "余额接口需要 Management Key，普通推理 Key 可能返回 403。", BalanceCapability.DIRECT_BALANCE
    ),
    SILICONFLOW(
        "SiliconFlow", "CNY", "SiliconFlow API Key", "sk-...",
        "通过官方 /user/info 接口读取总余额。", BalanceCapability.DIRECT_BALANCE
    ),
    MOONSHOT(
        "Kimi / Moonshot", "CNY", "Moonshot API Key", "sk-...",
        "普通 Key 仅验证可用性；余额请在账户设置中手动填写。", BalanceCapability.KEY_CHECK_ONLY
    ),
    ANTHROPIC(
        "Anthropic", "USD", "Anthropic API Key", "sk-ant-...",
        "普通 Key 不提供剩余余额；验证后可设置手动余额。", BalanceCapability.KEY_CHECK_ONLY
    ),
    GEMINI(
        "Google Gemini", "USD", "Gemini API Key", "AIza...",
        "Google AI Studio Key 不提供剩余余额；验证后可设置手动余额。", BalanceCapability.KEY_CHECK_ONLY
    ),
    XAI(
        "xAI / Grok", "USD", "xAI API Key", "xai-...",
        "普通 Key 不提供剩余余额；验证后可设置手动余额。", BalanceCapability.KEY_CHECK_ONLY
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
        fun waiting(credential: ApiCredential) = BalanceSnapshot(
            provider = credential.provider,
            credentialId = credential.id,
            accountLabel = credential.label,
            keySuffix = credential.keySuffix,
            primaryText = "等待查询",
            secondaryText = "点击立即刷新",
            balanceAmount = null,
            currencyCode = credential.provider.defaultCurrency,
            isManualBalance = false,
            status = SnapshotStatus.NOT_CONFIGURED,
            updatedAtEpochMillis = 0L
        )
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
