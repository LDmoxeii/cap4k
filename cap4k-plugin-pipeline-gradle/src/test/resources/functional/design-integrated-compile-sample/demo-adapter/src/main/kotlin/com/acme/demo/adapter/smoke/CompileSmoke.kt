package com.acme.demo.adapter.smoke

import com.acme.demo.adapter.application.capabilities.authorize.IssueTokenHandler
import com.acme.demo.contract.endpoints.order.SubmitOrderEndpoint
import com.acme.demo.adapter.application.queries.order.read.FindOrderQryHandler

@Suppress("unused")
internal fun ensureGeneratedAdapterTypesArePresent(
    queryHandler: FindOrderQryHandler? = null,
    capabilityHandler: IssueTokenHandler? = null,
    endpointRequest: SubmitOrderEndpoint.Request? = null,
): List<Any?> = listOf(queryHandler, capabilityHandler, endpointRequest)
