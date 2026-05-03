package com.acme.demo.application.smoke

import com.acme.demo.application.subscribers.domain.order.OrderCreatedDomainEventSubscriber

object CompileSmoke {
    val subscriberType = OrderCreatedDomainEventSubscriber::class
}
