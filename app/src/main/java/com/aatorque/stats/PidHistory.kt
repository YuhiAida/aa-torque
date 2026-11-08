package com.aatorque.stats

/**
 * A fixed-size record of a gauge's readings, oldest first.
 *
 * The chart used to start from nothing every time it was opened, because the only sink for a
 * reading was whichever view owned `TorqueData.notifyUpdate`. Keeping the readings here makes
 * the history independent of what is on screen, and the fixed capacity bounds the memory: at
 * the 300 ms refresh rate [DEFAULT_CAPACITY] holds about six minutes.
 *
 * Kept free of Android types so it can be unit tested.
 */
class PidHistory(private val capacity: Int = DEFAULT_CAPACITY) {

    companion object {
        /** About six minutes at TorqueRefresher.REFRESH_INTERVAL. */
        const val DEFAULT_CAPACITY = 1200
    }

    private val times = LongArray(capacity)
    private val values = DoubleArray(capacity)
    private var start = 0
    private var count = 0

    val size: Int get() = count

    /** Time of the oldest reading, or null while the record is empty. */
    val oldestTime: Long? get() = if (count == 0) null else times[start]

    /**
     * A copy of the readings, oldest first, taken under the same lock as [add]. The refresh
     * loop writes from a worker thread while the chart seeds itself on the main thread, so a
     * reader that walked the ring directly could pair one reading's time with another's value.
     */
    @Synchronized
    fun snapshot(): List<Reading> = (0 until count).map { i ->
        Reading(times[(start + i) % capacity], values[(start + i) % capacity])
    }

    class Reading(val time: Long, val value: Double)

    @Synchronized
    fun add(time: Long, value: Double) {
        if (count < capacity) {
            times[(start + count) % capacity] = time
            values[(start + count) % capacity] = value
            count++
            return
        }
        // Full: the oldest reading makes room for the new one.
        times[start] = time
        values[start] = value
        start = (start + 1) % capacity
    }

    fun timeAt(index: Int): Long = times[(start + index) % capacity]

    fun valueAt(index: Int): Double = values[(start + index) % capacity]

    fun clear() {
        start = 0
        count = 0
    }
}
