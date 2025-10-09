package ${basePackage}.application.subscribers.integration

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class ${IntegrationEvent}Subscriber {

    @EventListener(${IntegrationEvent}::class)
    fun on(event: ${IntegrationEvent}) {

    }
}
