package com.only4.cap4k.ddd.domain.distributed.snowflake

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("雪花ID生成器测试")
class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("测试构造函数参数验证")
    fun testConstructorValidation() {
        // 测试正常构造
        val generator = SnowflakeIdGenerator(1L, 1L)
        assertTrue(true) // 构造成功

        // 测试workerId边界值
        SnowflakeIdGenerator(0L, 0L) // 最小值
        SnowflakeIdGenerator(SnowflakeIdGenerator.MAX_WORKER_ID, 0L) // 最大值

        // 测试datacenterId边界值
        SnowflakeIdGenerator(0L, SnowflakeIdGenerator.MAX_DATACENTER_ID) // 最大值

        // 测试workerId超出范围
        assertThrows<IllegalArgumentException> {
            SnowflakeIdGenerator(-1L, 0L)
        }

        assertThrows<IllegalArgumentException> {
            SnowflakeIdGenerator(SnowflakeIdGenerator.MAX_WORKER_ID + 1, 0L)
        }

        // 测试datacenterId超出范围
        assertThrows<IllegalArgumentException> {
            SnowflakeIdGenerator(0L, -1L)
        }

        assertThrows<IllegalArgumentException> {
            SnowflakeIdGenerator(0L, SnowflakeIdGenerator.MAX_DATACENTER_ID + 1)
        }
    }

    @Test
    @DisplayName("测试ID生成的唯一性")
    fun testIdUniqueness() {
        val generator = SnowflakeIdGenerator(1L, 1L)
        val ids = mutableSetOf<Long>()

        // 生成1000个ID，验证唯一性
        repeat(1000) {
            val id = generator.nextId()
            assertTrue(ids.add(id), "生成的ID应该是唯一的: $id")
        }

        assertEquals(1000, ids.size, "应该生成1000个不同的ID")
    }

    @Test
    @DisplayName("测试同一毫秒内序列号递增")
    fun testSequenceIncrement() {
        val generator = SnowflakeIdGenerator(1L, 1L)

        // 快速生成多个ID（在同一毫秒内）
        val ids = mutableListOf<Long>()
        repeat(100) {
            ids.add(generator.nextId())
        }

        // 验证ID是递增的
        for (i in 1 until ids.size) {
            assertTrue(ids[i] > ids[i - 1], "ID应该是递增的")
        }
    }

    @Test
    @DisplayName("测试不同工作节点生成不同ID")
    fun testDifferentWorkerIds() {
        val generator1 = SnowflakeIdGenerator(1L, 1L)
        val generator2 = SnowflakeIdGenerator(2L, 1L)

        val id1 = generator1.nextId()
        val id2 = generator2.nextId()

        assertNotEquals(id1, id2, "不同工作节点应该生成不同的ID")
    }

    @Test
    @DisplayName("测试不同数据中心生成不同ID")
    fun testDifferentDatacenterIds() {
        val generator1 = SnowflakeIdGenerator(1L, 1L)
        val generator2 = SnowflakeIdGenerator(1L, 2L)

        val id1 = generator1.nextId()
        val id2 = generator2.nextId()

        assertNotEquals(id1, id2, "不同数据中心应该生成不同的ID")
    }

    @Test
    @DisplayName("测试ID格式和位数分配")
    fun testIdFormat() {
        val generator = SnowflakeIdGenerator(31L, 31L) // 使用最大值
        val id = generator.nextId()

        // 验证ID是正数
        assertTrue(id > 0, "生成的ID应该是正数")

        // 提取各个部分
        val sequence = id and SnowflakeIdGenerator.MAX_SEQUENCE
        val workerId = (id shr SnowflakeIdGenerator.SEQUENCE_BITS.toInt()) and SnowflakeIdGenerator.MAX_WORKER_ID
        val datacenterId =
            (id shr (SnowflakeIdGenerator.SEQUENCE_BITS + SnowflakeIdGenerator.WORKER_ID_BITS).toInt()) and SnowflakeIdGenerator.MAX_DATACENTER_ID
        val timestamp =
            id shr (SnowflakeIdGenerator.SEQUENCE_BITS + SnowflakeIdGenerator.WORKER_ID_BITS + SnowflakeIdGenerator.DATACENTER_ID_BITS).toInt()

        // 验证workerId和datacenterId
        assertEquals(31L, workerId, "提取的workerId应该匹配")
        assertEquals(31L, datacenterId, "提取的datacenterId应该匹配")
        assertTrue(timestamp > 0, "时间戳应该大于0")
        assertTrue(sequence >= 0, "序列号应该大于等于0")
    }

    @Test
    @DisplayName("测试序列号溢出处理")
    fun testSequenceOverflow() {
        val generator = SnowflakeIdGenerator(1L, 1L)

        // 通过反射设置序列号接近最大值来测试溢出
        val generatorClass = generator::class.java
        val sequenceField = generatorClass.getDeclaredField("sequence")
        sequenceField.isAccessible = true
        sequenceField.setLong(generator, SnowflakeIdGenerator.MAX_SEQUENCE - 1)

        val lastTimestampField = generatorClass.getDeclaredField("lastTimestamp")
        lastTimestampField.isAccessible = true
        lastTimestampField.setLong(generator, System.currentTimeMillis())

        // 生成几个ID来触发序列号溢出
        val id1 = generator.nextId()
        val id2 = generator.nextId()
        val id3 = generator.nextId()

        // 验证ID仍然是唯一且递增的
        assertTrue(id2 > id1, "序列号溢出后ID应该仍然递增")
        assertTrue(id3 > id2, "序列号溢出后ID应该仍然递增")
    }

    @Test
    @DisplayName("测试常量值正确性")
    fun testConstants() {
        assertEquals(5L, SnowflakeIdGenerator.WORKER_ID_BITS, "workerIdBits应该是5")
        assertEquals(5L, SnowflakeIdGenerator.DATACENTER_ID_BITS, "datacenterIdBits应该是5")
        assertEquals(12L, SnowflakeIdGenerator.SEQUENCE_BITS, "sequenceBits应该是12")
        assertEquals(31L, SnowflakeIdGenerator.MAX_WORKER_ID, "maxWorkerId应该是31")
        assertEquals(31L, SnowflakeIdGenerator.MAX_DATACENTER_ID, "maxDatacenterId应该是31")
        assertEquals(4095L, SnowflakeIdGenerator.MAX_SEQUENCE, "maxSequence应该是4095")
        assertEquals(1706716800000L, SnowflakeIdGenerator.EPOCH, "epoch应该是2024-01-01 00:00:00 UTC+8")
    }

    @Test
    @DisplayName("测试时间戳单调递增")
    fun testTimestampMonotonic() {
        val generator = SnowflakeIdGenerator(1L, 1L)

        val id1 = generator.nextId()
        Thread.sleep(2) // 确保时间戳变化
        val id2 = generator.nextId()

        // 提取时间戳部分
        val timestamp1 =
            id1 shr (SnowflakeIdGenerator.SEQUENCE_BITS + SnowflakeIdGenerator.WORKER_ID_BITS + SnowflakeIdGenerator.DATACENTER_ID_BITS).toInt()
        val timestamp2 =
            id2 shr (SnowflakeIdGenerator.SEQUENCE_BITS + SnowflakeIdGenerator.WORKER_ID_BITS + SnowflakeIdGenerator.DATACENTER_ID_BITS).toInt()

        assertTrue(timestamp2 >= timestamp1, "时间戳应该单调递增")
    }

    @Test
    @DisplayName("测试高并发场景下ID唯一性")
    fun testConcurrentIdGeneration() {
        val generator = SnowflakeIdGenerator(1L, 1L)
        val ids = mutableSetOf<Long>()
        val threads = mutableListOf<Thread>()

        // 创建10个线程，每个线程生成100个ID
        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(100) {
                    synchronized(ids) {
                        val id = generator.nextId()
                        assertTrue(ids.add(id), "并发生成的ID应该是唯一的: $id (thread: $threadIndex)")
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        // 等待所有线程完成
        threads.forEach { it.join() }

        assertEquals(1000, ids.size, "应该生成1000个不同的ID")
    }
}
