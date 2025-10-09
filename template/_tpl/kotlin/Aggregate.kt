package ${basePackage}.domain.aggregates${package}

import ${basePackage}.domain.aggregates${package}.factory.${Entity}Factory
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate

class ${aggregateNameTemplate} (
payload: ${Entity}Factory.Payload? = null,
) : Aggregate.Default<${Entity}>(payload) {

    val id by lazy { root.id }

    class Id(key: ${IdentityType}) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<${aggregateNameTemplate}, ${IdentityType} > (key)
}
