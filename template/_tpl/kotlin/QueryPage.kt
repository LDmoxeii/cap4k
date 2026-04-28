import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.query.PageRequest

object $ {Query }

{

    class Request(
        override val pageNum: Int = 1,
        override val pageSize: Int = 10

    ) : PageRequest, RequestParam<Response>

    class Response(
    )
}
