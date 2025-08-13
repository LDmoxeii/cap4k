package com.only4.cap4k.ddd.archinfo

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.archinfo.configure.ArchInfoProperties
import com.only4.cap4k.ddd.core.archinfo.ArchInfoManager
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_NAME
import com.only4.cap4k.ddd.core.share.Constants.CONFIG_KEY_4_SVC_VERSION
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.HttpRequestHandler
import java.nio.charset.StandardCharsets

/**
 * 架构信息自动配置
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
class ArchInfoAutoConfiguration {

    companion object {
        private val log = LoggerFactory.getLogger(ArchInfoAutoConfiguration::class.java)
    }

    @Bean
    fun archInfoManager(
        @Value(CONFIG_KEY_4_SVC_NAME) name: String,
        @Value(CONFIG_KEY_4_SVC_VERSION) version: String,
        archInfoProperties: ArchInfoProperties,
    ): ArchInfoManager = ArchInfoManager(name, version, archInfoProperties.basePackage)


    @ConditionalOnWebApplication
    @ConditionalOnProperty(name = ["cap4k.ddd.archinfo.enabled"], havingValue = "true")
    @Bean(name = ["/cap4k/archinfo"])
    fun archInfoRequestHandler(
        archInfoManager: ArchInfoManager,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String
    ): HttpRequestHandler = HttpRequestHandler { req, res ->
        val archInfo = archInfoManager.getArchInfo()
        res.characterEncoding = StandardCharsets.UTF_8.name()
        res.contentType = "application/json; charset=utf-8"
        res.writer.apply {
            println(JSON.toJSONString(archInfo))
            flush()
            close()
        }
    }.apply { log.info("ArchInfo URL: http://localhost:$serverPort$serverServletContentPath/cap4k/archinfo") }

}
