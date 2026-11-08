package com.aatorque.stats

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aatorque.datastore.Display
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.prowl.torque.remote.ITorqueService
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The dashboard reads every gauge PID in one call now. These tests drive that tick directly,
 * so they pin what the batch must keep from the per-PID path: each value lands on its own
 * gauge, a zero reading still reaches the gauge (issue #158), and a short result does not
 * leave half the dashboard on its previous number.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BatchedRefreshTest {

    /**
     * Torque is an AIDL interface, so a dynamic proxy doubles for it, the same way
     * [ZeroValueTest] does it. The fake answers by PID, so the order of the request does not
     * matter, and it records every request it sees.
     */
    private class FakeTorque(
        private val values: Map<String, Double>,
        private val shorten: Boolean = false
    ) {
        val calls = mutableListOf<List<String>>()

        fun service(): TorqueService {
            val fake = Proxy.newProxyInstance(
                ITorqueService::class.java.classLoader,
                arrayOf(ITorqueService::class.java)
            ) { _, method, args ->
                if (method.name != "getPIDValuesAsDouble") {
                    throw UnsupportedOperationException(method.name)
                }
                @Suppress("UNCHECKED_CAST")
                val pids = (args!![0] as Array<String>).toList()
                calls.add(pids)
                val answer = pids.map { values[it] ?: 0.0 }
                (if (shorten) answer.take(1) else answer).toDoubleArray()
            } as ITorqueService
            return TorqueService().apply { torqueService = fake }
        }
    }

    private fun gauge(refresher: TorqueRefresher, pos: Int, pid: String): TorqueData {
        val display = Display.newBuilder()
            .setPid("torque_$pid")
            .setLabel(pid)
            .setMinValue(0)
            .setMaxValue(255)
            .build()
        val data = TorqueData(display)
        refresher.data[pos] = data
        return data
    }

    @Test
    fun `one call reads every gauge`() {
        val refresher = TorqueRefresher()
        val rpm = gauge(refresher, 0, "0c")
        val coolant = gauge(refresher, 1, "05")
        val torque = FakeTorque(mapOf("0c" to 1500.0, "05" to 90.0))

        refresher.refreshDashboard(torque.service())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, torque.calls.size)
        assertEquals(setOf("0c", "05"), torque.calls[0].toSet())
        assertEquals(2, torque.calls[0].size)
        assertEquals(1500.0, rpm.lastData, 0.0)
        assertEquals(90.0, coolant.lastData, 0.0)
    }

    @Test
    fun `a zero reading still reaches the gauge`() {
        val refresher = TorqueRefresher()
        val speed = gauge(refresher, 0, "0d")
        var updates = 0
        // The setter fires once on assignment, so count only from here.
        speed.notifyUpdate = { updates++ }
        updates = 0
        val torque = FakeTorque(mapOf("0d" to 0.0))

        refresher.refreshDashboard(torque.service())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, updates)
        assertEquals("0.0", speed.lastDataStr)
        assertFalse(speed.hasReceivedNonZero)
    }

    @Test
    fun `a short result falls back to one call per gauge`() {
        val refresher = TorqueRefresher()
        val rpm = gauge(refresher, 0, "0c")
        val coolant = gauge(refresher, 1, "05")
        val torque = FakeTorque(mapOf("0c" to 1500.0, "05" to 90.0), shorten = true)

        refresher.refreshDashboard(torque.service())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(3, torque.calls.size)
        assertTrue(torque.calls.drop(1).all { it.size == 1 })
        assertEquals(1500.0, rpm.lastData, 0.0)
        assertEquals(90.0, coolant.lastData, 0.0)
    }
}
