package com.noyorin.balanceisland.display

import android.content.Context
import android.content.Intent
import com.noyorin.balanceisland.data.BalanceSnapshot
import com.noyorin.balanceisland.data.Provider

enum class ProviderDisplayMode(val label: String, val provider: Provider?) {
    AUTO_CONFIGURED("自动轮播", null),
    PIN_DEEPSEEK("固定 DeepSeek", Provider.DEEPSEEK),
    PIN_OPENAI("固定 OpenAI", Provider.OPENAI),
    PIN_OPENROUTER("固定 OpenRouter", Provider.OPENROUTER),
    PIN_SILICONFLOW("固定 SiliconFlow", Provider.SILICONFLOW),
    PIN_MOONSHOT("固定 Kimi / Moonshot", Provider.MOONSHOT),
    PIN_ANTHROPIC("固定 Anthropic", Provider.ANTHROPIC),
    PIN_GEMINI("固定 Google Gemini", Provider.GEMINI),
    PIN_XAI("固定 xAI / Grok", Provider.XAI)
}

enum class StatusBarPositionPreset(
    val label: String,
    val alignStart: Boolean,
    val roundedCornerSafeArea: Boolean
) {
    LEFT_SAFE("左上·圆角安全区", true, true),
    RIGHT_SAFE("右上·圆角安全区", false, true),
    LEFT_EDGE("左上·贴边", true, false),
    RIGHT_EDGE("右上·贴边", false, false)
}

enum class StatusBarVisualStyle(val label: String) {
    TEXT_ONLY("透明文字"),
    TRANSLUCENT_PILL("半透明底")
}

enum class StatusBarTextColor(val label: String, val argb: Int) {
    WHITE("白色", 0xFFFFFFFF.toInt()),
    MINT("薄荷", 0xFF73E0C1.toInt()),
    SKY("天蓝", 0xFF68B4FF.toInt()),
    CORAL("珊瑚", 0xFFFF8A75.toInt()),
    LIME("青柠", 0xFFB7DD43.toInt())
}

/** Controls which configured providers are allowed to appear in the island. */
class OverlayDisplayPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun mode(): ProviderDisplayMode = runCatching {
        ProviderDisplayMode.valueOf(
            prefs.getString(KEY_PROVIDER_DISPLAY_MODE, null)
                ?: ProviderDisplayMode.AUTO_CONFIGURED.name
        )
    }.getOrDefault(ProviderDisplayMode.AUTO_CONFIGURED)

    fun setMode(value: ProviderDisplayMode) {
        putString(KEY_PROVIDER_DISPLAY_MODE, value.name)
    }

    fun position(): StatusBarPositionPreset = enumPreference(
        KEY_POSITION_PRESET,
        StatusBarPositionPreset.LEFT_SAFE
    )

    fun setPosition(value: StatusBarPositionPreset) = putString(KEY_POSITION_PRESET, value.name)

    fun visualStyle(): StatusBarVisualStyle = enumPreference(
        KEY_VISUAL_STYLE,
        StatusBarVisualStyle.TEXT_ONLY
    )

    fun setVisualStyle(value: StatusBarVisualStyle) = putString(KEY_VISUAL_STYLE, value.name)

    fun textColor(): StatusBarTextColor = enumPreference(
        KEY_TEXT_COLOR,
        StatusBarTextColor.WHITE
    )

    fun setTextColor(value: StatusBarTextColor) = putString(KEY_TEXT_COLOR, value.name)

    fun horizontalOffsetDp(): Int = prefs.getInt(KEY_HORIZONTAL_OFFSET_DP, 0)

    fun setHorizontalOffsetDp(value: Int) {
        prefs.edit().putInt(KEY_HORIZONTAL_OFFSET_DP, value.coerceIn(0, 160)).apply()
        notifyChanged()
    }

    fun verticalOffsetDp(defaultValue: Int): Int =
        prefs.getInt(KEY_VERTICAL_OFFSET_DP, defaultValue)

    fun setVerticalOffsetDp(value: Int) {
        prefs.edit().putInt(KEY_VERTICAL_OFFSET_DP, value.coerceIn(0, 72)).apply()
        notifyChanged()
    }

    fun refreshIntervalMinutes(): Int =
        prefs.getInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL_MINUTES)
            .coerceIn(MIN_REFRESH_INTERVAL_MINUTES, MAX_REFRESH_INTERVAL_MINUTES)

    fun setRefreshIntervalMinutes(value: Int) {
        prefs.edit().putInt(
            KEY_REFRESH_INTERVAL_MINUTES,
            value.coerceIn(MIN_REFRESH_INTERVAL_MINUTES, MAX_REFRESH_INTERVAL_MINUTES)
        ).apply()
        notifyChanged()
    }

    fun select(snapshots: List<BalanceSnapshot>): List<BalanceSnapshot> {
        val pinned = mode().provider
        return if (pinned == null) {
            snapshots
        } else {
            snapshots.filter { it.provider == pinned }.ifEmpty { snapshots }
        }
    }

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T =
        runCatching {
            enumValueOf<T>(prefs.getString(key, null) ?: fallback.name)
        }.getOrDefault(fallback)

    private fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        notifyChanged()
    }

    private fun notifyChanged() {
        appContext.sendBroadcast(
            Intent(ACTION_DISPLAY_SETTINGS_CHANGED).setPackage(appContext.packageName)
        )
    }

    companion object {
        private const val PREFS_NAME = "overlay_settings"
        private const val KEY_PROVIDER_DISPLAY_MODE = "provider_display_mode"
        private const val KEY_POSITION_PRESET = "status_bar_position_preset"
        private const val KEY_VISUAL_STYLE = "status_bar_visual_style"
        private const val KEY_TEXT_COLOR = "status_bar_text_color"
        private const val KEY_HORIZONTAL_OFFSET_DP = "status_bar_horizontal_offset_dp"
        private const val KEY_VERTICAL_OFFSET_DP = "y_offset_dp"
        private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
        private const val DEFAULT_REFRESH_INTERVAL_MINUTES = 1
        private const val MIN_REFRESH_INTERVAL_MINUTES = 1
        private const val MAX_REFRESH_INTERVAL_MINUTES = 1440
        const val ACTION_DISPLAY_SETTINGS_CHANGED =
            "com.noyorin.balanceisland.DISPLAY_SETTINGS_CHANGED"
    }
}
