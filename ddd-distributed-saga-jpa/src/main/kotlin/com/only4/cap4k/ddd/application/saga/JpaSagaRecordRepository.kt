package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSaga
import com.only4.cap4k.ddd.application.saga.persistence.ArchivedSagaJpaRepository
import com.only4.cap4k.ddd.application.saga.persistence.Saga
import com.only4.cap4k.ddd.application.saga.persistence.SagaJpaRepository
import com.only4.cap4k.ddd.core.application.saga.SagaRecord
import com.only4.cap4k.ddd.core.application.saga.SagaRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 基于JPA的Saga记录仓储实现
 *
 * @author binking338
 * @date 2024/10/12
 */
open class JpaSagaRecordRepository(
    private val sagaJpaRepository: SagaJpaRepository,
    private val archivedSagaJpaRepository: ArchivedSagaJpaRepository
) : SagaRecordRepository {

    override fun create(): SagaRecord = SagaRecordImpl()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(sagaRecord: SagaRecord) {
        val record = sagaRecord as SagaRecordImpl
        val saga = sagaJpaRepository.saveAndFlush(record.saga)
        record.resume(saga)
    }

    override fun getById(id: String): SagaRecord {
        val saga = sagaJpaRepository.findOne { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<String>(Saga.F_SAGA_UUID), id)
        }.orElseThrow { DomainException("SagaRecord not found") }

        return SagaRecordImpl().apply {
            resume(saga)
        }
    }

    override fun getByNextTryTime(svcName: String, maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord> {
        val sagas = sagaJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.or(
                    cb.and(
                        // 【初始状态】
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.INIT),
                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Saga.F_SVC_NAME), svcName)
                    ),
                    cb.and(
                        // 【执行中状态】
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXECUTING),
                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Saga.F_SVC_NAME), svcName)
                    ),
                    cb.and(
                        // 【异常状态】
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXCEPTION),
                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Saga.F_SVC_NAME), svcName)
                    )
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Saga.F_NEXT_TRY_TIME)))

        return sagas.map { saga ->
            SagaRecordImpl().apply {
                resume(saga)
            }
        }.toList()
    }

    override fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int {
        val sagas = sagaJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.and(
                    // 【状态】
                    cb.or(
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.CANCEL),
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXPIRED),
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXHAUSTED),
                        cb.equal(root.get<Saga.SagaState>(Saga.F_SAGA_STATE), Saga.SagaState.EXECUTED)
                    ),
                    cb.lessThan(root.get(Saga.F_EXPIRE_AT), maxExpireAt),
                    cb.equal(root.get<String>(Saga.F_SVC_NAME), svcName)
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Saga.F_NEXT_TRY_TIME)))

        if (!sagas.hasContent()) {
            return 0
        }

        val archivedSagas = sagas.map { saga ->
            ArchivedSaga().apply {
                archiveFrom(saga)
            }
        }.toList()

        migrate(sagas.content, archivedSagas)
        return sagas.numberOfElements
    }

    @Transactional
    open fun migrate(sagas: List<Saga>, archivedSagas: List<ArchivedSaga>) {
        archivedSagaJpaRepository.saveAll(archivedSagas)
        sagaJpaRepository.deleteAllInBatch(sagas)
    }
}
