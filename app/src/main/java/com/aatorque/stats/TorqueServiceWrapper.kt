package com.aatorque.stats

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.prowl.torque.remote.ITorqueService
import timber.log.Timber

typealias PidInfo = List<Pair<String, List<String>>>
class TorqueServiceWrapper : Service() {
    private val binder = LocalBinder()
    var wasStartAttempted = false
    var torqueBindSucceeded = false
    var torqueService: ITorqueService? = null
    val onConnect = ArrayList<(ITorqueService) -> Unit>()
    var pids: PidInfo? = null
    var connectCount = 0
    val conLock = ReentrantLock()

    override fun onCreate() {
        super.onCreate()
        if (!wasStartAttempted) {
            wasStartAttempted = true
            torqueBindSucceeded = startTorque()
        }
        connectCount++
    }

    override fun onDestroy() {
        super.onDestroy()
        connectCount--
        if (connectCount == 0) {
            unbindService(torqueConnection)
        }
    }

    class ListPids(val ts: ITorqueService, val onComplete: (PidInfo) -> Unit) : Runnable {
        override fun run() {
            // Torque's dictionary holds every PID it knows about, including the many the
            // connected car never answers. Offering those means a gauge can be bound to a PID
            // that reports 0 forever with no warning. Prefer what the ECU actually supports,
            // and keep Torque's own synthetic metrics (ids starting with "ff": GPS speed,
            // acceleration, power) because those never come from the ECU.
            val all = ts.listAllPIDs().toList()
            val supported = try {
                ts.listECUSupportedPIDs()?.toSet() ?: emptySet()
            } catch (e: Exception) {
                emptySet()
            }
            val pids = selectPids(all, supported).toTypedArray()
            val pidAttributes = ts.getPIDInformation(pids).map { it.split(",") }

            onComplete(
                pids.zip(pidAttributes).sortedBy { it.second[0] }
            )
        }
    }

    fun loadPidInformation(force: Boolean = false, onComplete: ((PidInfo) -> Unit)? = null) {
        if (pids != null && !force) {
            onComplete?.invoke(pids!!)
            return
        }
        // When Torque is not available, do not leave callers waiting forever: hand back
        // whatever was cached (or an empty list) so settings can be configured off-car.
        // Fresh data from a live Torque connection still wins when it arrives later.
        if (torqueService == null) {
            val fallback = pids ?: emptyList()
            if (!torqueBindSucceeded) {
                onComplete?.invoke(fallback)
                return
            }
            // Torque was reachable at bind time but is not connected yet: give callers an
            // immediate (cached/empty) result and also queue the fresh load below.
            onComplete?.invoke(fallback)
        }
        addConnectCallback {
            val bg = ListPids(it) { newPids ->
                this.pids = newPids
                onComplete?.invoke(newPids)
            }
            Thread(bg).start()
        }
    }

    /**
     * Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): TorqueServiceWrapper = this@TorqueServiceWrapper
    }

    fun isAvailable(): Boolean = torqueService != null

    fun addConnectCallback(func: (ITorqueService) -> Unit): TorqueServiceWrapper {
        if (torqueService == null) {
            conLock.withLock {
                onConnect.add(func)
            }
        } else {
            func(torqueService!!)
        }
        return this
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val torqueConnection: ServiceConnection = object : ServiceConnection {
        /**
         * What to do when we get connected to Torque.
         *
         * @param arg0
         * @param service
         */
        override fun onServiceConnected(arg0: ComponentName, service: IBinder) {
            val svc = ITorqueService.Stub.asInterface(service)
            conLock.withLock {
                for (funt in onConnect) {
                    funt(svc)
                }
                onConnect.clear()
            }
            torqueService = svc
        }

        /**
         * What to do when we get disconnected from Torque.
         *
         * @param name
         */
        override fun onServiceDisconnected(name: ComponentName) {
            torqueService = null
        }
    }

    fun startTorque(): Boolean {
        val intent = Intent()
        intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService")
        val torqueBind = bindService(intent, torqueConnection, Activity.BIND_AUTO_CREATE)
        val message = if (torqueBind) {
            "Connected to torque service!"
        } else {
            "Unable to connect to Torque plugin service"
        }
        Timber.i(message)
        return torqueBind
    }

    companion object {
        fun runStartIntent(context: Context, conn: ServiceConnection): Boolean {
            Intent(context, TorqueServiceWrapper::class.java).also { intent ->
                return context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            }
        }
    }
}
