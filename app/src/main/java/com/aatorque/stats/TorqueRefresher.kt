package com.aatorque.stats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.aatorque.datastore.Display
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

    private val executor = ScheduledThreadPoolExecutor(7)
    val handler = Handler(Looper.getMainLooper())
    private var conWatcher = MutableStateFlow(ConnectStatus.CONNECTING_TORQUE)
    val connectStatus = conWatcher.asSharedFlow()
    val cache = mutableMapOf<Int, HashMap<Int, TorqueData>>()

    companion object {
        const val REFRESH_INTERVAL = 300L
    }

    fun populateQuery(pos: Int, screen: Int, query: Display): TorqueData {
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
        return torqueData
    }

    suspend fun makeExecutors(service: TorqueService) {
        var foundValid = false
        data.values.forEachIndexed { index, torqueData ->
            val refreshOffset = (REFRESH_INTERVAL / data.size) * index
            if (torqueData.pid != null) {
                foundValid = true
                if (torqueData.refreshTimer == null) {
                    Timber.i("Scheduled item in position $index with $refreshOffset delay")
                    doRefresh(service, torqueData)
                    torqueData.refreshTimer = executor.scheduleWithFixedDelay({
                        try {
                            doRefresh(service, torqueData)
                        } catch (e: Exception) {
                            Timber.e("Refresh failed in pos $index", e)
                        }
                    }, refreshOffset, REFRESH_INTERVAL, TimeUnit.MILLISECONDS)
                }
            } else {
                Timber.i("No reason to schedule item in position $index")
            }
        }
        conWatcher.emit(if (foundValid) conWatcher.value else ConnectStatus.SETUP_GAUGE)
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
            // Publish every successful read, including 0.0. A real zero is data: speed and
            // throttle are zero when the car stands still. The flag stays for the alarm
            // threshold check in TorqueData, which must ignore the values Torque reports
            // before the ECU answers at all.
            if (value != 0.0) {
                torqueData.hasReceivedNonZero = true
            }
            handler.post {
                torqueData.sendNotifyUpdate()
            }
            conWatcher.tryEmit(ConnectStatus.CONNECTED)
        }
    }

    fun stopExecutors() {
        Timber.i("Telling Torque refreshers to stop")
        for (td in data.values) {
            td.stopRefreshing()
        }
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
