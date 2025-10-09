package $

{ basePackage }.adapter.application.queries;

import ${basePackage}.application.queries${package}.${Query}
import com.only4.cap4k.ddd.core.application.query.PageQuery
import com.only4.cap4k.ddd.core.share.PageData
import org.springframework.stereotype.Service

@Service
class $ {Query }Handler(
) : PageQuery<${ Query }.Request, ${ Query }.Response> {

    override fun exec(request: $ {
        Query
    }.Request): PageData<${ Query }.Response > {

        return PageData.empty()

    }
}
