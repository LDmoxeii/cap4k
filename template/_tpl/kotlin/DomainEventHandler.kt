package $

{ basePackage }.application.subscribers.domain

import ${basePackage}.domain.aggregates${package}.events.${DomainEvent}
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class $ {DomainEvent }Subscriber {

    @EventListener($ { DomainEvent }::class)
    fun on(event: $ {
        DomainEvent
    }) {

    }
}
