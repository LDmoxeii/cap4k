package com.only4.core.domain.aggregate

import com.only4.core.share.misc.ClassUtils
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * 实体规格约束管理器
 *
 * @author binking338
 * @date 2023/8/5
 */
interface SpecificationManager {
    /**
     * 校验实体是否符合规格约束
     *
     * @param entity 待校验的实体
     * @return 校验结果
     */
    fun <Entity : Any> specifyInTransaction(entity: Entity): Specification.Result

    /**
     * 校验实体是否符合规格约束（事务开启前）
     *
     * @param entity 待校验的实体
     * @return 校验结果
     */
    fun <Entity : Any> specifyBeforeTransaction(entity: Entity): Specification.Result
}

/**
 * 默认实体规格约束管理器实现
 * 负责管理和执行实体的规格约束校验
 *
 * @author binking338
 * @date 2023/8/13
 */
class DefaultSpecificationManager(
    private val specifications: List<Specification<*>>
) : SpecificationManager {

    /**
     * 规格约束映射
     * 使用lazy委托属性实现线程安全的延迟初始化
     * 按照Order注解排序并建立实体类型到规格约束的映射关系
     */
    private val specificationMap by lazy {
        ConcurrentHashMap<Class<*>, List<Specification<*>>>().apply {
            // 按Order注解排序
            specifications.sortedBy { spec ->
                OrderUtils.getOrder(spec.javaClass, Ordered.LOWEST_PRECEDENCE)
            }.forEach { spec ->
                // 解析实体类型
                val entityClass = ClassUtils.resolveGenericTypeClass(
                    spec, 0,
                    Specification::class.java
                )
                // 获取或创建规格约束列表
                val specList = getOrPut(entityClass) { mutableListOf() }
                (specList as MutableList).add(spec)
            }
        }
    }

    override fun <Entity : Any> specifyInTransaction(entity: Entity): Specification.Result {
        return specificationMap[entity.javaClass]?.let { specs ->
            for (spec in specs) {
                if (spec.beforeTransaction()) continue
                val result = (spec as Specification<Entity>).specify(entity)
                if (!result.passed) return result
            }
            Specification.Result.pass()
        } ?: Specification.Result.pass()
    }

    override fun <Entity : Any> specifyBeforeTransaction(entity: Entity): Specification.Result {
        return specificationMap[entity.javaClass]?.let { specs ->
            for (spec in specs) {
                if (!spec.beforeTransaction()) continue
                val result = (spec as Specification<Entity>).specify(entity)
                if (!result.passed) return result
            }
            Specification.Result.pass()
        } ?: Specification.Result.pass()
    }
}
