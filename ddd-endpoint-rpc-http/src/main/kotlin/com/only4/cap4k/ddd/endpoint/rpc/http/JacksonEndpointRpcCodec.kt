package com.only4.cap4k.ddd.endpoint.rpc.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcCodec
import kotlin.reflect.KClass

class JacksonEndpointRpcCodec(private val objectMapper: ObjectMapper) : EndpointRpcCodec {
    override val identity: String = "json"
    override fun <T : Any> encode(value: T, type: KClass<T>): String = objectMapper.writeValueAsString(value)
    override fun <T : Any> decode(payload: String, type: KClass<T>): T = objectMapper.readValue(payload, type.java)
}
