package com.only4.cap4k.ddd.domain.distributed.snowflake

/**
 * SnowflakeId生成算法
 */
class SnowflakeIdGenerator(
    // 机器ID
    private val workerId: Long,
    // 数据中心ID
    private val datacenterId: Long
) {
    // 序列号
    private var sequence = 0L

    // 上一次生成ID的时间戳
    private var lastTimestamp = -1L

    // 机器ID向左移的位数
    private val workerIdShift = SEQUENCE_BITS

    // 数据中心ID向左移的位数
    private val datacenterIdShift = SEQUENCE_BITS + WORKER_ID_BITS

    // 时间戳向左移的位数
    private val timestampShift = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS

    init {
        require(workerId in 0..MAX_WORKER_ID) {
            "workerId must be between 0 and $MAX_WORKER_ID"
        }
        require(datacenterId in 0..MAX_DATACENTER_ID) {
            "datacenterId must be between 0 and $MAX_DATACENTER_ID"
        }
    }

    @Synchronized
    fun nextId(): Long {
        var timestamp = System.currentTimeMillis()

        // 如果当前时间小于上一次生成ID的时间戳，说明系统时钟回退过，应抛出异常
        if (timestamp < lastTimestamp) {
            throw RuntimeException("Clock moved backwards. Refusing to generate id for ${lastTimestamp - timestamp} milliseconds")
        }

        // 如果是同一毫秒内生成的，则进行序列号自增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) and MAX_SEQUENCE
            // 序列号已经达到最大值，需要等待下一毫秒
            if (sequence == 0L) {
                timestamp = waitNextMillis(timestamp)
            }
        } else {
            // 不同毫秒内生成的序列号归零
            sequence = 0L
        }

        lastTimestamp = timestamp
        // 生成全局唯一ID
        return ((timestamp - EPOCH) shl timestampShift.toInt()) or
                (datacenterId shl datacenterIdShift.toInt()) or
                (workerId shl workerIdShift.toInt()) or
                sequence
    }

    private fun waitNextMillis(currentTimestamp: Long): Long {
        var timestamp = System.currentTimeMillis()
        while (timestamp <= currentTimestamp) {
            timestamp = System.currentTimeMillis()
        }
        return timestamp
    }

    companion object {
        // 开始时间戳，用于计算相对时间
        const val EPOCH = 1706716800000L // 2024-01-01 00:00:00 UTC+8

        // 机器ID占用的位数
        const val WORKER_ID_BITS = 5L

        // 数据中心ID占用的位数
        const val DATACENTER_ID_BITS = 5L

        // 序列号占用的位数
        const val SEQUENCE_BITS = 12L

        // 最大机器ID（2的workerIdBits次方-1）
        const val MAX_WORKER_ID = -1L xor (-1L shl WORKER_ID_BITS.toInt())

        // 最大数据中心ID（2的datacenterIdBits次方-1）
        const val MAX_DATACENTER_ID = -1L xor (-1L shl DATACENTER_ID_BITS.toInt())

        // 序列号的最大值（2的sequenceBits次方-1）
        const val MAX_SEQUENCE = -1L xor (-1L shl SEQUENCE_BITS.toInt())
    }
}
