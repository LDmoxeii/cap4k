package com.only4.cap4k.ddd.core.domain.repo.impl

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认实体内联持久化监听器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultEntityInlinePersistListener : AbstractPersistListener<Any>() {

    companion object {
        val HANDLER_METHOD_CACHE: MutableMap<String, Method?> = ConcurrentHashMap()
    }

    override fun onCreate(entity: Any) {
        entity.tryInvokeHandlerMethod("onCreate")
    }

    override fun onUpdate(entity: Any) {
        entity.tryInvokeHandlerMethod("onUpdate")
    }

    override fun onDelete(entity: Any) {
        // 优先尝试onDelete方法，如果不存在再尝试onRemove方法
        if (!entity.tryInvokeHandlerMethod("onDelete")) {
            entity.tryInvokeHandlerMethod("onRemove")
        }
    }

    private fun Any.tryInvokeHandlerMethod(methodName: String): Boolean {
        val method = getHandlerMethod(this.javaClass, methodName) ?: return false
        method.invoke(this)
        return true
    }

    private fun getHandlerMethod(clazz: Class<*>, methodName: String): Method? {
        val key = "${clazz.name}.$methodName"
        return HANDLER_METHOD_CACHE.computeIfAbsent(key) {
            try {
                clazz.getMethod(methodName)
            } catch (ex: Exception) {
                // 方法不存在时忽略异常
                null
            }
        }
    }
}
