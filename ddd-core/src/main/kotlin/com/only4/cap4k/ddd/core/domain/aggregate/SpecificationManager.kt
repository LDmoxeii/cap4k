package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 实体规格约束管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface SpecificationManager {
    /**
     * 校验实体是否符合规格约束
     *
     * @param entity 待校验的实体
     * @return 校验结果
     */
    fun <Entity> specifyInTransaction(entity: Entity): Specification.Result

    /**
     * 校验实体是否符合规格约束（事务开启前）
     *
     * @param entity 待校验的实体
     * @return 校验结果
     */
    fun <Entity> specifyBeforeTransaction(entity: Entity): Specification.Result
}
