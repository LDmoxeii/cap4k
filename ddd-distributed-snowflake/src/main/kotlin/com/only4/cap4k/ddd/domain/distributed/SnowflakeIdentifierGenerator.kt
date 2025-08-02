package com.only4.cap4k.ddd.domain.distributed

import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import java.io.Serializable

/**
 * Snowflake Id生成器
 *
 * @author binking338
 * @date 2024/8/11
 */
class SnowflakeIdentifierGenerator : IdentifierGenerator {

    companion object {
        @JvmStatic
        private lateinit var snowflakeIdGeneratorImpl: SnowflakeIdGenerator

        /**
         * 配置雪花ID生成器实现
         */
        @JvmStatic
        fun configure(snowflakeIdGenerator: SnowflakeIdGenerator) {
            snowflakeIdGeneratorImpl = snowflakeIdGenerator
        }
    }

    /**
     * 生成ID
     */
    @Throws(HibernateException::class)
    override fun generate(session: SharedSessionContractImplementor, obj: Any): Serializable {
        return snowflakeIdGeneratorImpl.nextId()
    }
}
