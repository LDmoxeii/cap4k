package com.only4.cap4k.ddd.domain.distributed.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Snowflake配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.distributed.idgenerator.snowflake")
open class SnowflakeProperties(
    var enable: Boolean = true,

    var table: String = "`__worker_id`",
    var fieldDatacenterId: String = "`datacenter_id`",
    var fieldWorkerId: String = "`worker_id`",
    var fieldDispatchTo: String = "`dispatch_to`",
    var fieldDispatchAt: String = "`dispatch_at`",
    var fieldExpireAt: String = "`expire_at`",

    var workerId: Long? = null,
    var datacenterId: Long? = null,
    var expireMinutes: Int = 10,
    /**
     * 本地主机标识
     */
    var localHostIdentify: String = "",
    /**
     * 最大连续错误次数
     */
    var maxPongContinuousErrorCount: Int = 5
)
