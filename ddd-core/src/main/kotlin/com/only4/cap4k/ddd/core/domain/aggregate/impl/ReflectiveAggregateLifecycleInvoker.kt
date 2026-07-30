package com.only4.cap4k.ddd.core.domain.aggregate.impl

import com.only4.cap4k.ddd.core.domain.aggregate.AggregateLifecycleInvoker
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Invokes optional aggregate-root callbacks without making empty callback
 * methods part of the generated or compiled aggregate contract.
 */
class ReflectiveAggregateLifecycleInvoker : AggregateLifecycleInvoker {
    private val methodCache = ConcurrentHashMap<MethodKey, MethodResolution>()

    override fun onCreate(root: Any) {
        invokeOptional(root, "onCreate")
    }

    override fun onDeleted(root: Any) {
        invokeOptional(root, "onDeleted")
    }

    private fun invokeOptional(root: Any, methodName: String) {
        val member = resolveMember(root.javaClass, methodName)
        if (member != null) {
            invokeReflectively { member.invoke(root) }
            return
        }
        resolveBehavior(root.javaClass, methodName)?.let { behavior ->
            invokeReflectively { behavior.invoke(null, root) }
        }
    }

    private inline fun invokeReflectively(invocation: () -> Unit) {
        try {
            invocation()
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
    }

    private fun resolveMember(type: Class<*>, methodName: String): Method? =
        methodCache.computeIfAbsent(MethodKey("member", type, methodName)) {
            MethodResolution(runCatching { type.getMethod(methodName) }.getOrNull())
        }.method

    private fun resolveBehavior(type: Class<*>, methodName: String): Method? =
        generateSequence(type) { it.superclass?.takeIf { parent -> parent != Any::class.java } }
            .firstNotNullOfOrNull { targetType ->
                methodCache.computeIfAbsent(MethodKey("behavior", targetType, methodName)) {
                    val packageName = targetType.`package`?.name.orEmpty()
                    val behaviorName = if (packageName.isBlank()) {
                        "${targetType.simpleName}BehaviorKt"
                    } else {
                        "$packageName.${targetType.simpleName}BehaviorKt"
                    }
                    MethodResolution(
                        runCatching {
                            Class.forName(behaviorName, false, targetType.classLoader)
                                .getMethod(methodName, targetType)
                        }.getOrNull(),
                    )
                }.method
            }

    private data class MethodKey(
        val kind: String,
        val type: Class<*>,
        val methodName: String,
    )

    private data class MethodResolution(val method: Method?)
}
