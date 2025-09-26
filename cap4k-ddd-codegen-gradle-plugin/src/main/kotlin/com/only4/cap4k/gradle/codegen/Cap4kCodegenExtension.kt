package com.only4.cap4k.gradle.codegen

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Cap4k DDD 代码生成插件配置扩展
 */
open class Cap4kCodegenExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * 架构模板文件
     */
    val archTemplate: RegularFileProperty = objects.fileProperty()

    /**
     * 模板文件编码
     */
    val archTemplateEncoding: Property<String> = objects.property(String::class.java).convention("UTF-8")

    /**
     * 输出文件编码
     */
    val outputEncoding: Property<String> = objects.property(String::class.java).convention("UTF-8")

    /**
     * 基础包路径
     */
    val basePackage: Property<String> = objects.property(String::class.java)

    /**
     * 是否为多模块项目
     */
    val multiModule: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * adapter模块名称后缀
     */
    val moduleNameSuffix4Adapter: Property<String> = objects.property(String::class.java).convention("-adapter")

    /**
     * domain模块名称后缀
     */
    val moduleNameSuffix4Domain: Property<String> = objects.property(String::class.java).convention("-domain")

    /**
     * application模块名称后缀
     */
    val moduleNameSuffix4Application: Property<String> = objects.property(String::class.java).convention("-application")

    /**
     * 设计配置文件集合（支持多个文件）
     */
    val designFiles: ConfigurableFileCollection = objects.fileCollection()

    /**
     * 数据库连接配置
     */
    val database: DatabaseConfig = objects.newInstance(DatabaseConfig::class.java, objects)

    /**
     * 代码生成配置
     */
    val generation: GenerationConfig = objects.newInstance(GenerationConfig::class.java, objects)

    /**
     * 配置数据库连接
     */
    fun database(action: Action<DatabaseConfig>) {
        action.execute(database)
    }

    /**
     * 配置代码生成选项
     */
    fun generation(action: Action<GenerationConfig>) {
        action.execute(generation)
    }
}

/**
 * 数据库连接配置
 */
open class DatabaseConfig @Inject constructor(objects: ObjectFactory) {

    /**
     * 数据库连接URL
     */
    val url: Property<String> = objects.property(String::class.java)

    /**
     * 数据库用户名
     */
    val username: Property<String> = objects.property(String::class.java)

    /**
     * 数据库密码
     */
    val password: Property<String> = objects.property(String::class.java)

    /**
     * 数据库Schema
     */
    val schema: Property<String> = objects.property(String::class.java)

    /**
     * 包含的表名模式
     */
    val tables: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 忽略的表名模式
     */
    val ignoreTables: Property<String> = objects.property(String::class.java).convention("")
}

/**
 * 代码生成配置
 */
open class GenerationConfig @Inject constructor(objects: ObjectFactory) {

    /**
     * 乐观锁字段名
     */
    val versionField: Property<String> = objects.property(String::class.java).convention("version")

    /**
     * 软删字段名
     */
    val deletedField: Property<String> = objects.property(String::class.java).convention("deleted")

    /**
     * 只读字段列表
     */
    val readonlyFields: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 忽略字段列表
     */
    val ignoreFields: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 实体基类
     */
    val entityBaseClass: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 根实体基类
     */
    val rootEntityBaseClass: Property<String> = objects.property(String::class.java).convention("")

    val entityClassExtraImports: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 实体Schema输出模式 ref | abs
     */
    val entitySchemaOutputMode: Property<String> = objects.property(String::class.java).convention("abs")

    /**
     * 实体Schema输出包
     */
    val entitySchemaOutputPackage: Property<String> =
        objects.property(String::class.java).convention("domain._share.meta")

    /**
     * 实体Schema类名模板
     */
    val entitySchemaNameTemplate: Property<String> = objects.property(String::class.java).convention("S${'$'}{Entity}")

    /**
     * 关联实体加载模式 LAZY | EAGER
     */
    val fetchType: Property<String> = objects.property(String::class.java).convention("EAGER")

    /**
     * 主键生成器
     */
    val idGenerator: Property<String> = objects.property(String::class.java)
        .convention("com.only4.cap4k.ddd.domain.distributed.SnowflakeIdentifierGenerator")

    /**
     * 值对象主键生成器
     */
    val idGenerator4ValueObject: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 哈希值计算方法
     */
    val hashMethod4ValueObject: Property<String> = objects.property(String::class.java).convention("")

    /**
     * 枚举值字段名
     */
    val enumValueField: Property<String> = objects.property(String::class.java).convention("code")

    /**
     * 枚举名字段名
     */
    val enumNameField: Property<String> = objects.property(String::class.java).convention("desc")

    /**
     * 枚举值转换不匹配时是否抛出异常
     */
    val enumUnmatchedThrowException: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * 日期类型映射使用的包
     */
    val datePackage: Property<String> = objects.property(String::class.java).convention("java.time")

    /**
     * 类型映射
     */
    val typeRemapping: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(mutableMapOf())

    /**
     * 是否在注释中包含数据库字段类型
     */
    val generateDbType: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * 是否生成Schema辅助类
     */
    val generateSchema: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * 是否生成聚合封装类
     */
    val generateAggregate: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * 是否生成关联父实体字段
     */
    val generateParent: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /**
     * 仓储名称模板
     */
    val repositoryNameTemplate: Property<String> =
        objects.property(String::class.java).convention("${'$'}{Entity}Repository")

    /**
     * 仓储是否支持Querydsl
     */
    val repositorySupportQuerydsl: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * 聚合名称模板
     */
    val aggregateNameTemplate: Property<String> = objects.property(String::class.java).convention("Agg${'$'}{Entity}")
}
