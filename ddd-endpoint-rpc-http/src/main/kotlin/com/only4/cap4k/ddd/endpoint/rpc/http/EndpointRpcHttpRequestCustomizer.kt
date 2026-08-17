package com.only4.cap4k.ddd.endpoint.rpc.http

fun interface EndpointRpcHttpRequestCustomizer {
    fun customize(serviceId: String, operationName: String, headers: MutableMap<String, String>)
    companion object { @JvmField val NONE = EndpointRpcHttpRequestCustomizer { _, _, _ -> } }
}
