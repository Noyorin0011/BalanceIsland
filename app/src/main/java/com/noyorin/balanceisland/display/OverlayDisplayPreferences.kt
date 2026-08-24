package com.noyorin.balanceisland.display

import android.content.Context
import android.content.Intent
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.data.BalanceSnapshot
import com.noyorin.balanceisland.data.Provider
import com.noyorin.balanceisland.localization.AppLanguagePreferences
import java.util.Locale

enum class ProviderDisplayMode(val provider: Provider?) {
    AUTO_CONFIGURED(null),
    CUSTOM_GROUP(null),
    PIN_DEEPSEEK(Provider.DEEPSEEK),
    PIN_OPENAI(Provider.OPENAI),
    PIN_OPENROUTER(Provider.OPENROUTER),
    PIN_SILICONFLOW(Provider.SILICONFLOW),
    PIN_MOONSHOT(Provider.MOONSHOT),
    PIN_MIMO(Provider.MIMO),
    PIN_ANTHROPIC(Provider.ANTHROPIC),
    PIN_GEMINI(Provider.GEMINI),
    PIN_XAI(Provider.XAI)
}

enum class StatusBarPositionPreset(
    val alignStart: Boolean,
    val roundedCornerSafeArea: Boolean
) {
    LEFT_SAFE(true, true),
    RIGHT_SAFE(false, true),
    LEFT_EDGE(true, false),
    RIGHT_EDGE(false, false)
}

enum class StatusBarVisualStyle {
    TEXT_ONLY,
    TRANSLUCENT_PILL,
    OUTLINED_TEXT,
    ADAPTIVE_PILL
}

enum class BalanceContentMode {
    BALANCE_ONLY,
    TODAY_AND_BALANCE,
    AUTO_ROTATE
}

object BalanceTextFormatter {
    fun compact(context: Context, snapshot: BalanceSnapshot, showToday: Boolean): String {
        val todayUsed = snapshot.todayUsedAmount
        val balance = snapshot.balanceAmount
        if (!showToday || todayUsed == null || balance == null) return snapshot.primaryText
        val strings = AppLanguagePreferences.wrap(context)
        val format = if (snapshot.todayUsageIsEstimated) {
            R.string.status_today_estimated
        } else {
            R.string.status_today_balance
        }
        return strings.getString(
            format,
            amount(snapshot.currencyCode, todayUsed),
            amount(snapshot.currencyCode, balance)
        )
    }

    fun amount(currencyCode: String, value: Double): String {
        val symbol = when (currencyCode.uppercase(Locale.ROOT)) {
            "CNY", "RMB" -> "¥"
            "EUR" -> "€"
            "GBP" -> "£"
            else -> "$"
        }
        return symbol + String.format(Locale.US, "%.2f", value)
    }
}

/** Contrast helpers shared by the settings preview and the real overlay. */
object StatusBarContrast {
    fun isLight(argb: Int): Boolean {
        val red = ColorChannel.red(argb) / 255.0
        val green = ColorChannel.green(argb) / 255.0
        val blue = ColorChannel.blue(argb) / 255.0
        val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        return luminance >= 0.42
    }

