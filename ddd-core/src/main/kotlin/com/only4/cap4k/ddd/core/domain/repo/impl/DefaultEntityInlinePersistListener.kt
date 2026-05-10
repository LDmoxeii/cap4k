package com.only4.cap4k.ddd.core.domain.repo.impl

import java.lang.reflect.Method
import java.util.Collections

/**
 * 默认实体内联持久化监听器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultEntityInlinePersistListener : AbstractPersistListener<Any>() {

    companion object {
        val HANDLER_METHOD_CACHE: MutableMap<String, Method?> = Collections.synchronizedMap(mutableMapOf())
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
        return getCachedHandlerMethod(key) {
            try {
                clazz.getMethod(methodName)
            } catch (ex: Exception) {
                // 方法不存在时忽略异常
                null
            }
        }
    }

    private fun getBehaviorHandlerMethod(clazz: Class<*>, methodName: String): Method? {
        return behaviorLookupClasses(clazz)
            .firstNotNullOfOrNull { behaviorTargetClass ->
                val behaviorClassName = behaviorClassName(behaviorTargetClass)
                val key = "behavior:$behaviorClassName.$methodName(${behaviorTargetClass.name})"
                getCachedHandlerMethod(key) {
                    try {
                        Class.forName(behaviorClassName, false, behaviorTargetClass.classLoader)
                            .getMethod(methodName, behaviorTargetClass)
                    } catch (ex: Exception) {
                        // 方法不存在时忽略异常
                        null
                    }
                }
            }
    }

    private fun getCachedHandlerMethod(key: String, lookup: () -> Method?): Method? {
        synchronized(HANDLER_METHOD_CACHE) {
            if (HANDLER_METHOD_CACHE.containsKey(key)) {
                return HANDLER_METHOD_CACHE[key]
            }
            val resolved = lookup()
            HANDLER_METHOD_CACHE[key] = resolved
            return resolved
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

    private fun behaviorLookupClasses(clazz: Class<*>): Sequence<Class<*>> =
        generateSequence(clazz) { it.superclass }
            .takeWhile { it != Any::class.java }
}
