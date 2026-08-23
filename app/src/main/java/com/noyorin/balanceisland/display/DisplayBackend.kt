package com.noyorin.balanceisland.display

import android.content.Context
import com.noyorin.balanceisland.device.DeviceProfiles
import com.noyorin.balanceisland.service.IslandOverlayService

enum class BackendAvailability {
    AVAILABLE,
    REQUIRES_VENDOR_APPROVAL,
    UNSUPPORTED
}

data class DisplayBackendInfo(
    val name: String,
    val availability: BackendAvailability,
    val explanation: String
)

interface DisplayBackend {
    val info: DisplayBackendInfo
    fun start(context: Context): Boolean
    fun stop(context: Context)
}

/**
 * Public placeholder for OPPO/OnePlus Fluid Cloud integration.
 *
 * OPPO's official route uses a Pantanal UPK plus SeedlingSupportSDK. A UPK must
 * be published to the vendor service library before the host app can create and
 * update its SystemUI card. This class deliberately does not reflect into hidden
 * SystemUI APIs or impersonate media notifications.
 */
class ColorOsFluidCloudBackend : DisplayBackend {
    override val info = DisplayBackendInfo(
        name = "ColorOS 原生流体云",
        availability = if (DeviceProfiles.current().isColorOs) {
            BackendAvailability.REQUIRES_VENDOR_APPROVAL
        } else {
            BackendAvailability.UNSUPPORTED
        },
        explanation = "设备支持，但还需要已发布的 UPK、服务 ID/事件码及 SeedlingSupportSDK；侧载包无法强制启用。"
    )

    override fun start(context: Context): Boolean = false
    override fun stop(context: Context) = Unit
}

class OverlayDisplayBackend : DisplayBackend {
    override val info = DisplayBackendInfo(
        name = "状态栏余额文字条",
        availability = BackendAvailability.AVAILABLE,
        explanation = "无需厂商合作权限，支持 PLC110 左右圆角安全区、位置微调和多账户轮播。"
    )

    override fun start(context: Context): Boolean {
        IslandOverlayService.start(context)
        return true
    }

    override fun stop(context: Context) = IslandOverlayService.stop(context)
}

object DisplayBackendSelector {
    val native = ColorOsFluidCloudBackend()
    val fallback = OverlayDisplayBackend()
}
