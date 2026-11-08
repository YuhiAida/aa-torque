package com.aatorque.stats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.aatorque.datastore.Display
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runInterruptible
import timber.log.Timber

enum class ConnectStatus {
    CONNECTING_TORQUE,
    CONNECTING_ECU,
    CONNECTED,
    SETUP_GAUGE,

    /** Torque Pro is missing, so no connection can ever be made. See issue #160. */
    NO_TORQUE,

    /**
     * The bind failed while Torque Pro may still be installed: no permission to bind, or a
     * service that refuses the caller. Say only that the connection failed. See issue #160.
     */
    TORQUE_UNAVAILABLE
}

class TorqueRefresher {
    val data = HashMap<Int, TorqueData>()

    /** True while the developer option simulates values instead of reading Torque. */
    var simulate = false

    private val executor = ScheduledThreadPoolExecutor(1)
    val handler = Handler(Looper.getMainLooper())
    private var conWatcher = MutableStateFlow(ConnectStatus.CONNECTING_TORQUE)
    val connectStatus = conWatcher.asSharedFlow()
    val cache = mutableMapOf<Int, HashMap<Int, TorqueData>>()
    private var refreshTimer: ScheduledFuture<*>? = null

    companion object {
        /**
         * How often the dashboard reads Torque. Torque returns the value its own adapter loop
         * already fetched, so this sets how fresh the screen is, not the OBD load.
         */
        const val REFRESH_INTERVAL = 300L

        /**
         * How long a needle takes to reach a new value. Deliberately shorter than
         * [REFRESH_INTERVAL]: the needle lands, holds, then moves again. An animation that
         * lasts the whole interval never finishes, so the needle creeps until the next sample.
         */
        const val NEEDLE_ANIM_MS = 200L
    }

    fun populateQuery(pos: Int, screen: Int, query: Display): TorqueData = synchronized(data) {
        data[pos]?.stopRefreshing(true)
        val cacheItem = cache[screen]?.get(pos)
        val torqueData = if (cacheItem?.display?.equals(query) == true) {
            cacheItem
        } else {
            TorqueData(query)
        }
        cache.getOrPut(screen) {
            HashMap()
        }[pos] = torqueData
        data[pos] = torqueData
        Timber.i("Setting query: $query for pos $pos on screen $screen")
        torqueData
    }

    suspend fun makeExecutors(service: TorqueService) {
        val targets = activeTargets()
        if (targets.isEmpty()) {
            stopRefreshTimer()
        } else if (refreshTimer == null) {
            // One task for the whole dashboard: every gauge is read and applied in the same
            // tick, so they share a phase and move on the same frame.
            Timber.i("Scheduling the dashboard refresh for ${targets.size} items")
            refreshTimer = executor.scheduleWithFixedDelay({
                try {
                    refreshDashboard(service)
                } catch (e: Exception) {
                    Timber.e("Dashboard refresh failed", e)
                }
            }, 0L, REFRESH_INTERVAL, TimeUnit.MILLISECONDS)
        }
        val status = if (targets.isNotEmpty()) conWatcher.value else ConnectStatus.SETUP_GAUGE
        conWatcher.emit(status)
        service.addConnectCallback {
            if (conWatcher.value == ConnectStatus.CONNECTING_TORQUE) {
                conWatcher.tryEmit(ConnectStatus.CONNECTING_ECU)
            }
        }
    }

    /**
     * The bind to Torque failed, so no connect callback will ever advance the state. Say so
     * instead of leaving the car on an unbounded "Connecting to Torque…" message.
     */
    fun reportTorqueMissing() {
        conWatcher.tryEmit(ConnectStatus.NO_TORQUE)
    }

    /** The bind failed, but the package may be present; do not claim it is missing. */
    fun reportTorqueUnavailable() {
        conWatcher.tryEmit(ConnectStatus.TORQUE_UNAVAILABLE)
    }

