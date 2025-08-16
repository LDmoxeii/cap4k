package com.only4.cap4k.ddd.console

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.console.event.EventConsoleService
import com.only4.cap4k.ddd.console.locker.LockerConsoleService
import com.only4.cap4k.ddd.console.request.RequestConsoleService
import com.only4.cap4k.ddd.console.saga.SagaConsoleService
import com.only4.cap4k.ddd.console.snowflake.SnowflakeConsoleService
import com.only4.cap4k.ddd.core.application.RequestManager
import com.only4.cap4k.ddd.core.application.saga.SagaManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.HttpRequestHandler
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Configuration
class DDDConsoleAutoConfiguration {

    companion object {
        const val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private val log = LoggerFactory.getLogger(DDDConsoleAutoConfiguration::class.java)
    }

    @Bean
    @ConditionalOnMissingBean(EventConsoleService::class)
    fun eventConsoleService(jdbcTemplate: JdbcTemplate, eventPublisher: EventPublisher) =
        EventConsoleService(jdbcTemplate, eventPublisher)

    @Bean
    @ConditionalOnMissingBean(RequestConsoleService::class)
    fun requestConsoleService(jdbcTemplate: JdbcTemplate, requestManager: RequestManager) =
        RequestConsoleService(jdbcTemplate, requestManager)

    @Bean
    @ConditionalOnMissingBean(SagaConsoleService::class)
    fun sagaConsoleService(jdbcTemplate: JdbcTemplate, sagaManager: SagaManager) =
        SagaConsoleService(jdbcTemplate, sagaManager)

    @Bean
    @ConditionalOnMissingBean(LockerConsoleService::class)
    fun lockerConsoleService(jdbcTemplate: JdbcTemplate) =
        LockerConsoleService(jdbcTemplate)

    @Bean
    @ConditionalOnMissingBean(SnowflakeConsoleService::class)
    fun snowflakeConsoleService(jdbcTemplate: JdbcTemplate) =
        SnowflakeConsoleService(jdbcTemplate)

    data class OperationResponse<T>(
        val success: Boolean,
        val message: String,
        val data: T? = null,
    )

