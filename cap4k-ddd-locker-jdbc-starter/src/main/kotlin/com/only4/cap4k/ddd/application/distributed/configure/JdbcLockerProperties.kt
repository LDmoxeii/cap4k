package com.only4.cap4k.ddd.application.distributed.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.distributed.locker.jdbc")
class JdbcLockerProperties(
    var table: String = "__locker",
    var fieldName: String = "name",
    var fieldPwd: String = "pwd",
    var fieldLockAt: String = "lock_at",
    var fieldUnlockAt: String = "unlock_at",
)
