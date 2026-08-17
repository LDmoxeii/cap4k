package com.only4.cap4k.ddd.endpoint.rpc

import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import kotlin.reflect.KClass

interface EndpointTransportInvoker {
    fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> invoke(
        serviceId: String,
        operationName: String,
        request: REQUEST,
        requestType: KClass<REQUEST>,
        responseType: KClass<RESPONSE>,
    ): RESPONSE
}

class RemoteEndpointHandler<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any>(
    private val serviceId: String,
    private val operationName: String,
    private val requestType: KClass<REQUEST>,
    private val responseType: KClass<RESPONSE>,
    private val invoker: EndpointTransportInvoker,
) : EndpointHandler<REQUEST, RESPONSE> {
    override fun handle(request: REQUEST): RESPONSE = invoker.invoke(serviceId, operationName, request, requestType, responseType)
}
