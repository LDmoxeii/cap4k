package com.only4.cap4k.ddd.application.distributed.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * JdbcLocker配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConfigurationProperties("cap4j.ddd.distributed.locker.jdbc")
class JdbcLockerProperties(
    /**
     * 锁表名
     */
    var table: String = "__locker",

    /**
     * 锁名称字段名
     */
    var fieldName: String = "name",

    /**
     * 锁密码字段名
     */
    var fieldPwd: String = "pwd",

    /**
     * 锁获取时间字段名
     */
    var fieldLockAt: String = "lock_at",

    /**
     * 锁释放时间字段名
     */
    var fieldUnlockAt: String = "unlock_at"
)
