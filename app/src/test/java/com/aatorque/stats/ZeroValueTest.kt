package com.aatorque.stats

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aatorque.datastore.Coloring
import com.aatorque.datastore.Display
import com.aatorque.datastore.Operation
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.prowl.torque.remote.ITorqueService
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the refresh rule from issue #158: a successful read is data even when it is 0.0.
 * Speed, throttle and boost are zero whenever the car stands still, and the refresh loop
 * used to drop those reads, so the gauge kept the previous number or stayed blank.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ZeroValueTest {

    /**
     * Torque is an AIDL interface with about forty methods. The refresh path calls one of
     * them, so a dynamic proxy is cheaper than a hand-written stub. Each call to the proxy
     * returns the next value; the last one repeats.
     */
    private fun serviceReturning(vararg values: Double): TorqueService {
        var call = 0
        val fake = Proxy.newProxyInstance(
            ITorqueService::class.java.classLoader,
            arrayOf(ITorqueService::class.java)
        ) { _, method, _ ->
            if (method.name == "getPIDValuesAsDouble") {
                doubleArrayOf(values[minOf(call++, values.lastIndex)])
            } else {
                throw UnsupportedOperationException(method.name)
            }
        } as ITorqueService
        return TorqueService().apply { torqueService = fake }
    }

    private fun speedGauge(alarm: Coloring? = null): TorqueData {
        val display = Display.newBuilder()
            .setPid("torque_0c")
            .setLabel("Speed")
            .setUnit(" km/h")
            .setMinValue(0)
            .setMaxValue(255)
            .apply { alarm?.let { addAlarms(it) } }
            .build()
        return TorqueData(display)
    }

    private fun refresh(refresher: TorqueRefresher, data: TorqueData, vararg values: Double) {
        refresher.doRefresh(serviceReturning(*values), data)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a zero reading reaches the gauge`() {
        val data = speedGauge()
        var updates = 0
        // The setter fires once on assignment, so count only from here.
        data.notifyUpdate = { updates++ }
        updates = 0

        refresh(TorqueRefresher(), data, 0.0)

        assertEquals(1, updates)
        assertEquals("0.0", data.lastDataStr)
    }

    @Test
    fun `a zero after a non-zero reading still reaches the gauge`() {
        val data = speedGauge()
        var updates = 0
        data.notifyUpdate = { updates++ }
        updates = 0
        val refresher = TorqueRefresher()

        refresh(refresher, data, 42.0)
        refresh(refresher, data, 0.0)

        assertEquals(2, updates)
        assertEquals("0.0", data.lastDataStr)
    }

    @Test
    fun `an alarm ignores the first zero and fires once data arrives`() {
        val lowSpeed = Coloring.newBuilder()
            .setValue(5.0)
            .setOperation(Operation.LT)
            .build()
        val data = speedGauge(lowSpeed)
        val refresher = TorqueRefresher()

        refresh(refresher, data, 0.0)
        assertNull(data.currentAlarm.value)

        refresh(refresher, data, 1.0)
        // The alarm check runs inside the lastData setter, before the refresh loop flips
        // hasReceivedNonZero, so the first non-zero read only arms it.
        refresh(refresher, data, 1.0)
        assertEquals(5.0, data.currentAlarm.value?.value ?: 0.0, 0.0)
    }
}
