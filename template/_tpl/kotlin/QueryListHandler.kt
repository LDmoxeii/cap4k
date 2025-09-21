import com.only4.cap4k.ddd.core.application.query.ListQuery
import org.springframework.stereotype.Service

@Service
class $ {Query }Handler(
) : ListQuery<${ Query }.Request, ${ Query }.Response> {

    override fun exec(request: $ {
        Query
    }.Request): List<${ Query }.Response > {

        return listOf($ { Query }.Response(

        ))

    }
}
