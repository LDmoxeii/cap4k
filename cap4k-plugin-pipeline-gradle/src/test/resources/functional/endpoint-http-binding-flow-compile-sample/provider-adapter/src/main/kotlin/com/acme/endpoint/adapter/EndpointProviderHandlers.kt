package com.acme.endpoint.adapter

import com.acme.endpoint.application.CreateBookingCommand
import com.acme.endpoint.application.GetResourceQuery
import com.acme.endpoint.contract.CreateBookingEndpoint
import com.acme.endpoint.contract.GetResourceEndpoint
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import org.springframework.stereotype.Component

@Component
class CreateBookingEndpointHandler(
    private val commands: CommandSupervisor,
) : EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
    override fun handle(request: CreateBookingEndpoint.Request): CreateBookingEndpoint.Response {
        val result = commands.send(CreateBookingCommand(customerName = request.customerName))
        return CreateBookingEndpoint.Response(bookingId = result.bookingId)
    }
}

@Component
class GetResourceEndpointHandler(
    private val queries: QuerySupervisor,
) : EndpointHandler<GetResourceEndpoint.Request, GetResourceEndpoint.Response> {
    override fun handle(request: GetResourceEndpoint.Request): GetResourceEndpoint.Response {
        val result = queries.ask(GetResourceQuery(sourceName = request.sourceName))
        return GetResourceEndpoint.Response(url = result.url)
    }
}
