package com.aatorque.stats

import com.aatorque.datastore.Display
import kotlin.math.round

/**
 * Values for the metrics simulation.
 *
 * A display sweeps from its configured minimum to its maximum and back, so a test run shows
 * the needle over the whole scale instead of random jumps. Each PID starts at a different
 * point of the cycle, so the gauges do not move in step.
 */
object SimulatedValue {
    const val PERIOD_MS = 20_000L

    /** Used when a display has no usable range, so the gauge still moves. */
    private const val FALLBACK_MAX = 100.0

    fun at(display: Display, elapsedMs: Long, periodMs: Long = PERIOD_MS): Double {
        require(periodMs > 0) { "periodMs must be positive" }
        val configuredMin = display.minValue.toDouble()
        val configuredMax = display.maxValue.toDouble()
        val low = minOf(configuredMin, configuredMax)
        val high = maxOf(configuredMin, configuredMax)
        val from: Double
        val to: Double
        when {
            high > low -> {
                from = low
                to = high
            }

            low == 0.0 -> {
                from = 0.0
                to = FALLBACK_MAX
            }

            else -> return low
        }
        val phase = ((elapsedMs + offsetMs(display.pid)) % periodMs).toDouble() / periodMs
        val triangle = if (phase < 0.5) phase * 2 else (1 - phase) * 2
        val value = from + (to - from) * triangle
        return if (display.wholeNumbers) round(value) else value
    }

    /** Stable per-PID phase offset, so two gauges do not sweep together. */
    internal fun offsetMs(pid: String): Long = (pid.hashCode().toLong() and 0xFFFF) % PERIOD_MS
}
