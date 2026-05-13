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
            fileId = UUID(0L, 0L),
            variants = emptyList(),
        ),
    )
    val outboundEvent = ContentPublishedIntegrationEvent(contentId = 1L)
    val subscriber = MediaProcessingCallbackIntegrationEventSubscriber()
}
