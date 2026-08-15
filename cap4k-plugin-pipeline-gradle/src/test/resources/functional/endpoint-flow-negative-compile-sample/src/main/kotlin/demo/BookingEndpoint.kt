package demo

import com.only4.cap4k.analysis.metadata.DesignBlockMetadata
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler

@DesignBlockMetadata(
    tag = "endpoint",
    name = "CreateBooking",
    packageName = "booking",
    operationName = "booking.create",
    family = "endpoint",
)
object CreateBookingEndpoint {
    data class Request(val customerId: Long) : EndpointRequest<Response>
    data class Response(val bookingId: Long)
}

class LocalCreateBookingHandler : EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
    override fun handle(request: CreateBookingEndpoint.Request) = CreateBookingEndpoint.Response(request.customerId)
}

fun dispatchLocally(customerId: Long): CreateBookingEndpoint.Response =
    Mediator.endpoints.send(CreateBookingEndpoint.Request(customerId))