    /**
     * One dashboard read: every gauge PID in a single call, then one main-thread post so all
     * the needles move on the same frame. Public so the JVM test can drive it without a
     * scheduler.
     */
    fun refreshDashboard(service: TorqueService) {
        val targets = activeTargets()
        if (targets.isEmpty()) {
            return
        }
        if (simulate) {
            targets.forEach { doSimulatedRefresh(it) }
            return
        }
        val pids = targets.mapNotNull { it.pid }
        if (pids.size != targets.size) {
            targets.forEach { doRefresh(service, it) }
            return
        }
        service.runIfConnected { ts ->
            val values = try {
                ts.getPIDValuesAsDouble(pids.toTypedArray())
            } catch (e: Exception) {
                Timber.e(e, "Torque failed the batched dashboard refresh")
                null
            }
            if (values == null || values.size != targets.size) {
                Timber.w("Torque returned ${values?.size ?: 0} values for ${pids.size} PIDs")
                targets.forEach { doRefresh(service, it) }
                return@runIfConnected
            }
            applyValues(targets, values)
            handler.post {
                targets.forEach { it.sendNotifyUpdate() }
            }
            conWatcher.tryEmit(ConnectStatus.CONNECTED)
        }
    }

    /**
     * Put a batched Torque result on its targets, in request order. Every successful read is
     * published, including 0.0; only a non-zero read arms the alarm gate. See issue #158.
     */
    fun applyValues(targets: List<TorqueData>, values: DoubleArray) {
        targets.forEachIndexed { index, torqueData ->
            val value = values.getOrNull(index) ?: return@forEachIndexed
            torqueData.lastData = value
            if (value != 0.0) {
                torqueData.hasReceivedNonZero = true
            }
        }
    }

    fun doRefresh(service: TorqueService, torqueData: TorqueData) {
        if (simulate) {
            doSimulatedRefresh(torqueData)
            return
        }
        service.runIfConnected { ts ->
            val value = try {
                ts.getPIDValuesAsDouble(arrayOf(torqueData.pid!!))[0]
            } catch (e: ArrayIndexOutOfBoundsException) {
                Timber.e("Torque returned invalid data for ${torqueData.pid}")
                return@runIfConnected
            }
            torqueData.lastData = value
            Timber.d("Got valid $value from torque for ${torqueData.display.label}")
            // A real zero is data: speed and throttle are zero when the car stands still. The
            // flag stays for the alarm threshold check in TorqueData, which must ignore the
            // values Torque reports before the ECU answers at all.
            if (value != 0.0) {
                torqueData.hasReceivedNonZero = true
            }
            handler.post {
                torqueData.sendNotifyUpdate()
            }
            conWatcher.tryEmit(ConnectStatus.CONNECTED)
        }
    }

    private fun activeTargets(): List<TorqueData> =
        synchronized(data) { data.values.filter { it.pid != null } }

    fun stopExecutors() {
        Timber.i("Telling Torque refreshers to stop")
        stopRefreshTimer()
        synchronized(data) { data.values.toList() }.forEach { it.stopRefreshing() }
    }

    private fun stopRefreshTimer() {
        refreshTimer?.cancel(false)
        refreshTimer = null
    }

    private fun doSimulatedRefresh(torqueData: TorqueData) {
        val value = SimulatedValue.at(torqueData.display, SystemClock.elapsedRealtime())
        torqueData.hasReceivedNonZero = true
        torqueData.lastData = value
        handler.post {
            torqueData.sendNotifyUpdate()
        }
        conWatcher.tryEmit(ConnectStatus.CONNECTED)
    }

    fun hasChanged(idx: Int, otherScreen: Display?): Boolean {
        if (!data.containsKey(idx)) return true
        return data[idx]?.display?.equals(otherScreen) != true
    }

    fun updateIfNeeded(pos: Int, screen: Int, query: Display): TorqueData =
        if (hasChanged(pos, query)) {
            populateQuery(pos, screen, query)
        } else {
            data[pos]!!
        }
}
