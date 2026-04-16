package com.acme.demo.domain.smoke

import com.acme.demo.domain.order.events.OrderCreatedDomainEvent

object CompileSmoke {
    val eventType = OrderCreatedDomainEvent::class
}
