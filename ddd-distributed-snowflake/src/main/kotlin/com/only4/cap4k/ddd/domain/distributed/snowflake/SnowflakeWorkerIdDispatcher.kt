package com.only4.cap4k.ddd.domain.distributed.snowflake

/**
 * WorkerId调度器
 */
interface SnowflakeWorkerIdDispatcher {
    /**
     * 获取WorkerId占用
     *
     * @param workerId 指定workerId
     * @param datacenterId 指定datacenterId
     * @return 分配的workerId
     */
    fun acquire(workerId: Long?, datacenterId: Long?): Long

    /**
     * 释放WorkerId占用
     */
    fun release()

    /**
     * 心跳上报
     *
     * @return 心跳是否成功
     */
    fun pong(): Boolean

    /**
     * 心跳上报如果长期失联，失败累计到一定次数，提醒运维或相关人员，以便介入处理
     */
    fun remind()
}
