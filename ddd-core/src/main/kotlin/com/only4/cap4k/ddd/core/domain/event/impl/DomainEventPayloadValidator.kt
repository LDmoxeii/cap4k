package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.share.DomainException
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Best-effort guard for the historical-fact boundary.
 *
 * Domain Events may contain immutable snapshots and Value Objects, but must
 * never retain a live persistent Entity/Aggregate reference. Persistence
 * annotations are detected by name so the core contract stays ORM-neutral.
 */
internal object DomainEventPayloadValidator {
    private val entityAnnotationNames = setOf(
        "jakarta.persistence.Entity",
        "javax.persistence.Entity",
    )

    fun validate(payload: Any) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        visit(payload, "payload", visited)
    }

    private fun visit(value: Any?, path: String, visited: MutableSet<Any>) {
        if (value == null) return
        val type = value.javaClass
        if (isPersistentEntity(type)) {
            throw DomainException(
                "Domain Event payload must contain an immutable fact snapshot; " +
                    "persistent Entity reference found at $path (${type.name})"
            )
        }
        if (isTerminal(type) || !visited.add(value)) return

        when (value) {
            is Map<*, *> -> value.entries.forEachIndexed { index, entry ->
                visit(entry.key, "$path.keys[$index]", visited)
                visit(entry.value, "$path.values[$index]", visited)
            }

            is Iterable<*> -> value.forEachIndexed { index, item ->
                visit(item, "$path[$index]", visited)
            }

            else -> if (type.isArray) {
                repeat(java.lang.reflect.Array.getLength(value)) { index ->
                    visit(java.lang.reflect.Array.get(value, index), "$path[$index]", visited)
                }
            } else {
                fieldsOf(type).forEach { field ->
                    if (field.trySetAccessible()) {
                        visit(field.get(value), "$path.${field.name}", visited)
                    }
                }
            }
        }
    }

    private fun isPersistentEntity(type: Class<*>): Boolean =
        generateSequence(type) { current -> current.superclass }
            .any { current ->
                current.name == "org.springframework.data.domain.AbstractAggregateRoot" ||
                    current.annotations.any { annotation ->
                        annotation.annotationClass.java.name in entityAnnotationNames
                    }
            }

    private fun isTerminal(type: Class<*>): Boolean =
        type.isPrimitive ||
            type.isEnum ||
            type == String::class.java ||
            Number::class.java.isAssignableFrom(type) ||
            type == java.lang.Boolean::class.java ||
            type == java.lang.Character::class.java ||
            type.name.startsWith("java.time.") ||
            type.name.startsWith("java.math.") ||
            type.name.startsWith("java.util.UUID") ||
            type.name.startsWith("kotlin.time.")

    private fun fieldsOf(type: Class<*>): Sequence<java.lang.reflect.Field> =
        generateSequence(type) { current -> current.superclass }
            .takeWhile { current -> current != Any::class.java }
            .flatMap { current -> current.declaredFields.asSequence() }
            .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
}
