package com.only4.cap4k.ddd.endpoint.rpc

import com.only4.cap4k.contract.EndpointRequest
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

class EndpointRpcProviderBinding<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any>(
    val serviceId: String,
    val operationName: String,
    val requestType: KClass<REQUEST>,
    val responseType: KClass<RESPONSE>,
) {
    init {
        EndpointRpcProviderBindingValidator.validate(this)
    }
}

object EndpointRpcProviderBindingValidator {
    fun validateAll(serviceId: String, bindings: Collection<EndpointRpcProviderBinding<*, *>>) {
        require(serviceId.isNotBlank()) { "Endpoint RPC provider serviceId must not be blank" }
        require(bindings.isNotEmpty()) { "Endpoint RPC provider must declare at least one binding" }
        bindings.forEach {
            validate(it)
            require(it.serviceId == serviceId) {
                "Endpoint RPC binding serviceId '${it.serviceId}' does not match provider serviceId '$serviceId'"
            }
        }
        bindings.groupBy { it.serviceId to it.operationName }.entries.firstOrNull { it.value.size > 1 }?.key?.let {
            throw IllegalArgumentException("Duplicate Endpoint RPC binding for serviceId='${it.first}', operationName='${it.second}'")
        }
    }

    fun validate(binding: EndpointRpcProviderBinding<*, *>) {
        require(binding.serviceId.isNotBlank()) { "Endpoint RPC serviceId must not be blank" }
        require(binding.operationName.isNotBlank()) { "Endpoint RPC operationName must not be blank" }
        val requestOwner = binding.requestType.java.enclosingClass
            ?: throw IllegalArgumentException(
                "Endpoint Request '${binding.requestType.qualifiedName}' must be nested in its generated operation object",
            )
        val responseOwner = binding.responseType.java.enclosingClass
            ?: throw IllegalArgumentException(
                "Endpoint Response '${binding.responseType.qualifiedName}' must be nested in its generated operation object",
            )
        require(requestOwner == responseOwner) { "Endpoint RPC Request and Response do not share an operation owner" }
        val operationField = runCatching { requestOwner.getField("OPERATION_NAME") }.getOrNull()
            ?: throw IllegalArgumentException(
                "Endpoint operation owner '${requestOwner.name}' must expose public OPERATION_NAME",
            )
        val declaredName = runCatching { operationField.get(null) }.getOrNull() as? String
            ?: throw IllegalArgumentException(
                "Endpoint operation owner '${requestOwner.name}' OPERATION_NAME must be a public static String",
            )
        require(binding.operationName == declaredName) {
            "Endpoint RPC operationName '${binding.operationName}' does not match OPERATION_NAME '$declaredName'"
        }
        val resultType = binding.requestType.java.genericInterfaces.asSequence()
            .filterIsInstance<ParameterizedType>()
            .firstOrNull { it.rawType == EndpointRequest::class.java }
            ?.actualTypeArguments
            ?.singleOrNull() as? Class<*>
        require(resultType == binding.responseType.java) {
            "Endpoint RPC Request generic response does not match registered Response"
        }
    }
}

class EndpointRpcProviderRegistry(
    private val serviceId: String,
    bindings: Collection<EndpointRpcProviderBinding<*, *>>,
) {
    private val bindingsByOperation = bindings.toList()
        .also { EndpointRpcProviderBindingValidator.validateAll(serviceId, it) }
        .associateBy(EndpointRpcProviderBinding<*, *>::operationName)

    fun resolve(serviceId: String, operationName: String): EndpointRpcProviderBinding<*, *> {
        if (serviceId != this.serviceId) {
            throw EndpointRpcProtocolException(EndpointRpcFailureCategory.UNKNOWN_SERVICE, "unknown_service")
        }
        return bindingsByOperation[operationName]
            ?: throw EndpointRpcProtocolException(EndpointRpcFailureCategory.UNKNOWN_OPERATION, "unknown_operation")
    }
}
