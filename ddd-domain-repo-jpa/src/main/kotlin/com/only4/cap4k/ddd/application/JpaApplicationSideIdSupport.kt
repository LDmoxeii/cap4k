package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

internal class JpaApplicationSideIdSupport(
    private val idStrategyRegistry: IdentifierStrategyRegistry
) {

    // Compatibility path for manually annotated entities; generated Strong IDs are assigned before save.
    fun assignMissingIds(root: Any) {
        assignMissingIds(root, Collections.newSetFromMap(IdentityHashMap()))
    }

    fun assignMissingIdsToOwnedRelations(root: Any) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        visited.add(root)
        ownedRelationValues(root).forEach { related ->
            when (related) {
                is Iterable<*> -> related.filterNotNull().forEach { assignMissingIds(it, visited) }
                else -> assignMissingIds(related, visited)
            }
        }
    }

    fun findApplicationSideId(entity: Any): ApplicationSideIdMember? {
        val entityType = entityType(entity)
        return persistentFields(entityType)
            .firstNotNullOfOrNull { field ->
                val fieldAnnotation = field.getAnnotation(ApplicationSideId::class.java)
                val getter = getter(entityType, field.name)
                val annotation = fieldAnnotation
                    ?: getter?.getAnnotation(ApplicationSideId::class.java)
                    ?: return@firstNotNullOfOrNull null
                validateNoGeneratedValue(field, entity)
                val ownerType = fieldAnnotation?.let { field.declaringClass } ?: getter!!.declaringClass
                ApplicationSideIdMember(field, annotation, ownerType)
            }
    }

    fun isDefaultId(member: ApplicationSideIdMember, entity: Any): Boolean {
        val strategy = idStrategyRegistry.get(member.annotation.strategy)
        requireEntityIdPreassignment(strategy)
        return strategy.isDefaultValue(member.get(entity), member.field.type.kotlin)
    }

    private fun assignMissingIds(entity: Any, visited: MutableSet<Any>) {
        if (!visited.add(entity)) return

        findApplicationSideId(entity)?.let { member ->
            val strategy = idStrategyRegistry.get(member.annotation.strategy)
            requireEntityIdPreassignment(strategy)
            if (strategy.isDefaultValue(member.get(entity), member.field.type.kotlin)) {
                validateOutputType(strategy, member)
                val generated = nextValue(strategy, member)
                check(generated != null) {
                    "ID strategy ${strategy.name} generated null for application-side ID"
                }
                check(!strategy.isDefaultValue(generated, member.field.type.kotlin)) {
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

    private fun requireEntityIdPreassignment(strategy: IdentifierStrategy) {
        require(IdentifierCapability.ENTITY_ID_PREASSIGNMENT in strategy.capabilities) {
            "identifier strategy ${strategy.name} does not support entity ID preassignment"
        }
    }

    private fun validateOutputType(strategy: IdentifierStrategy, member: ApplicationSideIdMember) {
        val fieldType = member.field.type.kotlin
        require(strategy.supports(fieldType)) {
            "identifier strategy ${strategy.name} does not support output type ${member.field.type.name} for field " +
                "${member.field.declaringClass.name}.${member.field.name}"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun nextValue(strategy: IdentifierStrategy, member: ApplicationSideIdMember): Any? {
        val fieldType = member.field.type.kotlin as KClass<Any>
        return strategy.next(fieldType)
    }

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
    val annotation: ApplicationSideId,
    val ownerType: Class<*>,
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
