package demo.consumer
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import demo.contract.CreateBookingEndpoint
class RpcProxyShapedHandler : EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
    override fun handle(request: CreateBookingEndpoint.Request) = CreateBookingEndpoint.Response(request.customerId)
}
fun directConsumerCall(customerId: Long) = Mediator.endpoints.send(CreateBookingEndpoint.Request(customerId))
data class BookLocally(val customerId: Long) : CapabilityCall<Long>
class BookingAcl : CapabilityHandler<BookLocally, Long> {
    override fun call(request: BookLocally): Long =
        Mediator.endpoints.send(CreateBookingEndpoint.Request(request.customerId)).bookingId
}
