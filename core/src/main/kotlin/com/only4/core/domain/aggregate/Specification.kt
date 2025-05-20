package com.only4.core.domain.aggregate

/**
 * 实体规格约束
 *
 * @author binking338
 * @date 2023/8/5
 */
interface Specification<Entity> {
    /**
     * 是否强制在事务开启前执行规格校验
     *
     * @return
     */
    fun beforeTransaction(): Boolean {
        return false
    }

    /**
     * 校验实体是否符合规格约束
     *
     * @param entity
     * @return
     */
    fun specify(entity: Entity): Result?

    /**
     * 规格校验结果
     */
    class Result(
        /**
         * 是否通过规格校验
         */
        private val passed: Boolean,
        /**
         * 规格校验反馈消息
         */
        private val message: String?
    ) {
        companion object {
            fun pass(): Result {
                return Result(true, null)
            }

            fun fail(message: String?): Result {
                return Result(false, message)
            }
        }
    }
}
