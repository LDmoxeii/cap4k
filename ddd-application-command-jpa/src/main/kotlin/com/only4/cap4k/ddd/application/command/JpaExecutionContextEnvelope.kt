package com.only4.cap4k.ddd.application.command

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import com.only4.cap4k.ddd.core.share.json.RuntimeExecutionContextJson

internal object JpaExecutionContextEnvelope {
    fun encode(elements: Collection<EncodedExecutionContextElement>): String =
        RuntimeExecutionContextJson.encode(elements, "Reliable Command ExecutionContext envelope")

    fun decode(envelope: String?): List<EncodedExecutionContextElement> =
        RuntimeExecutionContextJson.decode(envelope, "reliable Command ExecutionContext envelope")
}
