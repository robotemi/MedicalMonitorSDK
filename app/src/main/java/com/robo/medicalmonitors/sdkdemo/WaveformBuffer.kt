package com.robo.medicalmonitors.sdkdemo

class WaveformBuffer(
    private val channelCount: Int,
    private val capacity: Int
) {
    class Snapshot(channelCount: Int, capacity: Int) {
        val times = LongArray(capacity)
        val values = FloatArray(channelCount * capacity)
        var count: Int = 0
            internal set
    }

    private val times = LongArray(capacity)
    private val values = FloatArray(channelCount * capacity)
    private var start = 0
    private var size = 0
    private var lastTime = Long.MIN_VALUE

    init {
        require(channelCount > 0) { "channelCount must be positive" }
        require(capacity > 0) { "capacity must be positive" }
    }

    @Synchronized
    fun add(time: Long, value: Float) {
        require(channelCount == 1) { "Single-value samples require one channel" }
        add(time, floatArrayOf(value))
    }

    @Synchronized
    fun add(time: Long, sample: IntArray) {
        require(sample.size == channelCount) { "Expected $channelCount channels" }
        add(time, FloatArray(channelCount) { sample[it].toFloat() })
    }

    @Synchronized
    fun clear() {
        start = 0
        size = 0
        lastTime = Long.MIN_VALUE
    }

    @Synchronized
    fun copyInto(snapshot: Snapshot, fromTime: Long, toTime: Long) {
        snapshot.count = 0
        for (offset in 0 until size) {
            val sourceIndex = (start + offset) % capacity
            val time = times[sourceIndex]
            if (time !in fromTime..toTime) continue

            val targetIndex = snapshot.count
            if (targetIndex >= snapshot.times.size) break
            snapshot.times[targetIndex] = time
            values.copyInto(
                destination = snapshot.values,
                destinationOffset = targetIndex * channelCount,
                startIndex = sourceIndex * channelCount,
                endIndex = (sourceIndex + 1) * channelCount
            )
            snapshot.count++
        }
    }

    private fun add(time: Long, sample: FloatArray) {
        val normalizedTime = maxOf(time, lastTime)
        val targetIndex = if (size < capacity) {
            val index = (start + size) % capacity
            size++
            index
        } else {
            val index = start
            start = (start + 1) % capacity
            index
        }

        times[targetIndex] = normalizedTime
        sample.copyInto(values, targetIndex * channelCount)
        lastTime = normalizedTime
    }
}
