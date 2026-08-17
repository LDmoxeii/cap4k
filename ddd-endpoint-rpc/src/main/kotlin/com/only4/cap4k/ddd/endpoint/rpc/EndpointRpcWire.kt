package com.only4.cap4k.ddd.endpoint.rpc

import com.only4.cap4k.ddd.core.application.context.EncodedExecutionContextElement
import kotlin.reflect.KClass

const val ENDPOINT_RPC_PROTOCOL_VERSION: Int = 1

data class EndpointRpcRequestEnvelope(
    val protocolVersion: Int,
    val codec: String,
    val serviceId: String,
    val operationName: String,
    val payload: String,
    val context: List<EncodedExecutionContextElement> = emptyList(),
)

data class EndpointRpcResponseEnvelope(
    val protocolVersion: Int = ENDPOINT_RPC_PROTOCOL_VERSION,
    val codec: String,
    val success: Boolean,
    val payload: String? = null,
    val failure: EndpointRpcFailure? = null,
)

data class EndpointRpcFailure(
    val category: EndpointRpcFailureCategory,
    val code: String,
    val correlationId: String? = null,
)

enum class EndpointRpcFailureCategory {
    PROTOCOL,
    UNKNOWN_SERVICE,
    UNKNOWN_OPERATION,
    CONTEXT,
    REQUEST_CODEC,
    RESPONSE_CODEC,
    REMOTE,
    ROUTE,
    CONNECTION,
    TIMEOUT,
    TRANSPORT,
}

object EndpointRpcResponseEnvelopeValidator {
    private val providerFailureCategories = setOf(
        EndpointRpcFailureCategory.PROTOCOL,
        EndpointRpcFailureCategory.UNKNOWN_SERVICE,
        EndpointRpcFailureCategory.UNKNOWN_OPERATION,
        EndpointRpcFailureCategory.CONTEXT,
        EndpointRpcFailureCategory.REQUEST_CODEC,
        EndpointRpcFailureCategory.RESPONSE_CODEC,
        EndpointRpcFailureCategory.REMOTE,
    )

    fun validate(envelope: EndpointRpcResponseEnvelope) {
        require(envelope.codec.isNotBlank()) { "Endpoint RPC response codec must not be blank" }
        if (envelope.success) {
            require(envelope.payload != null && envelope.failure == null) {
                "Endpoint RPC success response must contain payload and no failure"
            }
        } else {
            val failure = requireNotNull(envelope.failure) {
                "Endpoint RPC failure response must contain failure"
            }
            require(envelope.payload == null) { "Endpoint RPC failure response must not contain payload" }
            require(failure.code.isNotBlank()) { "Endpoint RPC failure code must not be blank" }
            require(failure.category in providerFailureCategories) {
                "Endpoint RPC Provider returned a Consumer-local failure category"
            }
        }
    }
}

interface EndpointRpcCodec {
    val identity: String
    fun <T : Any> encode(value: T, type: KClass<T>): String
    fun <T : Any> decode(payload: String, type: KClass<T>): T
}

class EndpointRpcProtocolException(
    val category: EndpointRpcFailureCategory,
    val code: String,
) : RuntimeException("Endpoint RPC protocol failure: category=$category, code=$code")

class EndpointRemoteInvocationException(
    val category: EndpointRpcFailureCategory,
    val serviceId: String,
    val operationName: String,
    val status: Int? = null,
    val correlationId: String? = null,
) : RuntimeException(buildString {
    append("Endpoint RPC invocation failed: category=").append(category)
    append(", serviceId=").append(serviceId).append(", operationName=").append(operationName)
    status?.let { append(", status=").append(it) }
    correlationId?.let { append(", correlationId=").append(it) }
})
