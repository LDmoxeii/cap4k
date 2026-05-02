package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.jvm.javaObjectType

internal class JpaApplicationSideIdSupport(
    private val idStrategyRegistry: IdStrategyRegistry
) {

    fun assignMissingIds(root: Any) {
        assignMissingIds(root, Collections.newSetFromMap(IdentityHashMap()))
    }

    fun findApplicationSideId(entity: Any): ApplicationSideIdMember? =
        persistentFields(entityType(entity))
            .firstNotNullOfOrNull { field ->
                val annotation = field.getAnnotation(ApplicationSideId::class.java)
                    ?: findGetterAnnotation(entityType(entity), field.name)
                    ?: return@firstNotNullOfOrNull null
                validateNoGeneratedValue(field, entity)
                ApplicationSideIdMember(field, annotation)
            }

    fun isDefaultId(member: ApplicationSideIdMember, entity: Any): Boolean {
        val strategy = idStrategyRegistry.get(member.annotation.strategy)
        requireApplicationSide(strategy)
        return strategy.isDefaultValue(member.get(entity))
    }

    private fun assignMissingIds(entity: Any, visited: MutableSet<Any>) {
        if (!visited.add(entity)) return

        findApplicationSideId(entity)?.let { member ->
            val strategy = idStrategyRegistry.get(member.annotation.strategy)
            requireApplicationSide(strategy)
            if (strategy.isDefaultValue(member.get(entity))) {
                validateOutputType(strategy, member)
                val generated = nextValue(strategy)
                check(generated != null) {
                    "ID strategy ${strategy.name} generated null for application-side ID"
                }
                check(!strategy.isDefaultValue(generated)) {
                    "ID strategy ${strategy.name} generated a default application-side ID value"
                }
                member.set(entity, generated)
            }
        }

        ownedRelationValues(entity).forEach { related ->
            when (related) {
                is Iterable<*> -> related.filterNotNull().forEach { assignMissingIds(it, visited) }
                else -> assignMissingIds(related, visited)
            }
        }
    }

    private fun ownedRelationValues(entity: Any): List<Any> =
        persistentFields(entityType(entity)).mapNotNull { field ->
            if (field.getAnnotation(ManyToOne::class.java) != null) return@mapNotNull null
            val oneToMany = field.getAnnotation(OneToMany::class.java)
            val oneToOne = field.getAnnotation(OneToOne::class.java)
            if (oneToMany?.mappedBy?.isNotBlank() == true || oneToOne?.mappedBy?.isNotBlank() == true) {
                return@mapNotNull null
            }
            val cascades = oneToMany?.cascade?.toSet() ?: oneToOne?.cascade?.toSet() ?: return@mapNotNull null
            if (CascadeType.ALL !in cascades && CascadeType.PERSIST !in cascades && CascadeType.MERGE !in cascades) {
                return@mapNotNull null
            }
            field.isAccessible = true
            field.get(entity)?.takeIf(Hibernate::isInitialized)
        }.toList()

    private fun validateNoGeneratedValue(field: Field, entity: Any) {
        val owner = entityType(entity)
        val getterGeneratedValue = getter(owner, field.name)?.getAnnotation(GeneratedValue::class.java)
        check(field.getAnnotation(GeneratedValue::class.java) == null && getterGeneratedValue == null) {
            "Application-side ID field ${owner.name}.${field.name} must not also use @GeneratedValue"
        }
    }

    private fun requireApplicationSide(strategy: IdStrategy) {
        require(strategy.kind == IdGenerationKind.APPLICATION_SIDE) {
            "ID strategy ${strategy.name} is not application-side"
        }
    }

    private fun validateOutputType(strategy: IdStrategy, member: ApplicationSideIdMember) {
        val outputType = strategy.outputType.javaObjectType
        val fieldType = member.field.type.kotlin.javaObjectType
        require(fieldType.isAssignableFrom(outputType)) {
            "ID strategy ${strategy.name} output type ${outputType.name} cannot be assigned to field " +
                "${member.field.declaringClass.name}.${member.field.name} of type ${member.field.type.name}"
        }
    }

    private fun nextValue(strategy: IdStrategy): Any? =
        IdStrategy::class.java.getMethod("next").invoke(strategy)

    private fun findGetterAnnotation(type: Class<*>, fieldName: String): ApplicationSideId? =
        getter(type, fieldName)?.getAnnotation(ApplicationSideId::class.java)

    private fun getter(type: Class<*>, fieldName: String) =
        type.methods.firstOrNull {
            it.name == "get${fieldName.replaceFirstChar(Char::uppercaseChar)}" && it.parameterCount == 0
        }

    private fun persistentFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { current ->
            current.superclass?.takeIf { it != Any::class.java }
        }.flatMap { current -> current.declaredFields.asSequence() }

    private fun entityType(entity: Any): Class<*> =
        Hibernate.getClassLazy(entity)
}

internal data class ApplicationSideIdMember(
    val field: Field,
    val annotation: ApplicationSideId
) {
    fun get(entity: Any): Any? {
        field.isAccessible = true
        return field.get(entity)
    }

    fun set(entity: Any, value: Any) {
        field.isAccessible = true
        field.set(entity, value)
    }
}
