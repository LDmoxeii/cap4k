package com.acme.endpoint.adapter

import com.acme.endpoint.contract.CreateBookingEndpoint
import com.acme.endpoint.contract.GetResourceEndpoint
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcBinding
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcRequestMapper
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponseHeader
import com.only4.cap4k.ddd.endpoint.http.EndpointMvcResponsePolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

@Configuration(proxyBeanMethods = false)
class EndpointHttpBindings {
    @Bean
    fun createBookingHttpBinding(): EndpointMvcBinding<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> =
        EndpointMvcBinding.json(
            operationName = CreateBookingEndpoint.OPERATION_NAME,
            requestType = CreateBookingEndpoint.Request::class,
            responseType = CreateBookingEndpoint.Response::class,
            method = HttpMethod.POST,
            path = "/api/bookings",
        )

    @Bean
    fun getResourceHttpBinding(): EndpointMvcBinding<GetResourceEndpoint.Request, GetResourceEndpoint.Response> =
        EndpointMvcBinding.special(
            operationName = GetResourceEndpoint.OPERATION_NAME,
            requestType = GetResourceEndpoint.Request::class,
            responseType = GetResourceEndpoint.Response::class,
            method = HttpMethod.GET,
            path = "/file/getResource",
            requestMapper = EndpointMvcRequestMapper { request ->
                GetResourceEndpoint.Request(sourceName = request.query("sourceName"))
            },
            responsePolicy = EndpointMvcResponsePolicy.none(
                status = 302,
                headers = listOf(
                    EndpointMvcResponseHeader.property("Location", GetResourceEndpoint.Response::url),
                ),
            ),
        )

    @Bean
    fun commandUnitOfWorkCoordinator(): CommandUnitOfWorkCoordinator = object : CommandUnitOfWorkCoordinator {
        override val active: Boolean = false
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }

    @Bean
    fun queryExecution(): QueryExecution = object : QueryExecution {
        override val active: Boolean = false
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }
}
