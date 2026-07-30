package com.only4.cap4k.ddd.application.command
import com.only4.cap4k.ddd.core.application.command.ReliableCommandTransaction
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** Spring transaction bridge used by the JPA reliable Command provider. */
class JpaReliableCommandTransaction : ReliableCommandTransaction {
    override fun requireActive() {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "Reliable Command registration requires an active physical transaction"
        }
        check(TransactionSynchronizationManager.isSynchronizationActive()) {
            "Reliable Command registration requires active transaction synchronization"
        }
    }

    override fun afterCommit(action: () -> Unit) {
        requireActive()
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = action()
            },
        )
    }
}
