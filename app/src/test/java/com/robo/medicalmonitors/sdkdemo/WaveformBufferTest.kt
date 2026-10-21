package com.robo.medicalmonitors.sdkdemo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformBufferTest {
    @Test fun wrapKeepsNewestFramesAndChannelOrder() {
        val buffer = WaveformBuffer(2, 3)
        repeat(5) { buffer.add(it.toLong(), intArrayOf(it, -it)) }
        val snapshot = WaveformBuffer.Snapshot(2, 3)
        buffer.copyInto(snapshot, 0, 10)
        assertEquals(3, snapshot.count)
        assertArrayEquals(longArrayOf(2, 3, 4), snapshot.times)
        assertArrayEquals(floatArrayOf(2f, -2f, 3f, -3f, 4f, -4f), snapshot.values, 0f)
    }

    @Test fun frozenWindowExcludesFutureFrames() {
        val buffer = WaveformBuffer(1, 8)
        repeat(6) { buffer.add(it * 100L, it.toFloat()) }
        val snapshot = WaveformBuffer.Snapshot(1, 8)
        buffer.copyInto(snapshot, 200, 400)
        assertEquals(3, snapshot.count)
        assertArrayEquals(longArrayOf(200, 300, 400), snapshot.times.copyOf(snapshot.count))
    }

    @Test fun clearLeavesNoOldSamplesAfterReconnect() {
        val buffer = WaveformBuffer(1, 3)
        buffer.add(100, 42f)
        buffer.clear()
        buffer.add(20, 7f)
        val snapshot = WaveformBuffer.Snapshot(1, 3)
        buffer.copyInto(snapshot, 0, 200)
        assertEquals(1, snapshot.count)
        assertEquals(20L, snapshot.times[0])
        assertEquals(7f, snapshot.values[0], 0f)
    }

    @Test fun receivedArrayIsCopiedAndOutOfOrderTimeDoesNotReversePlot() {
        val buffer = WaveformBuffer(2, 3)
        val values = intArrayOf(10, 20)
        buffer.add(100, values)
        values[0] = 999
        buffer.add(90, intArrayOf(30, 40))
        val snapshot = WaveformBuffer.Snapshot(2, 3)
        buffer.copyInto(snapshot, 0, 200)
        assertEquals(10f, snapshot.values[0], 0f)
        assertArrayEquals(longArrayOf(100, 100), snapshot.times.copyOf(2))
    }
}
