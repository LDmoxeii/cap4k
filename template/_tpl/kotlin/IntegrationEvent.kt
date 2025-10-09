package ${basePackage}.application.distributed.events${subPackage}

import com.only4.cap4k.ddd.core.application.event.annotation.AutoRelease
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent

@IntegrationEvent(value = $ {MQ_TOPIC}, subscriber = ${MQ_CONSUMER})
class UpdatedRoleInfoIntegrationEvent(

)
