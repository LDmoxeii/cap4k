package com.acme.demo.adapter.smoke

import com.acme.demo.adapter.application.distributed.clients.authorize.IssueTokenCliHandler
import com.acme.demo.adapter.portal.api.payload.order.SubmitOrderPayload
import com.acme.demo.adapter.queries.order.read.FindOrderQryHandler

@Suppress("unused")
internal fun ensureGeneratedAdapterTypesArePresent(
    queryHandler: FindOrderQryHandler? = null,
    clientHandler: IssueTokenCliHandler? = null,
    payloadRequest: SubmitOrderPayload.Request? = null,
): List<Any?> = listOf(queryHandler, clientHandler, payloadRequest)
