package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.share.json.RuntimeExecutionContextJson

internal object JpaExecutionContextEnvelope {
    fun encode(elements: Collection<EncodedExecutionContextElement>): String =
        RuntimeExecutionContextJson.encode(elements, "Reliable Event ExecutionContext envelope")

    fun decode(envelope: String?): List<EncodedExecutionContextElement> =
        RuntimeExecutionContextJson.decode(envelope, "reliable Event ExecutionContext envelope")
}
