package com.only4.cap4k.ddd.domain.repo.configure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * JpaUnitOfWork配置类
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
@Configuration
@ConfigurationProperties("cap4k.ddd.application.jpa-uow")
class JpaUnitOfWorkProperties(
    /**
     * 单次获取记录数
     */
    var retrieveCountWarnThreshold: Int = 3000,

    /**
     * 是否支持实体内联持久化监听器
     * 创建 onCreate
     * 更新 onUpdate
     * 删除 onDelete | onRemove
     */
    var supportEntityInlinePersistListener: Boolean = true,

    /**
     * 是否在保存时检查值对象是否存在
     */
    var supportValueObjectExistsCheckOnSave: Boolean = true,

    /**
     * 通用主键字段名
     */
    var generalIdFieldName: String = "id"
)
