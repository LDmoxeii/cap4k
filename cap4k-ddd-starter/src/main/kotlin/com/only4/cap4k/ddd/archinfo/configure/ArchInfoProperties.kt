package com.only4.cap4k.ddd.archinfo.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 架构信息配置类
 *
 * @author binking338
 * @date 2024/11/24
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.archinfo")
open class ArchInfoProperties(
    var enabled: Boolean = false,
    var basePackage: String = ""
)
