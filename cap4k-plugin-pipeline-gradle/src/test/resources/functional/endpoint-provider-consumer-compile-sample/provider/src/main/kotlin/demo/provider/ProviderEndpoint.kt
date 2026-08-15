package demo.provider
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import demo.contract.CreateBookingEndpoint
class ProviderHandler : EndpointHandler<CreateBookingEndpoint.Request, CreateBookingEndpoint.Response> {
    override fun handle(request: CreateBookingEndpoint.Request) = CreateBookingEndpoint.Response(request.customerId)
}
fun providerBinding(request: CreateBookingEndpoint.Request): CreateBookingEndpoint.Response =
    Mediator.endpoints.send(request)
