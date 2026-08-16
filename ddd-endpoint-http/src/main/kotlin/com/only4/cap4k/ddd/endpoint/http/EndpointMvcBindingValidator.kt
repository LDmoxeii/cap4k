package com.only4.cap4k.ddd.endpoint.http

import com.only4.cap4k.contract.EndpointRequest
import org.springframework.http.HttpMethod
import java.lang.reflect.ParameterizedType

object EndpointMvcBindingValidator {
    private val supportedMethods = setOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)

    fun validateAll(bindings: Collection<EndpointMvcBinding<*, *>>) {
        bindings.forEach(::validate)
        bindings.groupBy { it.operationName }.filterValues { it.size > 1 }.keys.sorted().firstOrNull()?.let {
            throw IllegalArgumentException("Duplicate Endpoint HTTP binding for operation '$it'")
        }
        bindings.groupBy { "${it.method.name()} ${it.path}" }.filterValues { it.size > 1 }.keys.sorted().firstOrNull()?.let {
            throw IllegalArgumentException("Duplicate Endpoint HTTP route '$it'")
        }
    }

    fun validate(binding: EndpointMvcBinding<*, *>) {
        require(binding.operationName.isNotBlank()) { "Endpoint HTTP operationName must not be blank" }
        require(binding.method in supportedMethods) { "Unsupported Endpoint HTTP method '${binding.method.name()}'" }
        val requestOwner = binding.requestType.java.enclosingClass
            ?: throw IllegalArgumentException("Endpoint Request '${binding.requestType.qualifiedName}' must be nested in its generated operation object")
        val responseOwner = binding.responseType.java.enclosingClass
            ?: throw IllegalArgumentException("Endpoint Response '${binding.responseType.qualifiedName}' must be nested in its generated operation object")
        require(requestOwner == responseOwner) {
            "Endpoint Request '${binding.requestType.qualifiedName}' and Response '${binding.responseType.qualifiedName}' do not share an operation owner"
        }
        val operationField = runCatching { requestOwner.getField("OPERATION_NAME") }.getOrNull()
            ?: throw IllegalArgumentException("Endpoint operation owner '${requestOwner.name}' must expose public OPERATION_NAME")
        val declaredName = operationField.get(null) as? String
            ?: throw IllegalArgumentException("Endpoint operation owner '${requestOwner.name}' OPERATION_NAME must be a String")
        require(binding.operationName == declaredName) {
            "Endpoint HTTP operationName '${binding.operationName}' does not match ${requestOwner.name}.OPERATION_NAME '$declaredName'"
        }
        val resultType = endpointResultType(binding.requestType.java)
            ?: throw IllegalArgumentException("Endpoint Request '${binding.requestType.qualifiedName}' must implement EndpointRequest<Response>")
        require(resultType == binding.responseType.java) {
            "Endpoint Request '${binding.requestType.qualifiedName}' declares EndpointRequest<${resultType.typeName}> but binding response is '${binding.responseType.qualifiedName}'"
        }
    }

    internal fun normalizeMethod(method: HttpMethod): HttpMethod {
        require(method in supportedMethods) { "Unsupported Endpoint HTTP method '${method.name()}'" }
        return HttpMethod.valueOf(method.name().uppercase())
    }

    internal fun normalizePath(path: String): String {
        require(path.isNotBlank()) { "Endpoint HTTP path must not be blank" }
        require(path.startsWith('/')) { "Endpoint HTTP path must be absolute: '$path'" }
        require(!path.contains('?') && !path.contains('#')) { "Endpoint HTTP path must not contain query or fragment: '$path'" }
        val normalized = path.replace(Regex("/{2,}"), "/").let { if (it.length > 1) it.trimEnd('/') else it }
        require(normalized.split('/').none { it == "." || it == ".." }) { "Endpoint HTTP path must not contain dot segments: '$path'" }
        return normalized
    }

    private fun endpointResultType(type: Class<*>): Class<*>? = type.genericInterfaces.asSequence()
        .filterIsInstance<ParameterizedType>()
        .firstOrNull { it.rawType == EndpointRequest::class.java }
        ?.actualTypeArguments?.singleOrNull() as? Class<*>
}
