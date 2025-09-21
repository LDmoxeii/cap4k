import com.only4.cap4k.ddd.core.application.query.PageQuery
import org.springframework.stereotype.Service

@Service
class $ {Query }Handler(
) : PageQuery<${ Query }.Request, ${ Query }.Response> {

    override fun exec(request: $ {
        Query
    }.Request): PageData<${ Query }.Response > {
    }
}
