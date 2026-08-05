package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.share.json.RuntimeExecutionContextJson

internal object IntegrationEventExecutionContextEnvelope {
    fun encode(elements: Collection<EncodedExecutionContextElement>): String =
        RuntimeExecutionContextJson.encode(elements, "Integration Event ExecutionContext envelope")

    fun decode(rawEnvelope: Any?): List<EncodedExecutionContextElement> =
        RuntimeExecutionContextJson.decode(rawEnvelope, "HTTP Integration Event ExecutionContext envelope")
}
