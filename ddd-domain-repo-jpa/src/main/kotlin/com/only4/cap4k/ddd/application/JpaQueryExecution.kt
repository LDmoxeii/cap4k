package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.query.QueryExecution
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.FlushMode
import org.hibernate.Session
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy

/** Hibernate-backed Handler-wide read-only Query boundary. */
open class JpaQueryExecution : QueryExecution {
    @PersistenceContext
    lateinit var entityManager: EntityManager

    private val activeDepth = ThreadLocal<Int>()
    @Autowired
    @Lazy
    private lateinit var self: JpaQueryExecution

    override val active: Boolean
        get() = (activeDepth.get() ?: 0) > 0

    override fun <RESULT> execute(block: () -> RESULT): RESULT {
        if (active) return withDepth(block)
        return self.requiredReadOnly(block)
    }

    @Transactional(readOnly = true, rollbackFor = [Exception::class])
    open fun <RESULT> requiredReadOnly(block: () -> RESULT): RESULT {
        check(!active) { "Only the outer QueryExecution may create a read transaction" }
        val session = entityManager.unwrap(Session::class.java)
        val previousFlushMode = session.hibernateFlushMode
        val previousReadOnly = session.isDefaultReadOnly
        session.hibernateFlushMode = FlushMode.MANUAL
        session.isDefaultReadOnly = true
        return try {
            withDepth(block)
        } finally {
            session.isDefaultReadOnly = previousReadOnly
            session.hibernateFlushMode = previousFlushMode
        }
    }

    private fun <RESULT> withDepth(block: () -> RESULT): RESULT {
        val previous = activeDepth.get() ?: 0
        activeDepth.set(previous + 1)
        return try {
            block()
        } finally {
            if (previous == 0) activeDepth.remove() else activeDepth.set(previous)
        }
    }
}
