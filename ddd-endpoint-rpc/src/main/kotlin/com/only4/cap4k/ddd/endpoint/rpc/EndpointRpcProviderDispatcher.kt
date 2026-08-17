package com.only4.cap4k.ddd.endpoint.rpc

import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager

class EndpointRpcProviderDispatcher(
    private val registry: EndpointRpcProviderRegistry,
    private val codec: EndpointRpcCodec,
    private val contextCodecs: ExecutionContextCodecRegistry,
    private val scopeManager: ExecutionContextScopeManager,
) {
    fun dispatch(envelope: EndpointRpcRequestEnvelope): EndpointRpcResponseEnvelope {
        if (envelope.protocolVersion != ENDPOINT_RPC_PROTOCOL_VERSION) {
            return failure(EndpointRpcFailureCategory.PROTOCOL, "unsupported_version")
        }
        if (envelope.codec != codec.identity) {
            return failure(EndpointRpcFailureCategory.PROTOCOL, "unsupported_codec")
        }
        val binding = try {
            registry.resolve(envelope.serviceId, envelope.operationName)
        } catch (failure: EndpointRpcProtocolException) {
            return failure(failure.category, failure.code)
        }
        val snapshot = try {
            contextCodecs.decodeExternal(envelope.context, ExecutionContextBoundary.RPC)
        } catch (_: Exception) {
            return failure(EndpointRpcFailureCategory.CONTEXT, "invalid_context")
        }
        @Suppress("UNCHECKED_CAST")
        val typed = binding as EndpointRpcProviderBinding<EndpointRequest<Any>, Any>
        val request = try {
            codec.decode(envelope.payload, typed.requestType)
        } catch (_: Exception) {
            return failure(EndpointRpcFailureCategory.REQUEST_CODEC, "request_decode_failed")
        }
        return scopeManager.install(snapshot).use {
            val response = try {
                Mediator.endpoints.send(request)
            } catch (_: Exception) {
                return@use failure(EndpointRpcFailureCategory.REMOTE, "provider_invocation_failed")
            }
            try {
                EndpointRpcResponseEnvelope(
                    codec = codec.identity,
                    success = true,
                    payload = codec.encode(response, typed.responseType),
                )
            } catch (_: Exception) {
                failure(EndpointRpcFailureCategory.RESPONSE_CODEC, "response_encode_failed")
            }
        }
    }

    private fun failure(category: EndpointRpcFailureCategory, code: String) = EndpointRpcResponseEnvelope(
        codec = codec.identity,
        success = false,
        failure = EndpointRpcFailure(category, code),
    )
}
