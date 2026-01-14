package com.only4.cap4k.ddd.domain.repo

import com.github.f4b6a3.uuid.UuidCreator
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Id
import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator
import java.io.Serializable
import java.lang.reflect.AnnotatedElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * UUIDv7 identifier generator.
 *
 * @author LD_moxeii
 * @date 2025/11/24
 */
class UuidV7IdentifierGenerator : IdentifierGenerator {

    @Throws(HibernateException::class)
    override fun generate(session: SharedSessionContractImplementor, entity: Any): Serializable {
        val uuid = UuidCreator.getTimeOrderedEpoch()
        return when (resolveIdType(entity)) {
            String::class.java -> uuid.toString()
            UUID::class.java -> uuid
            else -> uuid
        }
    }

    companion object {
        private val idTypeCache = ConcurrentHashMap<Class<*>, Class<*>>()

        private fun resolveIdType(entity: Any): Class<*>? {
            val entityClass = entity.javaClass
            return idTypeCache[entityClass] ?: findIdType(entityClass)?.also {
                idTypeCache[entityClass] = it
            }
        }

        private fun findIdType(entityClass: Class<*>): Class<*>? {
            var current: Class<*>? = entityClass
            while (current != null && current != Any::class.java) {
                current.declaredFields.firstOrNull { hasIdAnnotation(it) }?.let { return it.type }
                current.declaredMethods.firstOrNull { hasIdAnnotation(it) }?.let { return it.returnType }
                current = current.superclass
            }
            return null
        }

        private fun hasIdAnnotation(element: AnnotatedElement): Boolean {
            return element.isAnnotationPresent(Id::class.java) || element.isAnnotationPresent(EmbeddedId::class.java)
        }
    }
}
