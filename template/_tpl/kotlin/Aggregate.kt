import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate

class Agg${ entity }(
payload: ${ Entity }Factory.Payload? = null,
) : Aggregate.Default<${ Entity }>(payload) {

    val id by lazy { root.id }

    class Id(key: $ {IdentityType }) : com.only4.cap4k.ddd.core.domain.aggregate.Id.Default<Agg${ entity }, ${ IdentityType } > (key)
}
