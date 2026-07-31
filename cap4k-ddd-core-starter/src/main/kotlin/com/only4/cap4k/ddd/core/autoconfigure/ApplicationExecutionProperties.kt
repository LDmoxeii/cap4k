package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.application.async.AsyncOverloadStrategy
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cap4k.ddd.application.execution")
class ApplicationExecutionProperties {
    var query: AsyncExecutor = AsyncExecutor(threadNamePrefix = "cap4k-query-")
    var capability: AsyncExecutor = AsyncExecutor(threadNamePrefix = "cap4k-capability-")

    class AsyncExecutor(
        var workers: Int = 4,
        var queueCapacity: Int = 256,
        var overloadStrategy: AsyncOverloadStrategy = AsyncOverloadStrategy.CALLER_RUNS,
        var threadNamePrefix: String = "cap4k-application-",
    )
}
