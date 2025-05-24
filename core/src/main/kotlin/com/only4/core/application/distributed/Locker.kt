package com.only4.core.application.distributed

import java.time.Duration

/**
 * 锁接口
 *
 * @author binking338
 * @date 2023/8/17
 */
interface Locker {
    /**
     * 获取锁
     *
     * @param key
     * @param pwd
     * @param expireDuration
     * @return
     */
    fun acquire(key: String, pwd: String, expireDuration: Duration): Boolean

    /**
     * 释放锁
     *
     * @param key
     * @param pwd
     * @return
     */
    fun release(key: String, pwd: String): Boolean
}
