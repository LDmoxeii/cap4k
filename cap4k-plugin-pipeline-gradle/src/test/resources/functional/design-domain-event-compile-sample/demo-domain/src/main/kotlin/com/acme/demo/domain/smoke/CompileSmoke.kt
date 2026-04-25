package com.acme.demo.domain.smoke

import com.acme.demo.domain.aggregates.order.events.OrderCreatedDomainEvent

object CompileSmoke {
    val eventType = OrderCreatedDomainEvent::class
}
