package com.aatorque.stats

/**
 * One published card frame.
 *
 * Android Auto fetches the artwork by URI, possibly one frame behind the newest. Keeping the
 * snapshot with the frame id means the provider redraws exactly the frame AA asked for.
 */
data class CardSnapshot(
    val style: CardStyle,
    val theme: String,
    val metrics: List<CardMetric>,
    val subtitle: String,
    val description: String
)

/**
 * Small ring of the last published frames, keyed by frame id.
 *
 * Deliberately tiny and lock-based: the service publishes from a coroutine and the
 * ContentProvider reads from a binder thread.
 */
object CardFrames {

    /** Enough to cover AA being a few frames behind without holding old bitmaps. */
    private const val KEEP = 64

    private val frames = LinkedHashMap<Int, CardSnapshot>()

    @Synchronized
    fun publish(id: Int, snapshot: CardSnapshot) {
        frames[id] = snapshot
        while (frames.size > KEEP) {
            frames.remove(frames.keys.first())
        }
    }

    @Synchronized
    fun snapshot(id: Int): CardSnapshot? = frames[id]

    @Synchronized
    fun clear() {
        frames.clear()
    }
}

/**
 * The two text lines Android Auto shows over the artwork.
 *
 * Pure string work on purpose: the card's text is the part a driver actually reads, so it is
 * the part worth testing without a device.
 */
object CardText {

    fun subtitle(metrics: List<CardMetric>): String =
        metrics.take(2).joinToString(" | ") { "${it.title} ${it.value}" }

    fun description(ecuLive: Boolean, hasValues: Boolean): String = when {
        ecuLive && hasValues -> "ECU live"
        ecuLive -> "ECU connected - no data yet"
        else -> "No ECU - open Torque"
    }
}
