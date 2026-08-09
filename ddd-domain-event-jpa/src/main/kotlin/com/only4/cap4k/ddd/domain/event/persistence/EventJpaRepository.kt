package com.only4.cap4k.ddd.domain.event.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/** Event persistence carrier plus private reliable-execution CAS operations. */
interface EventJpaRepository :
    JpaRepository<Event, Long>,
    JpaSpecificationExecutor<Event> {

    @Query(
        """
        select event
          from Event event
         where event.svcName = :serviceName
           and event.expireAt > :now
           and (
                (event.eventState in :readyStates
                    and event.nextTryTime <= :now
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
                or
                (event.eventState = :ownedState
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
           )
         order by event.nextTryTime asc, event.id asc
        """
    )
    fun findClaimCandidates(
        @Param("serviceName") serviceName: String,
        @Param("now") now: LocalDateTime,
        @Param("readyStates") readyStates: Collection<Event.EventState>,
        @Param("ownedState") ownedState: Event.EventState,
        pageable: Pageable,
    ): List<Event>

    @Query(
        """
        select event
          from Event event
         where event.svcName = :serviceName
           and event.expireAt <= :now
           and (
                event.eventState in :readyStates
                or
                (event.eventState = :ownedState
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
           )
         order by event.id asc
        """
    )
    fun findExpiredCandidates(
        @Param("serviceName") serviceName: String,
        @Param("readyStates") readyStates: Collection<Event.EventState>,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<Event>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.eventState = :expiredState,
               event.failureFactsJson = :failureFacts,
               event.terminalizedAt = :now,
               event.deliveryToken = null,
               event.leaseUntil = null,
               event.version = event.version + 1
         where event.id = :recordId
           and event.version = :version
           and event.svcName = :serviceName
           and event.expireAt <= :now
           and (
                event.eventState in :readyStates
                or
                (event.eventState = :ownedState
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
           )
    """
    )
    fun terminalizeExpired(
        @Param("recordId") recordId: Long,
        @Param("version") version: Int,
        @Param("serviceName") serviceName: String,
        @Param("readyStates") readyStates: Collection<Event.EventState>,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("expiredState") expiredState: Event.EventState,
        @Param("failureFacts") failureFacts: String,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.eventState = :exhaustedState,
               event.failureFactsJson = :failureFacts,
               event.terminalizedAt = :now,
               event.deliveryToken = null,
               event.leaseUntil = null,
               event.version = event.version + 1
         where event.id = :recordId
           and event.version = :version
           and event.svcName = :serviceName
           and event.expireAt > :now
           and event.triedTimes >= :retryLimit
           and (
                (event.eventState in :readyStates
                    and event.nextTryTime <= :now
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
                or
                (event.eventState = :ownedState
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
           )
        """
    )
    fun terminalizeExhausted(
        @Param("recordId") recordId: Long,
        @Param("version") version: Int,
        @Param("serviceName") serviceName: String,
        @Param("readyStates") readyStates: Collection<Event.EventState>,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("exhaustedState") exhaustedState: Event.EventState,
        @Param("retryLimit") retryLimit: Int,
        @Param("failureFacts") failureFacts: String,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.eventState = :ownedState,
               event.lastTryTime = :now,
               event.nextTryTime = :nextTryTime,
               event.triedTimes = event.triedTimes + 1,
               event.deliveryToken = :token,
               event.leaseUntil = :leaseUntil,
               event.version = event.version + 1
         where event.id = :recordId
           and event.svcName = :serviceName
           and event.expireAt > :now
           and event.triedTimes < :retryLimit
           and (
                (event.eventState in :readyStates
                    and event.nextTryTime <= :now
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
                or
                (event.eventState = :ownedState
                    and (event.leaseUntil is null or event.leaseUntil <= :now))
           )
        """
    )
    fun claim(
        @Param("recordId") recordId: Long,
        @Param("serviceName") serviceName: String,
        @Param("readyStates") readyStates: Collection<Event.EventState>,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("now") now: LocalDateTime,
        @Param("nextTryTime") nextTryTime: LocalDateTime,
        @Param("token") token: ByteArray,
        @Param("leaseUntil") leaseUntil: LocalDateTime,
        @Param("retryLimit") retryLimit: Int,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.leaseUntil = :leaseUntil,
               event.version = event.version + 1
         where event.id = :recordId
           and event.deliveryToken = :token
           and event.leaseUntil > :now
           and event.leaseUntil < :leaseUntil
           and event.eventState = :ownedState
        """
    )
    fun renew(
        @Param("recordId") recordId: Long,
        @Param("token") token: ByteArray,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("now") now: LocalDateTime,
        @Param("leaseUntil") leaseUntil: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.eventState = :successState,
               event.terminalizedAt = :now,
               event.deliveryToken = null,
               event.leaseUntil = null,
               event.version = event.version + 1
         where event.id = :recordId
           and event.deliveryToken = :token
           and event.leaseUntil > :now
           and event.eventState = :ownedState
        """
    )
    fun acknowledge(
        @Param("recordId") recordId: Long,
        @Param("token") token: ByteArray,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("successState") successState: Event.EventState,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.eventState = :failureState,
               event.failureFactsJson = :failureFacts,
               event.nextTryTime = :nextTryTime,
               event.terminalizedAt = :terminalizedAt,
               event.deliveryToken = null,
               event.leaseUntil = null,
               event.version = event.version + 1
         where event.id = :recordId
           and event.deliveryToken = :token
           and event.leaseUntil > :now
           and event.eventState = :ownedState
        """
    )
    fun transitionFailure(
        @Param("recordId") recordId: Long,
        @Param("token") token: ByteArray,
        @Param("ownedState") ownedState: Event.EventState,
        @Param("failureState") failureState: Event.EventState,
        @Param("failureFacts") failureFacts: String,
        @Param("nextTryTime") nextTryTime: LocalDateTime,
        @Param("terminalizedAt") terminalizedAt: LocalDateTime? = null,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update Event event
           set event.eventState = :readyState,
               event.lastTryTime = :now,
               event.nextTryTime = :now,
               event.triedTimes = 0,
               event.deliveryToken = null,
               event.leaseUntil = null,
               event.redriveRequestToken = :requestToken,
               event.terminalizedAt = null,
               event.version = event.version + 1
         where event.id = :recordId
           and event.version = :version
           and event.svcName = :serviceName
           and event.eventState = :expectedState
           and event.expireAt > :now
           and (event.leaseUntil is null or event.leaseUntil <= :now)
        """
    )
    fun redrive(
        @Param("recordId") recordId: Long,
        @Param("version") version: Int,
        @Param("serviceName") serviceName: String,
        @Param("expectedState") expectedState: Event.EventState,
        @Param("readyState") readyState: Event.EventState,
        @Param("now") now: LocalDateTime,
        @Param("requestToken") requestToken: String,
    ): Int

    /** Select a bounded retention batch without exposing event payloads. */
    @Query(
        """
        select event.id
          from Event event
         where event.svcName = :serviceName
           and event.leaseUntil is null
           and (
                (event.eventState = :successState
                    and event.terminalizedAt <= :successfulCutoff)
                or
                (event.eventState = :exhaustedState
                    and event.expireAt <= :now
                    and event.terminalizedAt <= :exhaustedCutoff)
                or
                (event.eventState = :expiredState
                    and event.terminalizedAt <= :expiredCutoff)
           )
         order by event.terminalizedAt asc, event.id asc
        """
    )
    fun findRetentionCandidateIds(
        @Param("serviceName") serviceName: String,
        @Param("successState") successState: Event.EventState,
        @Param("exhaustedState") exhaustedState: Event.EventState,
        @Param("expiredState") expiredState: Event.EventState,
        @Param("successfulCutoff") successfulCutoff: LocalDateTime,
        @Param("exhaustedCutoff") exhaustedCutoff: LocalDateTime,
        @Param("expiredCutoff") expiredCutoff: LocalDateTime,
        @Param("now") now: LocalDateTime,
        pageable: Pageable,
    ): List<Long>

    /** Delete only the still-eligible ids from a previously selected bounded batch. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        delete from Event event
         where event.id in :recordIds
           and event.svcName = :serviceName
           and event.leaseUntil is null
           and (
                (event.eventState = :successState
                    and event.terminalizedAt <= :successfulCutoff)
                or
                (event.eventState = :exhaustedState
                    and event.expireAt <= :now
                    and event.terminalizedAt <= :exhaustedCutoff)
                or
                (event.eventState = :expiredState
                    and event.terminalizedAt <= :expiredCutoff)
           )
        """
    )
    fun deleteRetentionCandidates(
        @Param("recordIds") recordIds: Collection<Long>,
        @Param("serviceName") serviceName: String,
        @Param("successState") successState: Event.EventState,
        @Param("exhaustedState") exhaustedState: Event.EventState,
        @Param("expiredState") expiredState: Event.EventState,
        @Param("successfulCutoff") successfulCutoff: LocalDateTime,
        @Param("exhaustedCutoff") exhaustedCutoff: LocalDateTime,
        @Param("expiredCutoff") expiredCutoff: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Int
}
