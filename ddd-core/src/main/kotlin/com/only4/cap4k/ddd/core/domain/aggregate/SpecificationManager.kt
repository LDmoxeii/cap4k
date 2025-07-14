package com.only4.cap4k.ddd.core.domain.aggregate

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
