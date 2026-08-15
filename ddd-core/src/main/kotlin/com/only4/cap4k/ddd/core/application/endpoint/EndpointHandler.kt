package com.only4.cap4k.ddd.core.application.endpoint

import com.only4.cap4k.contract.EndpointRequest

/** Local provider or consumer-side implementation for one published endpoint request. */
fun interface EndpointHandler<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> {
    fun handle(request: REQUEST): RESPONSE
}
