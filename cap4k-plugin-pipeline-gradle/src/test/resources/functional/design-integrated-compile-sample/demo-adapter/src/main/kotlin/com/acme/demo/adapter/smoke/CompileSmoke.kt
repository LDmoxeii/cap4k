package com.acme.demo.adapter.smoke

import com.acme.demo.adapter.application.capabilities.authorize.IssueTokenHandler
import com.acme.demo.adapter.portal.api.payload.order.SubmitOrderPayload
import com.acme.demo.adapter.application.queries.order.read.FindOrderQryHandler

@Suppress("unused")
internal fun ensureGeneratedAdapterTypesArePresent(
    queryHandler: FindOrderQryHandler? = null,
    capabilityHandler: IssueTokenHandler? = null,
    payloadRequest: SubmitOrderPayload.Request? = null,
): List<Any?> = listOf(queryHandler, capabilityHandler, payloadRequest)
