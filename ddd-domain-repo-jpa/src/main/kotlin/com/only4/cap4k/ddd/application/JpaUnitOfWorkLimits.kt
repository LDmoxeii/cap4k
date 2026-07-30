package com.only4.cap4k.ddd.application

data class JpaUnitOfWorkLimits(
    val maxFrontierRounds: Int = 64,
    val maxSynchronousEvents: Int = 10_000,
    val maxNestedCommands: Int = 256,
    val maxProviderFlushes: Int = 64,
) {
    init {
        require(maxFrontierRounds >= 0)
        require(maxSynchronousEvents >= 0)
        require(maxNestedCommands >= 0)
        require(maxProviderFlushes >= 0)
    }
}
