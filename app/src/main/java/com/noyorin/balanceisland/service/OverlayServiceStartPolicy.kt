package com.noyorin.balanceisland.service

internal data class OverlayServiceStartDecision(
    val stopService: Boolean,
    val ensureOverlayAttached: Boolean,
    val revealOverlay: Boolean,
    val refreshNow: Boolean
)

/** Converts service intents and persisted user intent into restart-safe behavior. */
internal object OverlayServiceStartPolicy {
    const val ACTION_STOP = "com.noyorin.balanceisland.STOP_OVERLAY"
    const val ACTION_RESTART = "com.noyorin.balanceisland.RESTART_OVERLAY"
    const val ACTION_REFRESH = "com.noyorin.balanceisland.REFRESH_OVERLAY"
    const val ACTION_SHOW_OVERLAY = "com.noyorin.balanceisland.SHOW_OVERLAY"

    fun decide(action: String?, desiredRunning: Boolean): OverlayServiceStartDecision {
        if (action == ACTION_STOP || !desiredRunning) {
            return OverlayServiceStartDecision(
                stopService = true,
                ensureOverlayAttached = false,
                revealOverlay = false,
                refreshNow = false
            )
        }

        val revealOverlay = action == null ||
            action == ACTION_RESTART ||
            action == ACTION_SHOW_OVERLAY
        return OverlayServiceStartDecision(
            stopService = false,
            ensureOverlayAttached = true,
            revealOverlay = revealOverlay,
            refreshNow = action == ACTION_REFRESH
        )
    }
}
