package com.only4.cap4k.ddd.core.application

/**
 * Internal runtime boundary used by the Command supervisor.
 *
 * This type is bytecode-public only because the JPA runtime lives in another
 * module. It is not an application-facing Unit of Work API and deliberately
 * exposes no static locator or explicit flush operation.
 */
interface CommandUnitOfWorkCoordinator {
    val active: Boolean

    fun <RESULT> execute(block: () -> RESULT): RESULT
}
