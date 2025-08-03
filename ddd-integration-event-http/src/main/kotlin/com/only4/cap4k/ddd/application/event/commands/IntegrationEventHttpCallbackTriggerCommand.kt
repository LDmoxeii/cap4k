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

        private val logger = LoggerFactory.getLogger(IntegrationEventHttpCallbackTriggerCommand::class.java)

        override fun exec(param: Request): Response {
            val uriParams = buildMap {
                put(eventParamName, URLEncoder.encode(param.event, StandardCharsets.UTF_8))
                put(eventIdParamName, URLEncoder.encode(param.uuid, StandardCharsets.UTF_8))
            }

            val url = buildString {
                append(param.url)
                uriParams.forEach { (key, _) ->
                    append(if (contains("?")) "&" else "?")
                    append("$key={$key}")
                }
            }

            return runCatching {
                val payloadJsonStr = param.payload?.let { JSON.toJSONString(it) }
                val headers = HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                }

                val requestEntity = HttpEntity(
                    payloadJsonStr?.toByteArray(StandardCharsets.UTF_8),
                    headers
                )

                val response = restTemplate.postForEntity(
                    url,
                    requestEntity,
                    HttpIntegrationEventSubscriberAdapter.OperationResponse::class.java,
                    uriParams
                )

                when {
                    response.statusCode.is2xxSuccessful -> {
                        val body = response.body
                        if (body?.success == true) {
                            logger.info("集成事件触发成功, ${param.uuid}")
                            Response(success = true)
                        } else {
                            val errorMsg = "集成事件触发失败, ${param.uuid} (Consume) ${body?.message}"
                            logger.error(errorMsg)
                            throw RuntimeException(errorMsg)
                        }
                    }

                    else -> {
                        val errorMsg =
                            "集成事件触发失败, ${param.uuid} (Server) 集成事件HTTP消费失败:${response.statusCode.value()}"
                        logger.error(errorMsg)
                        throw RuntimeException(errorMsg)
                    }
                }
            }.onFailure { throwable ->
                logger.error("集成事件触发失败, ${param.uuid} (Client)", throwable)
            }.getOrThrow()
        }
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
