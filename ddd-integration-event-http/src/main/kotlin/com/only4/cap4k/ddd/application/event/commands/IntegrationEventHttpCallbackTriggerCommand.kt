package com.only4.cap4k.ddd.application.event.commands

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.application.event.HttpIntegrationEventSubscriberAdapter
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.command.Command
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 集成事件HTTP回调触发命令
 *
 * @author binking338
 * @date 2025/6/18
 */
object IntegrationEventHttpCallbackTriggerCommand {

    class Handler(
        private val restTemplate: RestTemplate,
        private val eventParamName: String,
        private val eventIdParamName: String
    ) : Command<Request, Response> {

        private val log = LoggerFactory.getLogger(IntegrationEventHttpCallbackTriggerCommand::class.java)

        override fun exec(param: Request): Response {
            val uriParams = buildUriParams(param)
            val url = buildUrlWithParams(param.url, uriParams)

            return runCatching {
                val requestEntity = createRequestEntity(param.payload)
                val response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    HttpIntegrationEventSubscriberAdapter.OperationResponse::class.java,
                    uriParams
                )

                processResponse(response, param.uuid)
            }.onFailure { throwable ->
                log.error("集成事件触发失败, ${param.uuid} (Client)", throwable)
            }.getOrThrow()
        }

        private fun buildUriParams(param: Request) = mapOf(
            eventParamName to param.event.urlEncode(),
            eventIdParamName to param.uuid.urlEncode()
        )

        private fun buildUrlWithParams(baseUrl: String, params: Map<String, String>) = buildString {
            append(baseUrl)
            params.keys.forEach { key ->
                append(if (contains("?")) "&" else "?")
                append("$key={$key}")
            }
        }

        private fun createRequestEntity(payload: Any?) = HttpEntity(
            payload?.let { JSON.toJSONString(it).toByteArray(StandardCharsets.UTF_8) },
            HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        )

        private fun processResponse(
            response: org.springframework.http.ResponseEntity<HttpIntegrationEventSubscriberAdapter.OperationResponse<out Any>>,
            uuid: String
        ): Response = when {
            response.statusCode.is2xxSuccessful -> {
                val body = response.body
                when {
                    body?.success == true -> {
                        log.info("集成事件触发成功, $uuid")
                        Response(success = true)
                    }

                    else -> {
                        val errorMsg = "集成事件触发失败, $uuid (Consume) ${body?.message}"
                        log.error(errorMsg)
                        throw RuntimeException(errorMsg)
                    }
                }
            }

            else -> {
                val errorMsg = "集成事件触发失败, $uuid (Server) 集成事件HTTP消费失败:${response.statusCode.value()}"
                log.error(errorMsg)
                throw RuntimeException(errorMsg)
            }
        }

        private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
    }

    data class Request(
        val url: String,
        val uuid: String,
        val event: String,
        val payload: Any?
    ) : RequestParam<Response>

    data class Response(
        val success: Boolean
    )
}
