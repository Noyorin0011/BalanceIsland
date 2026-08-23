package com.noyorin.balanceisland.device

import android.os.Build
import java.util.Locale

data class DeviceProfile(
    val id: String,
    val displayName: String,
    val collapsedWidthDp: Int,
    val collapsedHeightDp: Int,
    val expandedWidthDp: Int,
    val defaultYOffsetDp: Int,
    val statusBarTextHeightDp: Int,
    val roundedCornerSafeInsetDp: Int,
    val statusBarMaxWidthDp: Int,
    val collapsedWidthScreenFraction: Float? = null,
    val collapsedHeightScreenWidthFraction: Float? = null,
    val nativeWidthPx: Int? = null,
    val nativeHeightPx: Int? = null,
    val isColorOs: Boolean = false,
    val isOnePlus: Boolean = false
)

object DeviceProfiles {
    private val plc110 = DeviceProfile(
        id = "plc110_coloros16",
        displayName = "一加 Ace 5 至尊版（PLC110）",
        collapsedWidthDp = 192,
        collapsedHeightDp = 34,
        expandedWidthDp = 304,
        defaultYOffsetDp = 13,
        statusBarTextHeightDp = 26,
        roundedCornerSafeInsetDp = 28,
        statusBarMaxWidthDp = 210,
        collapsedWidthScreenFraction = 0.427f,
        collapsedHeightScreenWidthFraction = 0.0731f,
        nativeWidthPx = 1272,
        nativeHeightPx = 2800,
        isColorOs = true,
        isOnePlus = true
    )

    private val generic = DeviceProfile(
        id = "generic_android",
        displayName = "通用 Android 设备",
        collapsedWidthDp = 208,
        collapsedHeightDp = 38,
        expandedWidthDp = 320,
        defaultYOffsetDp = 8,
        statusBarTextHeightDp = 28,
        roundedCornerSafeInsetDp = 24,
        statusBarMaxWidthDp = 220
    )

    fun current(): DeviceProfile {
        val identity = listOf(
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.DISPLAY,
            Build.VERSION.INCREMENTAL
        ).joinToString(" ").uppercase(Locale.ROOT)
        if ("PLC110" in identity) return plc110

        val brandIdentity = "${Build.BRAND} ${Build.MANUFACTURER}".uppercase(Locale.ROOT)
        if ("ONEPLUS" in brandIdentity || "OPPO" in brandIdentity || "OPLUS" in brandIdentity) {
            return generic.copy(
                id = "coloros_generic",
                displayName = "一加 / OPPO ColorOS 设备",
                collapsedWidthDp = 196,
                collapsedHeightDp = 36,
                expandedWidthDp = 312,
                defaultYOffsetDp = 10,
                statusBarTextHeightDp = 28,
                roundedCornerSafeInsetDp = 26,
                statusBarMaxWidthDp = 216,
                isColorOs = true,
                isOnePlus = "ONEPLUS" in brandIdentity
            )
        }
        return generic
    }

    fun softwareLabel(): String {
        val incremental = Build.VERSION.INCREMENTAL.orEmpty()
        val display = Build.DISPLAY.orEmpty()
        return listOf(incremental, display)
            .firstOrNull { it.contains("16.", ignoreCase = true) }
            ?.take(48)
            ?: "Android ${Build.VERSION.RELEASE}"
    }
}