    @Bean(name = ["/cap4k/console/event/search"])
    fun eventSearch(
        eventConsoleService: EventConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val param = EventConsoleService.SearchParam().apply {
            uuid = req.getParameter("uuid")
            type = req.getParameter("type")
            req.getParameterValues("state")?.let { stateParam ->
                state = stateParam.map { it.toInt() }.toIntArray()
            }
            req.getParameterValues("scheduleAt")?.let { scheduleParams ->
                scheduleAt = scheduleParams.map {
                    LocalDateTime.parse(it, DateTimeFormatter.ofPattern(DATE_TIME_FORMAT))
                }.toTypedArray()
            }

            pageNum = req.getParameter("pageNum")?.toInt() ?: 1
            pageSize = req.getParameter("pageSize")?.toInt() ?: 20
        }

        val result = try {
            OperationResponse(
                success = true,
                message = "ok",
                data = eventConsoleService.search(param)
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/event/search?uuid={uuid}&type={type}&state={state}&scheduleAt={scheduleAtBegin}&scheduleAt={scheduleAtEnd}&pageSize={pageSize}&pageNum={pageNum}") }

    @Bean(name = ["/cap4k/console/event/retry"])
    fun eventRetry(
        eventConsoleService: EventConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val uuid = req.getParameter("uuid")
        val result = try {
            eventConsoleService.retry(uuid)
            OperationResponse(
                success = true,
                message = "ok",
                data = true
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/event/retry?uuid={uuid}") }

    @Bean(name = ["/cap4k/console/request/search"])
    fun requestSearch(
        requestConsoleService: RequestConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val param = RequestConsoleService.SearchParam().apply {
            uuid = req.getParameter("uuid")
            type = req.getParameter("type")
            req.getParameterValues("state")?.let { stateParam ->
                state = stateParam.map { it.toInt() }.toIntArray()
            }
            req.getParameterValues("scheduleAt")?.let { scheduleParams ->
                scheduleAt = scheduleParams.map {
                    LocalDateTime.parse(it, DateTimeFormatter.ofPattern(DATE_TIME_FORMAT))
                }.toTypedArray()
            }
            pageNum = req.getParameter("pageNum")?.toInt() ?: 1
            pageSize = req.getParameter("pageSize")?.toInt() ?: 20
        }

        val result = try {
            OperationResponse(
                success = true,
                message = "ok",
                data = requestConsoleService.search(param)
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/request/search?uuid={uuid}&type={type}&state={state}&scheduleAt={scheduleAtBegin}&scheduleAt={scheduleAtEnd}&pageSize={pageSize}&pageNum={pageNum}") }

    @Bean(name = ["/cap4k/console/request/retry"])
    fun requestRetry(
        requestConsoleService: RequestConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val uuid = req.getParameter("uuid")
        val result = try {
            requestConsoleService.retry(uuid)
            OperationResponse(
                success = true,
                message = "ok",
                data = true
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/request/retry?uuid={uuid}") }

    @Bean(name = ["/cap4k/console/saga/search"])
    fun sagaSearch(
        sagaConsoleService: SagaConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val param = SagaConsoleService.SearchParam().apply {
            uuid = req.getParameter("uuid")
            type = req.getParameter("type")
            req.getParameterValues("state")?.let { stateParam ->
                state = stateParam.map { it.toInt() }.toIntArray()
            }
            req.getParameterValues("scheduleAt")?.let { scheduleParams ->
                scheduleAt = scheduleParams.map {
                    LocalDateTime.parse(it, DateTimeFormatter.ofPattern(DATE_TIME_FORMAT))
                }.toTypedArray()
            }
            pageNum = req.getParameter("pageNum")?.toInt() ?: 1
            pageSize = req.getParameter("pageSize")?.toInt() ?: 20
        }

        val result = try {
            OperationResponse(
                success = true,
                message = "ok",
                data = sagaConsoleService.search(param)
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/saga/search?uuid={uuid}&type={type}&state={state}&scheduleAt={scheduleAtBegin}&scheduleAt={scheduleAtEnd}&pageSize={pageSize}&pageNum={pageNum}") }

    @Bean(name = ["/cap4k/console/saga/retry"])
    fun sagaRetry(
        sagaConsoleService: SagaConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val uuid = req.getParameter("uuid")
        val result = try {
            sagaConsoleService.retry(uuid)
            OperationResponse(
                success = true,
                message = "ok",
                data = true
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/saga/retry?uuid={uuid}") }

    @Bean(name = ["/cap4k/console/locker/search"])
    fun lockerSearch(
        lockerConsoleService: LockerConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val param = LockerConsoleService.SearchParam().apply {
            name = req.getParameter("name")
            lock = req.getParameter("lock")?.toBoolean()
            pageNum = req.getParameter("pageNum")?.toInt() ?: 1
            pageSize = req.getParameter("pageSize")?.toInt() ?: 20
        }

        val result = try {
            OperationResponse(
                success = true,
                message = "ok",
                data = lockerConsoleService.search(param)
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/locker/search?name={name}&lock={true|false}&pageSize={pageSize}&pageNum={pageNum}") }

    @Bean(name = ["/cap4k/console/locker/unlock"])
    fun lockerUnlock(
        lockerConsoleService: LockerConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val name = req.getParameter("name")
        val pwd = req.getParameter("pwd")
        val result = try {
            val success = lockerConsoleService.unlock(name, pwd)
            OperationResponse(
                success = success,
                message = "ok",
                data = success
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/locker/unlock?name={name}&pwd={pwd}") }

    @Bean(name = ["/cap4k/console/snowflake/search"])
    fun snowflakeSearch(
        snowflakeConsoleService: SnowflakeConsoleService,
        @Value("\${server.port:80}") serverPort: String,
        @Value("\${server.servlet.context-path:}") serverServletContentPath: String,
    ) = HttpRequestHandler { req, res ->
        val param = SnowflakeConsoleService.SearchParam().apply {
            free = req.getParameter("free")?.toBoolean()
            dispatchTo = req.getParameter("dispatchTo")
            pageNum = req.getParameter("pageNum")?.toInt() ?: 1
            pageSize = req.getParameter("pageSize")?.toInt() ?: 20
        }

        val result = try {
            OperationResponse(
                success = true,
                message = "ok",
                data = snowflakeConsoleService.search(param)
            )
        } catch (throwable: Throwable) {
            OperationResponse<Any>(
                success = false,
                message = throwable.message ?: "Unknown error"
            )
        }

        res.apply {
            characterEncoding = StandardCharsets.UTF_8.name()
            contentType = "application/json; charset=utf-8"
            writer.apply {
                println(JSON.toJSONString(result))
                flush()
                close()
            }
        }
    }.apply { log.info("DDD Console URL: http://localhost:$serverPort$serverServletContentPath/cap4k/console/snowflake/search?free={true|false}&dispatchTo={dispatchTo}&pageSize={pageSize}&pageNum={pageNum}") }
}