    fun outlineColorFor(textColor: Int): Int =
        if (isLight(textColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    fun backgroundColorFor(textColor: Int): Int =
        if (isLight(textColor)) 0xB8000000.toInt() else 0xD9FFFFFF.toInt()

    private fun linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92
        else Math.pow((channel + 0.055) / 1.055, 2.4)

    private object ColorChannel {
        fun red(color: Int) = color shr 16 and 0xFF
        fun green(color: Int) = color shr 8 and 0xFF
        fun blue(color: Int) = color and 0xFF
    }
}

enum class StatusBarTextColor(val argb: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    MINT(0xFF73E0C1.toInt()),
    SKY(0xFF68B4FF.toInt()),
    CORAL(0xFFFF8A75.toInt()),
    LIME(0xFFB7DD43.toInt())
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

    fun providerGroup(): Set<Provider> = prefs
        .getStringSet(KEY_PROVIDER_GROUP, emptySet())
        .orEmpty()
        .mapNotNullTo(linkedSetOf()) { name ->
            runCatching { Provider.valueOf(name) }.getOrNull()
        }

    fun setProviderGroup(providers: Set<Provider>) {
        prefs.edit().putStringSet(KEY_PROVIDER_GROUP, providers.mapTo(linkedSetOf()) { it.name })
            .apply()
        notifyChanged()
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

    fun contentMode(): BalanceContentMode = enumPreference(
        KEY_CONTENT_MODE,
        BalanceContentMode.BALANCE_ONLY
    )

    fun setContentMode(value: BalanceContentMode) = putString(KEY_CONTENT_MODE, value.name)

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

    fun verticalOffsetDp(): Int = prefs.getInt(KEY_VERTICAL_OFFSET_DP, 0)

    fun setVerticalOffsetDp(value: Int) {
        prefs.edit().putInt(KEY_VERTICAL_OFFSET_DP, value.coerceIn(0, 72)).apply()
        notifyChanged()
    }

    fun contentWidthDp(): Int = prefs.getInt(KEY_CONTENT_WIDTH_DP, DEFAULT_CONTENT_WIDTH_DP)
        .coerceIn(MIN_CONTENT_WIDTH_DP, MAX_CONTENT_WIDTH_DP)

    fun setContentWidthDp(value: Int) {
        prefs.edit().putInt(
            KEY_CONTENT_WIDTH_DP,
            value.coerceIn(MIN_CONTENT_WIDTH_DP, MAX_CONTENT_WIDTH_DP)
        ).apply()
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

    fun autoHideEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_HIDE_ENABLED, false)

    fun setAutoHideEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE_ENABLED, enabled).apply()
        notifyChanged()
    }

    fun autoHideMinutes(): Int =
        prefs.getInt(KEY_AUTO_HIDE_MINUTES, DEFAULT_AUTO_HIDE_MINUTES)
            .coerceIn(MIN_AUTO_HIDE_MINUTES, MAX_AUTO_HIDE_MINUTES)

    fun setAutoHideMinutes(value: Int) {
        prefs.edit().putInt(
            KEY_AUTO_HIDE_MINUTES,
            value.coerceIn(MIN_AUTO_HIDE_MINUTES, MAX_AUTO_HIDE_MINUTES)
        ).apply()
        notifyChanged()
    }

    fun select(snapshots: List<BalanceSnapshot>): List<BalanceSnapshot> {
        return when (val displayMode = mode()) {
            ProviderDisplayMode.AUTO_CONFIGURED -> snapshots
            ProviderDisplayMode.CUSTOM_GROUP -> {
                val selected = providerGroup()
                snapshots.filter { it.provider in selected }.ifEmpty { snapshots }
            }
            else -> snapshots.filter { it.provider == displayMode.provider }.ifEmpty { snapshots }
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
        private const val KEY_PROVIDER_GROUP = "provider_group"
        private const val KEY_POSITION_PRESET = "status_bar_position_preset"
        private const val KEY_VISUAL_STYLE = "status_bar_visual_style"
        private const val KEY_CONTENT_MODE = "balance_content_mode"
        private const val KEY_TEXT_COLOR = "status_bar_text_color"
        private const val KEY_HORIZONTAL_OFFSET_DP = "status_bar_horizontal_offset_dp"
        private const val KEY_VERTICAL_OFFSET_DP = "status_bar_vertical_adjustment_dp"
        private const val KEY_CONTENT_WIDTH_DP = "status_bar_content_width_dp"
        private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
        private const val KEY_AUTO_HIDE_ENABLED = "auto_hide_enabled"
        private const val KEY_AUTO_HIDE_MINUTES = "auto_hide_minutes"
        private const val DEFAULT_REFRESH_INTERVAL_MINUTES = 1
        private const val MIN_REFRESH_INTERVAL_MINUTES = 1
        private const val MAX_REFRESH_INTERVAL_MINUTES = 1440
        private const val DEFAULT_AUTO_HIDE_MINUTES = 30
        private const val MIN_AUTO_HIDE_MINUTES = 5
        private const val MAX_AUTO_HIDE_MINUTES = 1440
        private const val DEFAULT_CONTENT_WIDTH_DP = 220
        private const val MIN_CONTENT_WIDTH_DP = 72
        private const val MAX_CONTENT_WIDTH_DP = 320
        const val ACTION_DISPLAY_SETTINGS_CHANGED =
            "com.noyorin.balanceisland.DISPLAY_SETTINGS_CHANGED"
    }
}
