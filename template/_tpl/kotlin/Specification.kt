package $

{ basePackage }.domain.aggregates${ package }.specs

import ${basePackage}.domain.aggregates${package}.${Entity}
import com.only4.cap4k.ddd.core.domain.aggregate.Specification
import com.only4.cap4k.ddd.core.domain.aggregate.Specification.Result
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import org.springframework.stereotype.Service

/**
 * @author cap4k-ddd-codegen
 * @date ${date}
 */
@Service
@Aggregate(
    aggregate = "${Entity}",
    name = "${Entity}Specification",
    type = Aggregate.TYPE_SPECIFICATION,
    description = ""
)
class $ {Entity }Specification : Specification<${ Entity }> {

    override fun specify(entity: $ {
        Entity
    }): Result {
        return Result.pass()
    }

}
