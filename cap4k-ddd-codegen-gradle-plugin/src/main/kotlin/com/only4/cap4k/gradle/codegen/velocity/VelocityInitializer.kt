package com.only4.cap4k.gradle.codegen.velocity

import org.apache.velocity.app.Velocity
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import java.util.*

/**
 * Velocity 模板引擎初始化器
 *
 * 职责:
 * 1. 单例管理 Velocity 引擎生命周期
 * 2. 延迟初始化(首次使用时才初始化)
 * 3. 线程安全
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
object VelocityInitializer {

    @Volatile
    private var initialized = false

    private lateinit var config: VelocityConfig

    /**
     * 初始化 Velocity 引擎
     *
     * @param config 配置对象(可选,默认使用 DEFAULT)
     */
    @Synchronized
    fun initVelocity(config: VelocityConfig = VelocityConfig.DEFAULT) {
        if (initialized) {
            return
        }

        this.config = config

        val properties = Properties().apply {
            // 资源加载器: 从 classpath 加载 .vm 文件
            setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath")
            setProperty(
                "resource.loader.classpath.class",
                ClasspathResourceLoader::class.java.name
            )

            // 编码设置
            setProperty(RuntimeConstants.INPUT_ENCODING, config.encoding)
            setProperty("output.encoding", config.encoding)

            // 引用模式
            setProperty(
                RuntimeConstants.RUNTIME_REFERENCES_STRICT,
                config.strictReferences.toString()
            )

            // 缓存设置
            setProperty(
                "resource.loader.classpath.cache",
                config.cacheEnabled.toString()
            )

            // 日志: 使用 NullLogChute 避免日志污染
            setProperty(
                RuntimeConstants.RUNTIME_LOG_NAME,
                "org.apache.velocity.runtime.log.NullLogChute"
            )
        }

        Velocity.init(properties)
        initialized = true
    }

    /**
     * 检查引擎是否已初始化
     */
    fun isInitialized(): Boolean = initialized

    /**
     * 获取当前配置
     */
    fun getConfig(): VelocityConfig {
        check(initialized) { "VelocityInitializer not initialized" }
        return config
    }

    /**
     * 重置引擎(主要用于测试)
     */
    @Synchronized
    fun reset() {
        initialized = false
    }
}
