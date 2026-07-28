package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import org.springframework.context.ApplicationContext

/**
 * 中介者配置
 *
 * @author LD_moxeii
 * @date 2025/07/22
 */
object MediatorSupport {
    private val iocSlot = CapabilitySlot<ApplicationContext>("ioc", "cap4k-ddd-core-starter")
    private val identifierSlot = CapabilitySlot<IdentifierGenerator>("identifiers", "cap4k-ddd-core-starter")

    val ioc: ApplicationContext
        get() = iocSlot.get()

    val identifiers: IdentifierGenerator
        get() = identifierSlot.get()

    fun configure(applicationContext: ApplicationContext) {
        iocSlot.configure(applicationContext)
    }

    fun configure(identifierGenerator: IdentifierGenerator) {
        identifierSlot.configure(identifierGenerator)
    }
}
