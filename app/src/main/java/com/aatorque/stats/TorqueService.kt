package com.aatorque.stats

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.prowl.torque.remote.ITorqueService
import timber.log.Timber

class TorqueService {
    var torqueService: ITorqueService? = null
    val onConnect = ArrayList<(ITorqueService) -> Unit>()
    val conLock = ReentrantLock()
    var hasBound = false

    fun addConnectCallback(func: (ITorqueService) -> Unit): TorqueService {
        if (torqueService == null) {
            conLock.withLock {
                onConnect.add(func)
            }
        } else {
            func(torqueService!!)
        }
        return this
    }

    fun runIfConnected(func: (ITorqueService) -> Unit) {
        if (torqueService != null) {
            func(torqueService!!)
        }
    }

    private val torqueConnection = object : ServiceConnection {
        var forceReconnect: (() -> Unit)? = null

        /**
         * What to do when we get connected to Torque.
         *
         * @param arg0
         * @param service
         */
        override fun onServiceConnected(arg0: ComponentName, service: IBinder) {
            try {
                val svc = ITorqueService.Stub.asInterface(service)
                // Torque's test mode outlives our process. An earlier build enabled it when no
                // ECU was connected, and Torque then feeds random values that look like live
                // data. Simulation belongs to the app now, so always turn Torque's off.
                try {
                    svc.setDebugTestMode(false)
                } catch (e: RemoteException) {
                    Timber.w(e, "Could not disable Torque test mode")
                }
                torqueService = svc
                conLock.withLock {
                    for (funt in onConnect) {
                        funt(svc)
                    }
                    onConnect.clear()
                }
            } catch (e: DeadObjectException) {
                Timber.e("Disconnected from service", e)
                torqueService = null
                forceReconnect?.invoke()
            }
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

    fun onDestroy(context: Context) {
        if (torqueService != null) {
            context.unbindService(torqueConnection)
            hasBound = false
            torqueService = null
        }
    }

    fun requestQuit(context: Context) {
        context.sendBroadcast(Intent("org.prowl.torque.REQUEST_TORQUE_QUIT"))
        Timber.i("Torque stop")
    }
    fun startTorque(context: Context): Boolean {
        val intent = Intent()
        intent.setClassName("org.prowl.torque", "org.prowl.torque.remote.TorqueService")
        hasBound = context.bindService(intent, torqueConnection, Activity.BIND_AUTO_CREATE)
        torqueConnection.forceReconnect = {
            onDestroy(context)
            context.bindService(intent, torqueConnection, Activity.BIND_AUTO_CREATE)
        }
        val message = if (hasBound) {
            "Connected to torque service!"
        } else {
            "Unable to connect to Torque plugin service"
        }
        Timber.i(message)
        return hasBound
    }
}
