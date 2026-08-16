package com.only4.cap4k.ddd.endpoint.http

import com.only4.cap4k.contract.EndpointRequest
import org.springframework.core.convert.ConversionService
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerRequest
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Immutable, typed provider-side Spring MVC binding for one published Endpoint operation. */
class EndpointMvcBinding<REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> private constructor(
    val operationName: String,
    val requestType: KClass<REQUEST>,
    val responseType: KClass<RESPONSE>,
    val method: HttpMethod,
    val path: String,
    val requestMapper: EndpointMvcRequestMapper<REQUEST>,
    val responsePolicy: EndpointMvcResponsePolicy<RESPONSE>,
) {
    companion object {
        fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> json(
            operationName: String,
            requestType: KClass<REQUEST>,
            responseType: KClass<RESPONSE>,
            method: HttpMethod,
            path: String,
            responsePolicy: EndpointMvcResponsePolicy<RESPONSE> = EndpointMvcResponsePolicy.response(),
        ): EndpointMvcBinding<REQUEST, RESPONSE> = of(
            operationName, requestType, responseType, method, path,
            EndpointMvcRequestMapper { request -> request.body(requestType) }, responsePolicy,
        )

        fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> special(
            operationName: String,
            requestType: KClass<REQUEST>,
            responseType: KClass<RESPONSE>,
            method: HttpMethod,
            path: String,
            requestMapper: EndpointMvcRequestMapper<REQUEST>,
            responsePolicy: EndpointMvcResponsePolicy<RESPONSE> = EndpointMvcResponsePolicy.response(),
        ): EndpointMvcBinding<REQUEST, RESPONSE> = of(
            operationName, requestType, responseType, method, path, requestMapper, responsePolicy,
        )

        private fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> of(
            operationName: String,
            requestType: KClass<REQUEST>,
            responseType: KClass<RESPONSE>,
            method: HttpMethod,
            path: String,
            requestMapper: EndpointMvcRequestMapper<REQUEST>,
            responsePolicy: EndpointMvcResponsePolicy<RESPONSE>,
        ) = EndpointMvcBinding(
            operationName = operationName,
            requestType = requestType,
            responseType = responseType,
            method = EndpointMvcBindingValidator.normalizeMethod(method),
            path = EndpointMvcBindingValidator.normalizePath(path),
            requestMapper = requestMapper,
            responsePolicy = responsePolicy,
        ).also(EndpointMvcBindingValidator::validate)
    }
}

fun interface EndpointMvcRequestMapper<REQUEST : Any> {
    fun map(request: EndpointMvcRequest): REQUEST
}

class EndpointMvcRequest(
    internal val delegate: ServerRequest,
    private val conversionService: ConversionService,
) {
    fun <T : Any> body(type: KClass<T>): T = binding("body") { delegate.body(type.java) }
    fun <T : Any> path(name: String, type: KClass<T>): T = required("path", name, delegate.pathVariable(name), type)
    fun <T : Any> query(name: String, type: KClass<T>): T =
        required("query", name, delegate.param(name).orElse(null), type)
    fun <T : Any> header(name: String, type: KClass<T>): T =
        required("header", name, delegate.headers().firstHeader(name), type)
    fun path(name: String): String = path(name, String::class)
    fun query(name: String): String = query(name, String::class)
    fun header(name: String): String = header(name, String::class)

    private fun <T : Any> required(source: String, name: String, value: String?, type: KClass<T>): T {
        if (value == null) throw EndpointMvcBindingException("Missing required $source '$name'")
        return binding("Cannot convert $source '$name' to ${type.qualifiedName}") {
            if (type == String::class) value as T
            else conversionService.convert(value, type.java)
                ?: throw EndpointMvcBindingException("Cannot convert $source '$name' to ${type.qualifiedName}")
        }
    }

    private inline fun <T> binding(label: String, block: () -> T): T = try {
        block()
    } catch (failure: EndpointMvcBindingException) {
        throw failure
    } catch (failure: Exception) {
        throw EndpointMvcBindingException(label, failure)
    }
}

class EndpointMvcBindingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

enum class EndpointMvcResponseBody { RESPONSE, NONE }

class EndpointMvcResponseHeader<RESPONSE : Any> internal constructor(
    val name: String,
    private val value: (RESPONSE) -> String,
) {
    fun resolve(response: RESPONSE): String = value(response)
    companion object {
        fun <RESPONSE : Any> fixed(name: String, value: String) = EndpointMvcResponseHeader<RESPONSE>(name) { value }
        fun <RESPONSE : Any, VALUE> property(name: String, property: KProperty1<RESPONSE, VALUE>) =
            EndpointMvcResponseHeader<RESPONSE>(name) { response -> property.get(response)?.toString() ?: "" }
    }
}

class EndpointMvcResponsePolicy<RESPONSE : Any> private constructor(
    val status: Int,
    val body: EndpointMvcResponseBody,
    val headers: List<EndpointMvcResponseHeader<RESPONSE>>,
    val contentType: MediaType?,
) {
    init { require(status in 100..599) { "Endpoint MVC response status must be between 100 and 599: $status" } }

    companion object {
        fun <RESPONSE : Any> response(
            status: Int = 200,
            headers: List<EndpointMvcResponseHeader<RESPONSE>> = emptyList(),
            contentType: MediaType? = null,
        ) = EndpointMvcResponsePolicy(status, EndpointMvcResponseBody.RESPONSE, headers.toList(), contentType)

        fun <RESPONSE : Any> none(
            status: Int = 200,
            headers: List<EndpointMvcResponseHeader<RESPONSE>> = emptyList(),
            contentType: MediaType? = null,
        ) = EndpointMvcResponsePolicy(status, EndpointMvcResponseBody.NONE, headers.toList(), contentType)
    }
}
