package demo.provider

import com.acme.rpc.contract.endpoints.booking.CreateBookingEndpoint
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler

class CreateBookingEndpointHandler :
    EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
    override fun handle(request: CreateBookingEndpoint.Request) =
        CreateBookingEndpoint.Response(bookingId = "booking-${request.customerId}")
}
