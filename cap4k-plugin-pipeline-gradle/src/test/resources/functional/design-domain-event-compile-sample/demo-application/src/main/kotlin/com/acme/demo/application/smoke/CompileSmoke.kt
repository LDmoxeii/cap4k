package com.acme.demo.application.smoke

import com.acme.demo.application.order.events.OrderCreatedDomainEventSubscriber

object CompileSmoke {
    val subscriberType = OrderCreatedDomainEventSubscriber::class
}
