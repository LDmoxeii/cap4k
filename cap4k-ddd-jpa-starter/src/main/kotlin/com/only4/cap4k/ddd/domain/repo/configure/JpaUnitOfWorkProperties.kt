package com.only4.cap4k.ddd.domain.repo.configure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application.jpa-uow")
class JpaUnitOfWorkProperties(
    var retrieveCountWarnThreshold: Int = 3000,
    var maxFrontierRounds: Int = 64,
    var maxSynchronousEvents: Int = 10_000,
    var maxNestedCommands: Int = 256,
    var maxProviderFlushes: Int = 64,
)
