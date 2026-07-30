package com.only4.cap4k.ddd.application.command
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

class JpaReliableCommandTransactionTest {
    private val transaction = JpaReliableCommandTransaction()

    @AfterEach
    fun clearTransactionState() {
        TransactionSynchronizationManager.clear()
    }

    @Test
    fun `registration requires an actual physical transaction`() {
        TransactionSynchronizationManager.initSynchronization()

        val failure = assertThrows<IllegalStateException> {
            transaction.requireActive()
        }

        assertTrue(failure.message.orEmpty().contains("physical transaction"))
    }

    @Test
    fun `rollback completion does not invoke after commit callback`() {
        beginTransaction()
        var invoked = false
        transaction.afterCommit { invoked = true }

        TransactionSynchronizationManager.getSynchronizations().forEach {
            it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        assertFalse(invoked)
    }

    @Test
    fun `commit invokes callback only in after commit phase`() {
        beginTransaction()
        var invoked = false
        transaction.afterCommit { invoked = true }
        val synchronizations = TransactionSynchronizationManager.getSynchronizations()

        synchronizations.forEach { it.beforeCommit(false) }
        synchronizations.forEach { it.beforeCompletion() }
        assertFalse(invoked)

        synchronizations.forEach { it.afterCommit() }
        assertTrue(invoked)
    }

    private fun beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
    }
}
