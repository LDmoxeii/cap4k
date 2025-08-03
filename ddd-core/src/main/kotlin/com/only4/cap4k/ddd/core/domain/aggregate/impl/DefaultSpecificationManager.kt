package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.aggregate.Specification
import com.only4.cap4k.ddd.core.domain.aggregate.SpecificationManager
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils

/**
 * 默认实体规约管理器
 *
 * @author LD_moxeii
 * @date 2025/07/23
 */
class DefaultSpecificationManager(
    private val specifications: List<Specification<*>>
) : SpecificationManager {

    private val specificationMap: Map<Class<*>, List<Specification<*>>> by lazy {
        specifications
            .sortedBy { OrderUtils.getOrder(it::class.java, Ordered.LOWEST_PRECEDENCE) }
            .groupBy { specification ->
                resolveGenericTypeClass(specification, 0, Specification::class.java)
            }
    }

    fun init() {
        specificationMap
    }

    override fun <Entity : Any> specifyBeforeTransaction(entity: Entity): Specification.Result {
        val specifications = specificationMap[entity::class.java] ?: return Specification.Result.pass()

        return specifications
            .filter { it.beforeTransaction() }
            .asSequence()
            .map {
                @Suppress("UNCHECKED_CAST")
                (it as Specification<Entity>).specify(entity)
            }
            .firstOrNull { !it.passed }
            ?: Specification.Result.pass()
    }

    override fun <Entity : Any> specifyInTransaction(entity: Entity): Specification.Result {
        val specifications = specificationMap[entity::class.java] ?: return Specification.Result.pass()

        return specifications
            .filterNot { it.beforeTransaction() }
            .asSequence()
            .map {
                @Suppress("UNCHECKED_CAST")
                (it as Specification<Entity>).specify(entity)
            }
            .firstOrNull { !it.passed }
            ?: Specification.Result.pass()
    }
}
