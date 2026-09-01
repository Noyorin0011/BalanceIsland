package com.noyorin.balanceisland.service

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowRegistrationTest {
    @Test
    fun consecutiveRestoreCommandsRegisterWindowOnlyOnce() {
        var windowOwnsView = false
        var addCount = 0

        OverlayWindowRegistration.ensureAttached(windowOwnsView) {
            addCount += 1
            windowOwnsView = true
        }
        OverlayWindowRegistration.ensureAttached(windowOwnsView) {
            addCount += 1
            windowOwnsView = true
        }

        assertEquals(1, addCount)
    }

    @Test
    fun restoreRegistersAgainAfterWindowOwnershipIsLost() {
        var windowOwnsView = false
        var addCount = 0

        OverlayWindowRegistration.ensureAttached(windowOwnsView) {
            addCount += 1
            windowOwnsView = true
        }
        windowOwnsView = false
        OverlayWindowRegistration.ensureAttached(windowOwnsView) {
            addCount += 1
            windowOwnsView = true
        }

        assertEquals(2, addCount)
    }
}
