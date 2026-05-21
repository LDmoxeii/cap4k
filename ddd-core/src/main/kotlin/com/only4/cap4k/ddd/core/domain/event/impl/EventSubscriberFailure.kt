package com.only4.cap4k.ddd.core.domain.event.impl

data class EventSubscriberFailure(
    val subscriberClass: Class<*>,
    val cause: Throwable,
)
