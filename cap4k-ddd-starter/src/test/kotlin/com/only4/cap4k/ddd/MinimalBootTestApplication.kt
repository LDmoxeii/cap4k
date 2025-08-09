package com.only4.cap4k.ddd

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * 最小化的Spring Boot测试应用程序
 * 不包含JPA和复杂的自动配置，用于基础测试
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootApplication
class MinimalBootTestApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(MinimalBootTestApplication::class.java, *args)
        }
    }
}