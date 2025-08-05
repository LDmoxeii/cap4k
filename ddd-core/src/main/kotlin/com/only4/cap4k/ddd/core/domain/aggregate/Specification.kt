package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 实体规格约束
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface Specification<in Entity : Any> {
    /**
     * 是否强制在事务开启前执行规格校验
     *
     * @return
     */
    fun beforeTransaction(): Boolean = false

    /**
     * 校验实体是否符合规格约束
     *
     * @param entity
     * @return
     */
    fun specify(entity: Entity): Result

    /**
     * 规格校验结果
     */
    class Result(
        /**
         * 是否通过规格校验
         */
        val passed: Boolean,
        /**
         * 规格校验反馈消息
         */
        val message: String = ""
    ) {
        companion object {
            @JvmStatic
            fun pass(): Result {
                return Result(true)
            }

            @JvmStatic
            fun fail(message: String = ""): Result {
                return Result(false, message)
            }
        }
    }
}
