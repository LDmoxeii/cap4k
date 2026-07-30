package com.only4.cap4k.ddd.core.domain.event.impl

internal enum class EventRuntimeScopeType {
    UNIT_OF_WORK,
    APPLICATION_INVOCATION,
    DOMAIN_DISPATCH,
    AMBIENT
}
