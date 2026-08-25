package com.noyorin.balanceisland.experimental

import com.noyorin.balanceisland.display.OverlayPlanDisplayPolicy
import com.noyorin.balanceisland.display.ProviderDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentalPlanWindowClassifierTest {
    @Test
    fun classifiesFiveHourWindowsWithTolerance() {
        assertEquals(
            ExperimentalPlanWindowKind.FIVE_HOUR,
            ExperimentalPlanWindowClassifier.classify(5L * 60L * 60L)
        )
        assertEquals(
            ExperimentalPlanWindowKind.FIVE_HOUR,
            ExperimentalPlanWindowClassifier.classify(4L * 60L * 60L)
        )
        assertEquals(
            ExperimentalPlanWindowKind.FIVE_HOUR,
            ExperimentalPlanWindowClassifier.classify(6L * 60L * 60L)
        )
    }

    @Test
    fun classifiesWeeklyWindowsWithTolerance() {
        assertEquals(
            ExperimentalPlanWindowKind.WEEKLY,
            ExperimentalPlanWindowClassifier.classify(7L * 24L * 60L * 60L)
        )
        assertEquals(
            ExperimentalPlanWindowKind.WEEKLY,
            ExperimentalPlanWindowClassifier.classify(6L * 24L * 60L * 60L)
        )
        assertEquals(
            ExperimentalPlanWindowKind.WEEKLY,
            ExperimentalPlanWindowClassifier.classify(8L * 24L * 60L * 60L)
        )
    }

    @Test
    fun leavesMissingAndUnrecognizedWindowsGeneric() {
        assertEquals(
            ExperimentalPlanWindowKind.UNKNOWN,
            ExperimentalPlanWindowClassifier.classify(null)
        )
        assertEquals(
            ExperimentalPlanWindowKind.UNKNOWN,
            ExperimentalPlanWindowClassifier.classify(60L * 60L)
        )
        assertEquals(
            ExperimentalPlanWindowKind.UNKNOWN,
            ExperimentalPlanWindowClassifier.classify(24L * 60L * 60L)
        )
    }

    @Test
    fun authenticationAndRateLimitFailuresPauseAutoRefresh() {
        assertEquals(
            ExperimentalPlanReadError.AUTH,
            ExperimentalPlanReadErrorClassifier.fromHttpStatus(401)
        )
        assertEquals(
            ExperimentalPlanReadError.AUTH,
            ExperimentalPlanReadErrorClassifier.fromHttpStatus(403)
        )
        assertEquals(
            ExperimentalPlanReadError.RATE_LIMIT,
            ExperimentalPlanReadErrorClassifier.fromHttpStatus(429)
        )
        assertEquals(true, ExperimentalPlanAutoRefreshPolicy.shouldPauseAfter(ExperimentalPlanReadError.AUTH))
        assertEquals(true, ExperimentalPlanAutoRefreshPolicy.shouldPauseAfter(ExperimentalPlanReadError.RATE_LIMIT))
        assertEquals(false, ExperimentalPlanAutoRefreshPolicy.shouldPauseAfter(ExperimentalPlanReadError.NETWORK))
    }

    @Test
    fun autoRefreshDelayRunsStaleDataSoonAndFreshDataAtFiveMinutes() {
        val now = 1_000_000L
        assertEquals(
            ExperimentalPlanAutoRefreshPolicy.INITIAL_DELAY_MILLIS,
            ExperimentalPlanAutoRefreshPolicy.nextDelayMillis(0L, now, immediateIfStale = true)
        )
        assertEquals(
            ExperimentalPlanAutoRefreshPolicy.INITIAL_DELAY_MILLIS,
            ExperimentalPlanAutoRefreshPolicy.nextDelayMillis(
                now - ExperimentalPlanAutoRefreshPolicy.INTERVAL_MILLIS,
                now,
                immediateIfStale = true
            )
        )
        assertEquals(
            ExperimentalPlanAutoRefreshPolicy.INTERVAL_MILLIS - 60_000L,
            ExperimentalPlanAutoRefreshPolicy.nextDelayMillis(now - 60_000L, now, immediateIfStale = true)
        )
        assertEquals(
            ExperimentalPlanAutoRefreshPolicy.INTERVAL_MILLIS,
            ExperimentalPlanAutoRefreshPolicy.nextDelayMillis(now, now, immediateIfStale = false)
        )
    }

    @Test
    fun overlayItemsKeepWindowKindRemainingAndReset() {
        val usage = ExperimentalPlanUsage(
            planType = "plus",
            primaryRemaining = 96,
            primaryResetAtSeconds = 1_700_000_000L,
            primaryWindowSeconds = 7L * 24L * 60L * 60L,
            secondaryRemaining = null,
            secondaryResetAtSeconds = null,
            secondaryWindowSeconds = null,
            updatedAtMillis = 1_000L
        )

        assertEquals(
            listOf(
                ExperimentalPlanOverlayItem(
                    kind = ExperimentalPlanWindowKind.WEEKLY,
                    remaining = 96,
                    resetAtSeconds = 1_700_000_000L
                )
            ),
            ExperimentalPlanOverlayFormatter.items(usage)
        )
    }

    @Test
    fun resetCountdownRoundsUpToTheNextMinute() {
        val nowMillis = 1_000_000L
        val resetAtSeconds = nowMillis / 1_000L + (2L * 24L * 60L * 60L) + (3L * 60L * 60L) + 61L

        assertEquals(
            ExperimentalResetCountdown(days = 2L, hours = 3L, minutes = 2L),
            ExperimentalPlanOverlayFormatter.countdown(resetAtSeconds, nowMillis)
        )
    }

    @Test
    fun planDisplayPolicySupportsAutoCustomAndPinnedPlanModes() {
        assertEquals(
            true,
            OverlayPlanDisplayPolicy.includesPlan(
                ProviderDisplayMode.AUTO_CONFIGURED,
                includeInAuto = true,
                includeInCustomGroup = false
            )
        )
        assertEquals(
            true,
            OverlayPlanDisplayPolicy.includesPlan(
                ProviderDisplayMode.CUSTOM_GROUP,
                includeInAuto = false,
                includeInCustomGroup = true
            )
        )
        assertEquals(
            true,
            OverlayPlanDisplayPolicy.includesPlan(
                ProviderDisplayMode.PIN_EXPERIMENTAL_PLAN,
                includeInAuto = false,
                includeInCustomGroup = false
            )
        )
        assertEquals(
            false,
            OverlayPlanDisplayPolicy.includesPlan(
                ProviderDisplayMode.PIN_OPENAI,
                includeInAuto = true,
                includeInCustomGroup = true
            )
        )
    }

    @Test
    fun resetDetectorOnlyReportsAConfirmedNewCycle() {
        val previous = usage(
            resetAtSeconds = 10_000L,
            updatedAtMillis = 9_000_000L,
            remaining = 8
        )
        val current = usage(
            resetAtSeconds = 10_000L + 7L * 24L * 60L * 60L,
            updatedAtMillis = 10_100_000L,
            remaining = 100
        )

        assertEquals(
            listOf(
                ExperimentalPlanResetEvent(
                    kind = ExperimentalPlanWindowKind.WEEKLY,
                    remaining = 100,
                    resetAtSeconds = current.primaryResetAtSeconds!!
                )
            ),
            ExperimentalPlanResetDetector.detect(previous, current)
        )
    }

    @Test
    fun resetDetectorIgnoresInitialReadsFutureReschedulesAndClockJitter() {
        val current = usage(20_000L, 10_000_000L, 100)
        assertEquals(emptyList<ExperimentalPlanResetEvent>(), ExperimentalPlanResetDetector.detect(null, current))

        val futurePrevious = usage(15_000L, 9_000_000L, 50)
        assertEquals(
            emptyList<ExperimentalPlanResetEvent>(),
            ExperimentalPlanResetDetector.detect(futurePrevious, current)
        )

        val duePrevious = usage(9_900L, 9_000_000L, 50)
        val jittered = usage(10_200L, 10_000_000L, 50)
        assertEquals(
            emptyList<ExperimentalPlanResetEvent>(),
            ExperimentalPlanResetDetector.detect(duePrevious, jittered)
        )
    }

    private fun usage(
        resetAtSeconds: Long,
        updatedAtMillis: Long,
        remaining: Int
    ) = ExperimentalPlanUsage(
        planType = "plus",
        primaryRemaining = remaining,
        primaryResetAtSeconds = resetAtSeconds,
        primaryWindowSeconds = 7L * 24L * 60L * 60L,
        secondaryRemaining = null,
        secondaryResetAtSeconds = null,
        secondaryWindowSeconds = null,
        updatedAtMillis = updatedAtMillis
    )
}
