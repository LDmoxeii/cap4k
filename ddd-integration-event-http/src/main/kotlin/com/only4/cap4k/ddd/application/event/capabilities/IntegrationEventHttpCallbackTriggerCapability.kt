package com.only4.cap4k.ddd.application.event.capabilities

import com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberAdapter
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_EXECUTION_CONTEXT
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4K_TIMESTAMP
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Invokes a subscriber-owned HTTP callback capability.
 */
object IntegrationEventHttpCallbackTriggerCapability {
    class Handler(
        private val restTemplate: RestTemplate,
        private val eventParamName: String,
        private val eventIdParamName: String,
    ) : CapabilityHandler<Request, Response> {
        private val log = LoggerFactory.getLogger(IntegrationEventHttpCallbackTriggerCapability::class.java)

        override fun call(request: Request): Response {
            val uriParams = buildUriParams(request)
            val url = buildUrlWithParams(request.url, uriParams)

            return runCatching {
                val requestEntity = createRequestEntity(
                    request.envelopeJson ?: request.payload?.let { RuntimeJson.write(it) },
                    request.publishedAt,
                    request.executionContext,
                )
                val response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    HttpIntegrationEventSubscriberAdapter.OperationResponse::class.java,
                    uriParams,
                )

                processResponse(response, request.uuid)
            }.onFailure { throwable ->
                log.error("集成事件触发失败, ${request.uuid} (Capability)", throwable)
            }.getOrThrow()
        }

        private fun buildUriParams(request: Request) = mapOf(
            eventParamName to request.event.urlEncode(),
            eventIdParamName to request.uuid.urlEncode(),
        )

        private fun buildUrlWithParams(baseUrl: String, params: Map<String, String>) = buildString {
            append(baseUrl)
            params.keys.forEach { key ->
                append(if (contains("?")) "&" else "?")
                append("$key={$key}")
            }
        }

        private fun createRequestEntity(payloadJson: String?, publishedAt: Instant, executionContext: String) = HttpEntity(
            payloadJson?.toByteArray(StandardCharsets.UTF_8),
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set(HEADER_KEY_CAP4K_TIMESTAMP, publishedAt.toEpochMilli().toString())
                set(HEADER_KEY_CAP4K_EXECUTION_CONTEXT, executionContext)
            },
        )

        private fun processResponse(
            response: org.springframework.http.ResponseEntity<HttpIntegrationEventSubscriberAdapter.OperationResponse<out Any>>,
            uuid: String,
        ): Response = when {
            response.statusCode.is2xxSuccessful -> {
                val body = response.body
                when {
                    body?.success == true -> {
                        log.info("集成事件触发成功, $uuid")
                        Response(success = true)
                    }

                    else -> {
                        val errorMessage = "集成事件触发失败, $uuid (Consume) ${body?.message}"
                        log.error(errorMessage)
                        throw RuntimeException(errorMessage)
                    }
                }
            }

            else -> {
                val errorMessage = "集成事件触发失败, $uuid (Server) 集成事件HTTP消费失败:${response.statusCode.value()}"
                log.error(errorMessage)
                throw RuntimeException(errorMessage)
            }
        }

        private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
    }

    data class Request(
        val url: String,
        val uuid: String,
        val event: String,
        val payload: Any?,
        val publishedAt: Instant,
        val executionContext: String = "[]",
        /** Canonical transport-neutral envelope JSON; legacy payload fields remain for old direct callers. */
        val envelopeJson: String? = null,
    ) : CapabilityCall<Response>

    data class Response(
        val success: Boolean,
    )
}
