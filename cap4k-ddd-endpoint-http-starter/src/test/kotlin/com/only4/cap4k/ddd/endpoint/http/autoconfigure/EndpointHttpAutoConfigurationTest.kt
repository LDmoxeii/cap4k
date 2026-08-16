package com.only4.cap4k.ddd.endpoint.http.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.endpoint.EndpointSupervisor
import com.only4.cap4k.ddd.core.application.endpoint.EndpointSupervisorSupport
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponseHeader
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import jakarta.validation.ConstraintViolationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class EndpointHttpAutoConfigurationTest {
    object JsonEndpoint {
        const val OPERATION_NAME = "json.echo"
        data class Request(val displayName: String) : EndpointRequest<Response>
        data class Response(val displayName: String)
    }
    object SpecialEndpoint {
        const val OPERATION_NAME = "special.lookup"
        data class Request(val id: Long, val term: String, val tenant: String) : EndpointRequest<Response>
        data class Response(val url: String)
    }

    private lateinit var supervisor: RecordingSupervisor

    @BeforeEach fun bind() {
        supervisor = RecordingSupervisor()
        EndpointSupervisorSupport.configure(supervisor)
    }
    @AfterEach fun release() = EndpointSupervisorSupport.release(supervisor)

    @Test
    fun `whole json uses configured MVC converter and mediator dispatch`() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
        val mvc = mvc(
            listOf(EndpointMvcBinding.json(JsonEndpoint.OPERATION_NAME, JsonEndpoint.Request::class, JsonEndpoint.Response::class, HttpMethod.POST, "/echo")),
            mapper,
        )
        supervisor.result = JsonEndpoint.Response("Ada Lovelace")
        mvc.perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"display_name\":\"Ada Lovelace\"}"))
            .andExpect(status().isOk)
            .andExpect(content().json("{\"display_name\":\"Ada Lovelace\"}"))
        assertEquals(JsonEndpoint.Request("Ada Lovelace"), supervisor.request)
        assertEquals(1, supervisor.sendCount)
    }

    @Test
    fun `special mapper reads path query header and emits typed redirect`() {
        val binding = EndpointMvcBinding.special(
            SpecialEndpoint.OPERATION_NAME, SpecialEndpoint.Request::class, SpecialEndpoint.Response::class,
            HttpMethod.GET, "/lookup/{id}",
            EndpointMvcRequestMapper { request ->
                SpecialEndpoint.Request(request.path("id", Long::class), request.query("q"), request.header("X-Tenant"))
            },
            EndpointMvcResponsePolicy.none(
                status = 302,
                headers = listOf(EndpointMvcResponseHeader.property("Location", SpecialEndpoint.Response::url)),
            ),
        )
        supervisor.result = SpecialEndpoint.Response("/result/42")
        mvc(listOf(binding)).perform(get("/lookup/42?q=book").header("X-Tenant", "acme"))
            .andExpect(status().isFound).andExpect(header().string("Location", "/result/42")).andExpect(content().string(""))
        assertEquals(SpecialEndpoint.Request(42, "book", "acme"), supervisor.request)
    }

    @Test
    fun `missing conversion malformed body and endpoint validation are bad request`() {
        val special = EndpointMvcBinding.special(
            SpecialEndpoint.OPERATION_NAME, SpecialEndpoint.Request::class, SpecialEndpoint.Response::class,
            HttpMethod.GET, "/lookup/{id}", EndpointMvcRequestMapper { r -> SpecialEndpoint.Request(r.path("id", Long::class), r.query("q"), r.header("X-Tenant")) },
        )
        mvc(listOf(special)).perform(get("/lookup/nope?q=x").header("X-Tenant", "a")).andExpect(status().isBadRequest)
        mvc(listOf(special)).perform(get("/lookup/1").header("X-Tenant", "a")).andExpect(status().isBadRequest)

        val json = EndpointMvcBinding.json(JsonEndpoint.OPERATION_NAME, JsonEndpoint.Request::class, JsonEndpoint.Response::class, HttpMethod.POST, "/echo")
        mvc(listOf(json)).perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
        supervisor.failure = ConstraintViolationException(emptySet())
        mvc(listOf(json)).perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\"}"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `handler exception propagates to configured MVC resolver`() {
        val binding = EndpointMvcBinding.json(JsonEndpoint.OPERATION_NAME, JsonEndpoint.Request::class, JsonEndpoint.Response::class, HttpMethod.POST, "/echo")
        supervisor.failure = IllegalStateException("domain failure")
        val routes = EndpointHttpAutoConfiguration().cap4kEndpointHttpRoutes(listOf(binding), DefaultFormattingConversionService())
        MockMvcBuilders.routerFunctions(routes).setHandlerExceptionResolvers(
            org.springframework.web.servlet.HandlerExceptionResolver { _, response, _, ex ->
                if (ex is IllegalStateException) {
                    response.status = 409
                    response.writer.write("resolved:${ex.message}")
                    org.springframework.web.servlet.ModelAndView()
                } else null
            },
        ).build()
            .perform(post("/echo").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\"}"))
            .andExpect(status().isConflict).andExpect(content().string("resolved:domain failure"))
    }

    private fun mvc(bindings: List<EndpointMvcBinding<*, *>>, mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())): MockMvc {
        val routes = EndpointHttpAutoConfiguration().cap4kEndpointHttpRoutes(bindings, DefaultFormattingConversionService())
        return MockMvcBuilders.routerFunctions(routes)
            .setMessageConverters(MappingJackson2HttpMessageConverter(mapper)).build()
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    class TestAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException::class)
        fun handle(ex: IllegalStateException) = org.springframework.http.ResponseEntity.status(409).body("resolved:${ex.message}")
    }

    private class RecordingSupervisor : EndpointSupervisor {
        var request: Any? = null
        var result: Any? = null
        var failure: RuntimeException? = null
        var sendCount = 0
        override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE {
            sendCount++
            this.request = request
            failure?.let { throw it }
            @Suppress("UNCHECKED_CAST") return result as RESPONSE
        }
        override fun <REQUEST : EndpointRequest<RESPONSE>, RESPONSE : Any> sendAsync(request: REQUEST): CompletionStage<RESPONSE> =
            CompletableFuture.completedFuture(send(request))
    }
}
