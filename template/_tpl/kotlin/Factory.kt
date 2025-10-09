package $

{ basePackage }.domain.aggregates${ package }.factory

import ${basePackage}.domain.aggregates${package}.${Entity}
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate
import org.springframework.stereotype.Service

@Service
@Aggregate(aggregate = "${Entity}", name = "${Entity}Factory", type = Aggregate.TYPE_FACTORY, description = "")
class $ {Entity }Factory : AggregateFactory<${ Entity }Factory.Payload, ${ Entity }> {
    override fun create(payload: Payload): $ {
        Entity
    } {
        return ${ Entity }(

    )
    }

    @Aggregate(
        aggregate = "${Entity}",
        name = "${Entity}Payload",
        type = Aggregate.TYPE_FACTORY_PAYLOAD,
        description = ""
    )
    class Payload(

    ) : AggregatePayload<$ {Entity }>
}
