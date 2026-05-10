package com.only4.cap4k.ddd.core.domain.repo.impl

import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections

/**
 * 默认实体内联持久化监听器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultEntityInlinePersistListener : AbstractPersistListener<Any>() {

    companion object {
        val HANDLER_METHOD_CACHE: MutableMap<HandlerMethodCacheKey, WeakReference<Method>?> =
            Collections.synchronizedMap(mutableMapOf())
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
        val key = HandlerMethodCacheKey(
            kind = "member",
            targetClassName = clazz.name,
            targetClassIdentity = System.identityHashCode(clazz),
            targetClassLoaderIdentity = System.identityHashCode(clazz.classLoader),
            methodName = methodName,
        )
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
                val key = HandlerMethodCacheKey(
                    kind = "behavior",
                    targetClassName = behaviorTargetClass.name,
                    targetClassIdentity = System.identityHashCode(behaviorTargetClass),
                    targetClassLoaderIdentity = System.identityHashCode(behaviorTargetClass.classLoader),
                    methodName = methodName,
                    behaviorClassName = behaviorClassName,
                )
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

    private fun getCachedHandlerMethod(key: HandlerMethodCacheKey, lookup: () -> Method?): Method? {
        synchronized(HANDLER_METHOD_CACHE) {
            if (HANDLER_METHOD_CACHE.containsKey(key)) {
                val cached = HANDLER_METHOD_CACHE[key]
                val method = cached?.get()
                if (cached == null || method != null) {
                    return method
                }
            }
            val resolved = lookup()
            HANDLER_METHOD_CACHE[key] = resolved?.let(::WeakReference)
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

    data class HandlerMethodCacheKey(
        val kind: String,
        val targetClassName: String,
        val targetClassIdentity: Int,
        val targetClassLoaderIdentity: Int,
        val methodName: String,
        val behaviorClassName: String? = null,
    )
}
