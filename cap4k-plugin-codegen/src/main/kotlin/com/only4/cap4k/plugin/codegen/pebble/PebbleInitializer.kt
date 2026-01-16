package com.only4.cap4k.plugin.codegen.pebble

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader

object PebbleInitializer {

    @Volatile
    private var initialized = false

    private lateinit var config: PebbleConfig
    private lateinit var engine: PebbleEngine

    @Synchronized
    fun initPebble(config: PebbleConfig = PebbleConfig.DEFAULT) {
        if (initialized) return

        this.config = config

        // 只使用 StringLoader，用于渲染纯字符串模板
        val stringLoader = StringLoader()

        engine = PebbleEngine.Builder()
            .loader(stringLoader)
            .strictVariables(config.strictMode)
            .cacheActive(config.cacheEnabled)
            .autoEscaping(config.autoEscaping)
            .newLineTrimming(config.newLineTrimming)
            .build()

        initialized = true
    }

    @Synchronized
    fun isInitialized() = initialized

    /**
     * 获取全局 PebbleEngine
     */
    fun getEngine(): PebbleEngine {
        check(initialized) { "PebbleInitializer not initialized" }
        return engine
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): PebbleConfig {
        check(initialized) { "PebbleInitializer not initialized" }
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
