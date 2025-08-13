package com.only4.cap4k.ddd

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * 测试用的Spring Boot应用程序
 *
 * @author LD_moxeii
 * @date 2025/08/09
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.only4.cap4k.ddd"])
@EnableJpaRepositories(
    basePackages = [
        "com.only4.cap4k.ddd.domain.event.persistence",
        "com.only4.cap4k.ddd.application.request.persistence",
        "com.only4.cap4k.ddd.application.saga.persistence"
    ]
)
@EntityScan(
    basePackages = [
        "com.only4.cap4k.ddd.domain.event.persistence",
        "com.only4.cap4k.ddd.application.request.persistence",
        "com.only4.cap4k.ddd.application.saga.persistence"
    ]
)
class TestApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(TestApplication::class.java, *args)
        }
    }
}
