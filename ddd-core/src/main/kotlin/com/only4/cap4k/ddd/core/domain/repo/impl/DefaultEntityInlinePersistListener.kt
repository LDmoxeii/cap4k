package com.only4.cap4k.ddd.core.domain.repo.impl

import java.lang.ref.ReferenceQueue
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
        private val HANDLER_METHOD_CACHE_REFERENCE_QUEUE = ReferenceQueue<Class<*>>()

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
            targetClass = clazz,
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
                    targetClass = behaviorTargetClass,
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
            purgeStaleCacheEntries()
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

    private fun purgeStaleCacheEntries() {
        while (true) {
            val reference = HANDLER_METHOD_CACHE_REFERENCE_QUEUE.poll() as? HandlerMethodCacheTargetClassReference
                ?: return
            HANDLER_METHOD_CACHE.remove(reference.key)
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

    class HandlerMethodCacheKey(
        val kind: String,
        targetClass: Class<*>,
        val methodName: String,
        val behaviorClassName: String? = null,
    ) {
        val targetClassName: String = targetClass.name
        private val targetClassIdentity: Int = System.identityHashCode(targetClass)
        private val targetClassReference = HandlerMethodCacheTargetClassReference(
            targetClass,
            HANDLER_METHOD_CACHE_REFERENCE_QUEUE,
            this,
        )

        fun matchesTargetClass(targetClass: Class<*>): Boolean =
            targetClassReference.get() === targetClass

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is HandlerMethodCacheKey) {
                return false
            }
            val targetClass = targetClassReference.get() ?: return false
            val otherTargetClass = other.targetClassReference.get() ?: return false
            return kind == other.kind &&
                targetClass === otherTargetClass &&
                methodName == other.methodName &&
                behaviorClassName == other.behaviorClassName
        }

        override fun hashCode(): Int {
            var result = kind.hashCode()
            result = 31 * result + targetClassIdentity
            result = 31 * result + methodName.hashCode()
            result = 31 * result + (behaviorClassName?.hashCode() ?: 0)
            return result
        }
    }

    private class HandlerMethodCacheTargetClassReference(
        targetClass: Class<*>,
        referenceQueue: ReferenceQueue<Class<*>>,
        val key: HandlerMethodCacheKey,
    ) : WeakReference<Class<*>>(targetClass, referenceQueue)
}
