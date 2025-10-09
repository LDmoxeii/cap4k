package $

{ basePackage }.adapter.application.queries

import ${basePackage}.application.queries${package}.${Query}
import com.only4.cap4k.ddd.core.application.query.Query
import org.springframework.stereotype.Service

@Service
class $ {Query }Handler(
) : Query<${ Query }.Request, ${ Query }.Response> {

    override fun exec(request: $ {
        Query
    }.Request): ${ Query }.Response {

        return ${ Query }.Response(

    )
    }
}
