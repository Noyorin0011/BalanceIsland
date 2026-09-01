package com.noyorin.balanceisland.service

/** Keeps WindowManager operations keyed to ownership rather than first-frame attachment. */
internal object OverlayWindowRegistration {
    fun ensureAttached(windowOwnsView: Boolean, addView: () -> Unit) {
        if (!windowOwnsView) addView()
    }
}
