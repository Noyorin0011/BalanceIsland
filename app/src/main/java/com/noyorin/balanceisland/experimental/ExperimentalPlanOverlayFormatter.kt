package com.noyorin.balanceisland.experimental

import android.content.Context
import com.noyorin.balanceisland.R
import com.noyorin.balanceisland.localization.AppLanguagePreferences

data class ExperimentalPlanOverlayItem(
    val kind: ExperimentalPlanWindowKind,
    val remaining: Int,
    val resetAtSeconds: Long?
)

data class ExperimentalResetCountdown(
    val days: Long,
    val hours: Long,
    val minutes: Long
)

object ExperimentalPlanOverlayFormatter {
    fun items(usage: ExperimentalPlanUsage): List<ExperimentalPlanOverlayItem> = buildList {
        usage.primaryRemaining?.let { remaining ->
            add(
                ExperimentalPlanOverlayItem(
                    kind = ExperimentalPlanWindowClassifier.classify(usage.primaryWindowSeconds),
                    remaining = remaining,
                    resetAtSeconds = usage.primaryResetAtSeconds
                )
            )
        }
        usage.secondaryRemaining?.let { remaining ->
            add(
                ExperimentalPlanOverlayItem(
                    kind = ExperimentalPlanWindowClassifier.classify(usage.secondaryWindowSeconds),
                    remaining = remaining,
                    resetAtSeconds = usage.secondaryResetAtSeconds
                )
            )
        }
    }

    fun compact(
        context: Context,
        usage: ExperimentalPlanUsage,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val strings = AppLanguagePreferences.wrap(context)
        return items(usage).takeIf { it.isNotEmpty() }?.joinToString(" · ") { item ->
            val label = strings.getString(
                when (item.kind) {
                    ExperimentalPlanWindowKind.FIVE_HOUR -> R.string.experimental_overlay_label_five_hour
                    ExperimentalPlanWindowKind.WEEKLY -> R.string.experimental_overlay_label_weekly
                    ExperimentalPlanWindowKind.UNKNOWN -> R.string.experimental_overlay_label_generic
                }
            )
            val reset = item.resetAtSeconds?.let { resetAt ->
                formatCountdown(strings, countdown(resetAt, nowMillis))
            }
            if (reset == null) {
                strings.getString(R.string.experimental_overlay_usage, label, item.remaining)
            } else {
                strings.getString(
                    R.string.experimental_overlay_usage_with_reset,
                    label,
                    item.remaining,
                    reset
                )
            }
        }
    }

    fun countdown(resetAtSeconds: Long, nowMillis: Long): ExperimentalResetCountdown {
        val remainingMinutes = (
            (resetAtSeconds - nowMillis / 1_000L).coerceAtLeast(0L) + 59L
            ) / 60L
        return ExperimentalResetCountdown(
            days = remainingMinutes / MINUTES_PER_DAY,
            hours = remainingMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR,
            minutes = remainingMinutes % MINUTES_PER_HOUR
        )
    }

    private fun formatCountdown(
        strings: Context,
        countdown: ExperimentalResetCountdown
    ): String = when {
        countdown.days > 0 -> strings.getString(
            R.string.experimental_overlay_countdown_days,
            countdown.days,
            countdown.hours
        )
        countdown.hours > 0 -> strings.getString(
            R.string.experimental_overlay_countdown_hours,
            countdown.hours,
            countdown.minutes
        )
        else -> strings.getString(R.string.experimental_overlay_countdown_minutes, countdown.minutes)
    }

    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
}
