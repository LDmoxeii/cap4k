package com.only4.cap4k.ddd.domain.distributed.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.distributed.id-generator.snowflake")
class SnowflakeProperties(
    var table: String = "`__worker_id`",
    var fieldDatacenterId: String = "`datacenter_id`",
    var fieldWorkerId: String = "`worker_id`",
    var fieldDispatchTo: String = "`dispatch_to`",
    var fieldDispatchAt: String = "`dispatch_at`",
    var fieldExpireAt: String = "`expire_at`",
    var workerId: Long? = null,
    var datacenterId: Long? = null,
    var expireMinutes: Int = 10,
    var localHostIdentify: String = "",
    var maxPongContinuousErrorCount: Int = 5,
)
