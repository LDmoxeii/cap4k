package com.only4.cap4k.ddd.endpoint.http.autoconfigure

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBindingException
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBindingValidator
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequest
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponseBody
import jakarta.validation.ConstraintViolationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.ConversionService
import org.springframework.http.HttpStatusCode
import org.springframework.web.servlet.function.HandlerFunction
import org.springframework.web.servlet.function.RequestPredicates
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.RouterFunctions
import org.springframework.web.servlet.function.ServerResponse

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class EndpointHttpAutoConfiguration {
    @Bean
    fun cap4kEndpointHttpRoutes(
        bindings: List<EndpointMvcBinding<*, *>>,
        @Qualifier("mvcConversionService") conversionService: ConversionService,
    ): RouterFunction<ServerResponse> {
        EndpointMvcBindingValidator.validateAll(bindings)
        if (bindings.isEmpty()) return RouterFunction { java.util.Optional.empty() }
        val first = bindings.first()
        return bindings.drop(1).fold(RouterFunctions.route(route(first), handler(first, conversionService))) { routes, binding ->
            routes.andRoute(route(binding), handler(binding, conversionService))
        }
    }

    private fun route(binding: EndpointMvcBinding<*, *>) =
        RequestPredicates.method(binding.method).and(RequestPredicates.path(binding.path))

    private fun handler(
        binding: EndpointMvcBinding<*, *>,
        conversionService: ConversionService,
    ): HandlerFunction<ServerResponse> = HandlerFunction { serverRequest ->
        try {
            @Suppress("UNCHECKED_CAST")
            val typed = binding as EndpointMvcBinding<com.only4.cap4k.contract.EndpointRequest<Any>, Any>
            val request = typed.requestMapper.map(EndpointMvcRequest(serverRequest, conversionService))
            val response = Mediator.endpoints.send(request)
            var builder = ServerResponse.status(HttpStatusCode.valueOf(typed.responsePolicy.status))
            typed.responsePolicy.contentType?.let { builder = builder.contentType(it) }
            typed.responsePolicy.headers.forEach { header -> builder = builder.header(header.name, header.resolve(response)) }
            when (typed.responsePolicy.body) {
                EndpointMvcResponseBody.RESPONSE -> builder.body(response)
                EndpointMvcResponseBody.NONE -> builder.build()
            }
        } catch (failure: EndpointMvcBindingException) {
            ServerResponse.badRequest().build()
        } catch (failure: ConstraintViolationException) {
            ServerResponse.badRequest().build()
        }
    }
}
