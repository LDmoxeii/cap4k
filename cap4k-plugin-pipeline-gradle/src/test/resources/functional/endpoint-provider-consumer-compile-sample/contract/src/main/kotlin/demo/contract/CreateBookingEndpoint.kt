package demo.contract
import com.only4.cap4k.contract.EndpointRequest
object CreateBookingEndpoint {
    const val OPERATION_NAME = "booking.create"
    data class Request(val customerId: Long) : EndpointRequest<Response>
    data class Response(val bookingId: Long)
}
