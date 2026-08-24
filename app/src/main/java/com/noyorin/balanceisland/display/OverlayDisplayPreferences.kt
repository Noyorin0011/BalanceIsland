package com.noyorin.balanceisland.display

import android.content.Context
import android.content.Intent
import com.noyorin.balanceisland.data.BalanceSnapshot
import com.noyorin.balanceisland.data.Provider

enum class ProviderDisplayMode(val provider: Provider?) {
    AUTO_CONFIGURED(null),
    PIN_DEEPSEEK(Provider.DEEPSEEK),
    PIN_OPENAI(Provider.OPENAI),
    PIN_OPENROUTER(Provider.OPENROUTER),
    PIN_SILICONFLOW(Provider.SILICONFLOW),
    PIN_MOONSHOT(Provider.MOONSHOT),
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

    fun verticalOffsetDp(): Int = prefs.getInt(KEY_VERTICAL_OFFSET_DP, 0)

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
        private const val KEY_VERTICAL_OFFSET_DP = "status_bar_vertical_adjustment_dp"
        private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
        private const val DEFAULT_REFRESH_INTERVAL_MINUTES = 1
        private const val MIN_REFRESH_INTERVAL_MINUTES = 1
        private const val MAX_REFRESH_INTERVAL_MINUTES = 1440
        const val ACTION_DISPLAY_SETTINGS_CHANGED =
            "com.noyorin.balanceisland.DISPLAY_SETTINGS_CHANGED"
    }
}
