package com.only4.cap4k.ddd.application.event.capabilities

import com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberAdapter
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Invokes the remote integration-event unsubscription capability.
 */
object IntegrationEventHttpUnsubscribeCapability {
    class Handler(
        private val restTemplate: RestTemplate,
        private val eventParamName: String,
        private val subscriberParamName: String,
    ) : CapabilityHandler<Request, Response> {
        private val log = LoggerFactory.getLogger(IntegrationEventHttpUnsubscribeCapability::class.java)

        override fun call(request: Request): Response {
            val uriParams = buildUriParams(request)
            val url = buildUrlWithParams(request.url, uriParams)

            return runCatching {
                val response = restTemplate.postForEntity(
                    url,
                    createRequestEntity(),
                    HttpIntegrationEventSubscriberAdapter.OperationResponse::class.java,
                    uriParams,
                )

                processResponse(response, request.event, "退订")
            }.onFailure { throwable ->
                log.error("集成事件HTTP退订失败, ${request.event} (Capability)", throwable)
            }.getOrThrow()
        }

        private fun buildUriParams(request: Request) = mapOf(
            eventParamName to request.event.urlEncode(),
            subscriberParamName to request.subscriber.urlEncode(),
        )

        private fun buildUrlWithParams(baseUrl: String, params: Map<String, String>) = buildString {
            append(baseUrl)
            params.keys.forEach { key ->
                append(if (contains("?")) "&" else "?")
                append("$key={$key}")
            }
        }

        private fun createRequestEntity() = HttpEntity(
            "",
            HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
        )

        private fun processResponse(
            response: org.springframework.http.ResponseEntity<HttpIntegrationEventSubscriberAdapter.OperationResponse<out Any>>,
            event: String,
            operation: String,
        ): Response = when {
            response.statusCode.is2xxSuccessful -> {
                val body = response.body
                when {
                    body?.success == true -> {
                        log.info("集成事件HTTP${operation}成功, $event")
                        Response(success = true)
                    }

                    else -> {
                        val errorMessage = "集成事件HTTP${operation}失败, $event (Consume) ${body?.message}"
                        log.error(errorMessage)
                        throw RuntimeException(errorMessage)
                    }
                }
            }

            else -> {
                val errorMessage = "集成事件HTTP${operation}失败, $event (Server) ${response.statusCode.value()}"
                log.error(errorMessage)
                throw RuntimeException(errorMessage)
            }
        }

        private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
    }

    data class Request(
        val url: String,
        val event: String,
        val subscriber: String,
    ) : CapabilityCall<Response>

    data class Response(
        val success: Boolean,
    )
}
