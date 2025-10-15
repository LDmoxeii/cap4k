package com.only4.cap4k.ddd.archinfo.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 架构信息配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.arch-info")
class ArchInfoProperties(
    var enabled: Boolean = false,
    var basePackage: String = ""
)
