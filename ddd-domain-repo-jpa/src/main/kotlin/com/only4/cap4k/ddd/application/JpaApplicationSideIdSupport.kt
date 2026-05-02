package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.ApplicationSideId
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import java.lang.reflect.Field

internal class JpaApplicationSideIdSupport(
    private val idStrategyRegistry: IdStrategyRegistry
) {

    fun assignMissingIds(root: Any) {
        assignMissingIds(root, linkedSetOf())
    }

    fun findApplicationSideId(entity: Any): ApplicationSideIdMember? =
        persistentFields(Hibernate.getClass(entity))
            .firstNotNullOfOrNull { field ->
                val annotation = field.getAnnotation(ApplicationSideId::class.java)
                    ?: findGetterAnnotation(Hibernate.getClass(entity), field.name)
                    ?: return@firstNotNullOfOrNull null
                validateNoGeneratedValue(field, entity)
                ApplicationSideIdMember(field, annotation)
            }

    fun isDefaultId(member: ApplicationSideIdMember, entity: Any): Boolean {
        val strategy = idStrategyRegistry.get(member.annotation.strategy)
        require(strategy.kind == IdGenerationKind.APPLICATION_SIDE) {
            "ID strategy ${strategy.name} is not application-side"
        }
        return strategy.isDefaultValue(member.get(entity))
    }

    private fun assignMissingIds(entity: Any, visited: MutableSet<Int>) {
        if (!visited.add(System.identityHashCode(entity))) return

        findApplicationSideId(entity)?.let { member ->
            val strategy = idStrategyRegistry.get(member.annotation.strategy)
            require(strategy.kind == IdGenerationKind.APPLICATION_SIDE) {
                "ID strategy ${strategy.name} is not application-side"
            }
            if (strategy.isDefaultValue(member.get(entity))) {
                member.set(entity, strategy.next())
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
        persistentFields(Hibernate.getClass(entity)).mapNotNull { field ->
            if (field.getAnnotation(ManyToOne::class.java) != null) return@mapNotNull null
            val oneToMany = field.getAnnotation(OneToMany::class.java)
            val oneToOne = field.getAnnotation(OneToOne::class.java)
            val cascades = oneToMany?.cascade?.toSet() ?: oneToOne?.cascade?.toSet() ?: return@mapNotNull null
            if (CascadeType.ALL !in cascades && CascadeType.PERSIST !in cascades && CascadeType.MERGE !in cascades) {
                return@mapNotNull null
            }
            field.isAccessible = true
            field.get(entity)
        }.toList()

    private fun validateNoGeneratedValue(field: Field, entity: Any) {
        val owner = Hibernate.getClass(entity)
        val getterGeneratedValue = getter(owner, field.name)?.getAnnotation(GeneratedValue::class.java)
        check(field.getAnnotation(GeneratedValue::class.java) == null && getterGeneratedValue == null) {
            "Application-side ID field ${owner.name}.${field.name} must not also use @GeneratedValue"
        }
    }

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
