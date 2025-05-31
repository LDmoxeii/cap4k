package com.only4.core.domain.aggregate

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
     * @param entity
     * @param <Entity>
     * @return
    </Entity> */
    fun <Entity : Any> specifyInTransaction(entity: Entity): Specification.Result

    /**
     * 校验实体是否符合规格约束（事务开启前）
     *
     * @param entity
     * @param <Entity>
     * @return
    </Entity> */
    fun <Entity : Any> specifyBeforeTransaction(entity: Entity): Specification.Result
}
