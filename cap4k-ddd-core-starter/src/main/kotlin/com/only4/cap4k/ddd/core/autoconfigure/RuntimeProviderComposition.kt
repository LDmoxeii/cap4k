package com.only4.cap4k.ddd.core.autoconfigure

import org.springframework.beans.factory.ListableBeanFactory

/**
 * Resolves runtime provider slots by Spring bean identity without silently
 * treating an ambiguous optional dependency as absent.
 */
object RuntimeProviderComposition {
    fun <T : Any> required(
        beanFactory: ListableBeanFactory,
        type: Class<T>,
        slot: String,
    ): T {
        val beanNames = beanNames(beanFactory, type)
        check(beanNames.size == 1) {
            "cap4k provider '$slot' requires exactly one implementation, found $beanNames"
        }
        return beanFactory.getBean(beanNames.single(), type)
    }

    fun <T : Any> optional(
        beanFactory: ListableBeanFactory,
        type: Class<T>,
        slot: String,
    ): T? {
        val beanNames = beanNames(beanFactory, type)
        check(beanNames.size <= 1) {
            "cap4k provider '$slot' allows at most one implementation, found $beanNames"
        }
        return beanNames.singleOrNull()?.let { beanFactory.getBean(it, type) }
    }

    private fun <T : Any> beanNames(
        beanFactory: ListableBeanFactory,
        type: Class<T>,
    ): List<String> = beanFactory
        .getBeanNamesForType(type, true, true)
        .sorted()
}
