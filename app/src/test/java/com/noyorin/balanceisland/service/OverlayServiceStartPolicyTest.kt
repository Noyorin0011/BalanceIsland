package com.noyorin.balanceisland.service

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayServiceStartPolicyTest {
    @Test
    fun restartRequestReattachesAndRevealsOverlayWhenServiceAlreadyExists() {
        val decision = OverlayServiceStartPolicy.decide(
            action = OverlayServiceStartPolicy.ACTION_RESTART,
            desiredRunning = true
        )

        assertEquals(
            OverlayServiceStartDecision(
                stopService = false,
                ensureOverlayAttached = true,
                revealOverlay = true,
                refreshNow = false
            ),
            decision
        )
    }

    @Test
    fun stickyRestartWithoutIntentReattachesAndRevealsOverlay() {
        val decision = OverlayServiceStartPolicy.decide(
            action = null,
            desiredRunning = true
        )

        assertEquals(
            OverlayServiceStartDecision(
                stopService = false,
                ensureOverlayAttached = true,
                revealOverlay = true,
                refreshNow = false
            ),
            decision
        )
    }

    @Test
    fun explicitShowRequestReattachesAndRevealsOverlay() {
        val decision = OverlayServiceStartPolicy.decide(
            action = OverlayServiceStartPolicy.ACTION_SHOW_OVERLAY,
            desiredRunning = true
        )

        assertEquals(
            OverlayServiceStartDecision(
                stopService = false,
                ensureOverlayAttached = true,
                revealOverlay = true,
                refreshNow = false
            ),
            decision
        )
    }

    @Test
    fun refreshRequestRepairsAttachmentWithoutCancellingAutoHide() {
        val decision = OverlayServiceStartPolicy.decide(
            action = OverlayServiceStartPolicy.ACTION_REFRESH,
            desiredRunning = true
        )

        assertEquals(
            OverlayServiceStartDecision(
                stopService = false,
                ensureOverlayAttached = true,
                revealOverlay = false,
                refreshNow = true
            ),
            decision
        )
    }

    @Test
    fun staleRestartStopsWhenUserNoLongerWantsOverlayRunning() {
        val decision = OverlayServiceStartPolicy.decide(
            action = OverlayServiceStartPolicy.ACTION_RESTART,
            desiredRunning = false
        )

        assertEquals(
            OverlayServiceStartDecision(
                stopService = true,
                ensureOverlayAttached = false,
                revealOverlay = false,
                refreshNow = false
            ),
            decision
        )
    }

    @Test
    fun explicitStopAlwaysStopsEvenWhenRunIntentIsStillPersisted() {
        val decision = OverlayServiceStartPolicy.decide(
            action = OverlayServiceStartPolicy.ACTION_STOP,
            desiredRunning = true
        )

        assertEquals(
            OverlayServiceStartDecision(
                stopService = true,
                ensureOverlayAttached = false,
                revealOverlay = false,
                refreshNow = false
            ),
            decision
        )
    }
}
