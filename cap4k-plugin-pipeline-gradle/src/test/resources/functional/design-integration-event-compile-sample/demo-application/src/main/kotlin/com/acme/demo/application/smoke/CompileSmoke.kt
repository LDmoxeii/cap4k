package com.acme.demo.application.smoke

import com.acme.demo.application.subscribers.integration.inbound.media.processing.MediaProcessingCallbackIntegrationEvent
import com.acme.demo.application.subscribers.integration.MediaProcessingCallbackIntegrationEventSubscriber
import com.acme.demo.application.subscribers.integration.outbound.content.ContentPublishedIntegrationEvent
import java.time.LocalDateTime
import java.util.UUID

class CompileSmoke {
    val inboundEvent = MediaProcessingCallbackIntegrationEvent(
        externalTaskId = "task-1",
        completedAt = LocalDateTime.MIN,
        file = MediaProcessingCallbackIntegrationEvent.FileInfo(
            fileId = UUID.fromString("01890f7e-9b2a-7cc2-9f4a-1f6c4c7a0001"),
            variants = emptyList(),
        ),
    )
    val outboundEvent = ContentPublishedIntegrationEvent(contentId = 1L)
    val subscriber = MediaProcessingCallbackIntegrationEventSubscriber()
}
