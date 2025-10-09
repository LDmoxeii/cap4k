package ${basePackage}

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * ${artifactId} 启动类
 *
 * @author cap4k-ddd-codegen
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(basePackages = ["${basePackage}.adapter.domain.repositories"])
@EntityScan(basePackages = ["${basePackage}.domain.aggregates"])
class StartApplication

fun main(args: Array<String>) {
    runApplication<StartApplication>(*args)
}
