package com.only4.cap4k.ddd

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

/**
 * 简单的测试用Spring Boot应用程序
 * 只包含最基本的配置，用于测试Bean加载
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
class SimpleTestApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(SimpleTestApplication::class.java, *args)
        }
    }
}