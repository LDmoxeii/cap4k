package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@AutoConfiguration(before = [HttpIntegrationEventAutoConfiguration::class])
@EnableJpaRepositories(basePackages = ["com.only4.cap4k.ddd.application.event.persistence"])
@EntityScan(basePackages = ["com.only4.cap4k.ddd.application.event.persistence"])
class HttpIntegrationEventJpaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(HttpIntegrationEventSubscriberRegister::class)
    fun jpaHttpIntegrationEventSubscriberRegister(
        repository: EventHttpSubscriberJpaRepository,
    ): JpaHttpIntegrationEventSubscriberRegister = JpaHttpIntegrationEventSubscriberRegister(repository)
}
