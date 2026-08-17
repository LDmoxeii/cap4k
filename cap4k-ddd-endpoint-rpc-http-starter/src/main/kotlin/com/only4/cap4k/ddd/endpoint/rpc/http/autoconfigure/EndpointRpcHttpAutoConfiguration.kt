package com.only4.cap4k.ddd.endpoint.rpc.http.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcCodec
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcFailure
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcFailureCategory
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderBinding
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderDispatcher
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcProviderRegistry
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcRequestEnvelope
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcResponseEnvelope
import com.only4.cap4k.ddd.endpoint.rpc.EndpointTransportInvoker
import com.only4.cap4k.ddd.endpoint.rpc.http.EndpointRpcHttpRequestCustomizer
import com.only4.cap4k.ddd.endpoint.rpc.http.EndpointRpcRouteResolver
import com.only4.cap4k.ddd.endpoint.rpc.http.HttpEndpointTransportInvoker
import com.only4.cap4k.ddd.endpoint.rpc.http.JacksonEndpointRpcCodec
import com.only4.cap4k.ddd.endpoint.rpc.http.StaticEndpointRpcRouteResolver
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.HttpRequestHandler
import java.nio.charset.StandardCharsets

@AutoConfiguration
@EnableConfigurationProperties(EndpointRpcHttpProperties::class)
class EndpointRpcHttpAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun endpointRpcCodec(objectMapper: ObjectMapper): EndpointRpcCodec = JacksonEndpointRpcCodec(objectMapper)

    @Bean
    @ConditionalOnMissingBean
    fun endpointRpcRouteResolver(properties: EndpointRpcHttpProperties): EndpointRpcRouteResolver =
        StaticEndpointRpcRouteResolver(properties.routes)

    @Bean
    @ConditionalOnMissingBean
    fun endpointRpcHttpRequestCustomizer(): EndpointRpcHttpRequestCustomizer = EndpointRpcHttpRequestCustomizer.NONE

    @Bean
    @ConditionalOnMissingBean
    fun endpointTransportInvoker(
        resolver: EndpointRpcRouteResolver,
        codec: EndpointRpcCodec,
        objectMapper: ObjectMapper,
        accessor: ExecutionContextAccessor,
        contextCodecs: ExecutionContextCodecRegistry,
        properties: EndpointRpcHttpProperties,
        customizer: EndpointRpcHttpRequestCustomizer,
    ): EndpointTransportInvoker = HttpEndpointTransportInvoker(
        resolver,
        codec,
        objectMapper,
        accessor,
        contextCodecs,
        properties.connectTimeout,
        properties.responseTimeout,
        customizer,
    )

    @Bean
    @ConditionalOnBean(EndpointRpcProviderBinding::class)
    fun endpointRpcProviderRegistry(
        properties: EndpointRpcHttpProperties,
        bindings: List<EndpointRpcProviderBinding<*, *>>,
    ) = EndpointRpcProviderRegistry(properties.serviceId, bindings)

    @Bean
    @ConditionalOnBean(EndpointRpcProviderRegistry::class)
    fun endpointRpcProviderDispatcher(
        registry: EndpointRpcProviderRegistry,
        codec: EndpointRpcCodec,
        contextCodecs: ExecutionContextCodecRegistry,
        scopeManager: ExecutionContextScopeManager,
    ) = EndpointRpcProviderDispatcher(registry, codec, contextCodecs, scopeManager)

    @Bean(name = [RPC_PATH])
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(EndpointRpcProviderDispatcher::class)
    fun endpointRpcHttpHandler(
        dispatcher: EndpointRpcProviderDispatcher,
        objectMapper: ObjectMapper,
        codec: EndpointRpcCodec,
    ): HttpRequestHandler = HttpRequestHandler { request, response ->
        when {
            request.method != HttpMethod.POST.name() -> {
                response.status = 405
                response.setHeader("Allow", HttpMethod.POST.name())
                writeResponse(
                    response,
                    objectMapper,
                    failure(codec, "method_not_allowed"),
                )
            }

            !isJson(request.contentType) -> {
                response.status = 415
                writeResponse(
                    response,
                    objectMapper,
                    failure(codec, "unsupported_media_type"),
                )
            }

            else -> {
                val envelope = try {
                    objectMapper.readValue(request.inputStream, EndpointRpcRequestEnvelope::class.java)
                } catch (_: Exception) {
                    response.status = 400
                    writeResponse(
                        response,
                        objectMapper,
                        failure(codec, "malformed_envelope"),
                    )
                    return@HttpRequestHandler
                }
                val result = dispatcher.dispatch(envelope)
                response.status = 200
                writeResponse(response, objectMapper, result)
            }
        }
    }

    private fun isJson(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return false
        return runCatching {
            MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))
        }.getOrDefault(false)
    }

    private fun failure(codec: EndpointRpcCodec, code: String) = EndpointRpcResponseEnvelope(
        codec = codec.identity,
        success = false,
        failure = EndpointRpcFailure(EndpointRpcFailureCategory.PROTOCOL, code),
    )

    private fun writeResponse(
        response: jakarta.servlet.http.HttpServletResponse,
        objectMapper: ObjectMapper,
        envelope: EndpointRpcResponseEnvelope,
    ) {
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.contentType = "application/json; charset=utf-8"
        response.writer.use { it.write(objectMapper.writeValueAsString(envelope)) }
    }

    companion object {
        const val RPC_PATH = "/cap4k/endpoints/rpc"
    }
}
