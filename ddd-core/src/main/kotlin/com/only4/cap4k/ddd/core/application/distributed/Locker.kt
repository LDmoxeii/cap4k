package com.only4.cap4k.ddd.core.application.distributed

import java.time.Duration


/**
 * 分布式锁接口
 * 用于实现分布式环境下的锁机制，支持锁的获取和释放操作
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface Locker {
    /**
     * 获取锁
     * 尝试获取指定key的锁，如果获取成功则返回true，否则返回false
     *
     * @param key 锁的唯一标识
     * @param pwd 锁的密码，用于验证锁的所有权
     * @param expireDuration 锁的过期时间
     * @return 是否成功获取锁
     */
    fun acquire(key: String, pwd: String, expireDuration: Duration): Boolean

    /**
     * 释放锁
     * 释放指定key的锁，只有持有正确密码的调用者才能释放锁
     *
     * @param key 锁的唯一标识
     * @param pwd 锁的密码，用于验证锁的所有权
     * @return 是否成功释放锁
     */
    fun release(key: String, pwd: String): Boolean
}
