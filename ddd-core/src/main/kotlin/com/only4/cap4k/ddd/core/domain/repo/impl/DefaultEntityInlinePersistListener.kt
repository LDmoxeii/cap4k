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
        if (entity.tryInvokeMemberHandlerMethod("onDelete")) {
            return
        }
        if (entity.tryInvokeBehaviorHandlerMethod("onDelete")) {
            return
        }
        if (entity.tryInvokeMemberHandlerMethod("onRemove")) {
            return
        }
        entity.tryInvokeBehaviorHandlerMethod("onRemove")
    }

    private fun Any.tryInvokeHandlerMethod(methodName: String): Boolean =
        tryInvokeMemberHandlerMethod(methodName) || tryInvokeBehaviorHandlerMethod(methodName)

    private fun Any.tryInvokeMemberHandlerMethod(methodName: String): Boolean {
        val method = getMemberHandlerMethod(this.javaClass, methodName) ?: return false
        method.invoke(this)
        return true
    }

    private fun Any.tryInvokeBehaviorHandlerMethod(methodName: String): Boolean {
        val method = getBehaviorHandlerMethod(this.javaClass, methodName) ?: return false
        method.invoke(null, this)
        return true
    }

    private fun getMemberHandlerMethod(clazz: Class<*>, methodName: String): Method? {
        val key = "member:${clazz.name}.$methodName"
        return HANDLER_METHOD_CACHE.computeIfAbsent(key) {
            try {
                clazz.getMethod(methodName)
            } catch (ex: Exception) {
                // 方法不存在时忽略异常
                null
            }
        }
    }

    private fun getBehaviorHandlerMethod(clazz: Class<*>, methodName: String): Method? {
        val behaviorClassName = behaviorClassName(clazz)
        val key = "behavior:$behaviorClassName.$methodName(${clazz.name})"
        return HANDLER_METHOD_CACHE.computeIfAbsent(key) {
            try {
                Class.forName(behaviorClassName, true, clazz.classLoader).getMethod(methodName, clazz)
            } catch (ex: Exception) {
                // 方法不存在时忽略异常
                null
            }
        }
    }

    private fun behaviorClassName(clazz: Class<*>): String {
        val packageName = clazz.`package`?.name.orEmpty()
        return if (packageName.isBlank()) {
            "${clazz.simpleName}BehaviorKt"
        } else {
            "$packageName.${clazz.simpleName}BehaviorKt"
        }
    }
}
