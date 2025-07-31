package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.application.persistence.ArchivedRequest
import com.only4.cap4k.ddd.application.persistence.ArchivedRequestJpaRepository
import com.only4.cap4k.ddd.application.persistence.Request
import com.only4.cap4k.ddd.application.persistence.RequestJpaRepository
import com.only4.cap4k.ddd.core.application.RequestRecord
import com.only4.cap4k.ddd.core.application.RequestRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 基于Jpa的请求记录仓储实现
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
open class JpaRequestRecordRepository(
    private val requestJpaRepository: RequestJpaRepository,
    private val archivedRequestJpaRepository: ArchivedRequestJpaRepository
) : RequestRecordRepository {

    override fun create(): RequestRecord = RequestRecordImpl()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun save(requestRecord: RequestRecord) {
        val requestRecordImpl = requestRecord as RequestRecordImpl
        val updatedRequest = requestJpaRepository.saveAndFlush(requestRecordImpl.request)
        requestRecordImpl.resume(updatedRequest)
    }

    override fun getById(id: String): RequestRecord {
        val request = requestJpaRepository.findOne { root, _, criteriaBuilder ->
            criteriaBuilder.equal(root.get<String>(Request.F_REQUEST_UUID), id)
        }.orElseThrow { DomainException("RequestRecord not found") }

        return RequestRecordImpl().apply {
            resume(request)
        }
    }

    override fun getByNextTryTime(svcName: String, maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord> {
        val requests = requestJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.or(
                    cb.and(
                        // 【初始状态】
                        cb.equal(root.get<Request.RequestState>(Request.F_REQUEST_STATE), Request.RequestState.INIT),
                        cb.lessThan(root.get(Request.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Request.F_SVC_NAME), svcName)
                    ),
                    cb.and(
                        // 【执行中状态】
                        cb.equal(
                            root.get<Request.RequestState>(Request.F_REQUEST_STATE),
                            Request.RequestState.EXECUTING
                        ),
                        cb.lessThan(root.get(Request.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Request.F_SVC_NAME), svcName)
                    ),
                    cb.and(
                        // 【异常状态】
                        cb.equal(
                            root.get<Request.RequestState>(Request.F_REQUEST_STATE),
                            Request.RequestState.EXCEPTION
                        ),
                        cb.lessThan(root.get(Request.F_NEXT_TRY_TIME), maxNextTryTime),
                        cb.equal(root.get<String>(Request.F_SVC_NAME), svcName)
                    )
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Request.F_NEXT_TRY_TIME)))

        return requests.map { request ->
            RequestRecordImpl().apply {
                resume(request)
            }
        }.toList()
    }

    override fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int {
        val requests = requestJpaRepository.findAll({ root, cq, cb ->
            cq.where(
                cb.and(
                    // 【状态】
                    cb.or(
                        cb.equal(root.get<Request.RequestState>(Request.F_REQUEST_STATE), Request.RequestState.CANCEL),
                        cb.equal(root.get<Request.RequestState>(Request.F_REQUEST_STATE), Request.RequestState.EXPIRED),
                        cb.equal(
                            root.get<Request.RequestState>(Request.F_REQUEST_STATE),
                            Request.RequestState.EXHAUSTED
                        ),
                        cb.equal(root.get<Request.RequestState>(Request.F_REQUEST_STATE), Request.RequestState.EXECUTED)
                    ),
                    cb.lessThan(root.get(Request.F_EXPIRE_AT), maxExpireAt),
                    cb.equal(root.get<String>(Request.F_SVC_NAME), svcName)
                )
            )
            null
        }, PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, Request.F_NEXT_TRY_TIME)))

        if (!requests.hasContent()) {
            return 0
        }

        val archivedRequests = requests.map { request ->
            ArchivedRequest().apply {
                archiveFrom(request)
            }
        }.toList()

        migrate(requests.content, archivedRequests)
        return requests.numberOfElements
    }

    @Transactional
    open fun migrate(requests: List<Request>, archivedRequests: List<ArchivedRequest>) {
        archivedRequestJpaRepository.saveAll(archivedRequests)
        requestJpaRepository.deleteAllInBatch(requests)
    }
}
