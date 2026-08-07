package com.only4.cap4k.ddd.application.command.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/** Command persistence carrier plus private reliable-execution CAS operations. */
interface CommandRecordJpaRepository :
    JpaRepository<CommandRecordEntity, Long>,
    JpaSpecificationExecutor<CommandRecordEntity> {

    @Query(
        """
        select command
          from CommandRecordEntity command
         where command.svcName = :serviceName
           and command.expireAt > :now
           and (
                (command.commandState in :readyStates
                    and command.nextTryTime <= :now
                    and (command.leaseUntil is null or command.leaseUntil <= :now))
                or
                (command.commandState = :ownedState
                    and (command.leaseUntil is null or command.leaseUntil <= :now))
           )
         order by command.nextTryTime asc, command.id asc
        """
    )
    fun findClaimCandidates(
        @Param("serviceName") serviceName: String,
        @Param("now") now: LocalDateTime,
        @Param("readyStates") readyStates: Collection<CommandRecordEntity.CommandState>,
        @Param("ownedState") ownedState: CommandRecordEntity.CommandState,
        pageable: Pageable,
    ): List<CommandRecordEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CommandRecordEntity command
           set command.commandState = :ownedState,
               command.lastTryTime = :now,
               command.nextTryTime = :nextTryTime,
               command.triedTimes = command.triedTimes + 1,
               command.deliveryToken = :token,
               command.leaseUntil = :leaseUntil,
               command.version = command.version + 1
         where command.id = :recordId
           and command.svcName = :serviceName
           and command.expireAt > :now
           and (
                (command.commandState in :readyStates
                    and command.nextTryTime <= :now
                    and (command.leaseUntil is null or command.leaseUntil <= :now))
                or
                (command.commandState = :ownedState
                    and (command.leaseUntil is null or command.leaseUntil <= :now))
           )
        """
    )
    fun claim(
        @Param("recordId") recordId: Long,
        @Param("serviceName") serviceName: String,
        @Param("readyStates") readyStates: Collection<CommandRecordEntity.CommandState>,
        @Param("ownedState") ownedState: CommandRecordEntity.CommandState,
        @Param("now") now: LocalDateTime,
        @Param("nextTryTime") nextTryTime: LocalDateTime,
        @Param("token") token: String,
        @Param("leaseUntil") leaseUntil: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CommandRecordEntity command
           set command.leaseUntil = :leaseUntil,
               command.version = command.version + 1
         where command.id = :recordId
           and command.deliveryToken = :token
           and command.leaseUntil > :now
           and command.leaseUntil < :leaseUntil
           and command.expireAt > :now
           and command.commandState = :ownedState
        """
    )
    fun renew(
        @Param("recordId") recordId: Long,
        @Param("token") token: String,
        @Param("ownedState") ownedState: CommandRecordEntity.CommandState,
        @Param("now") now: LocalDateTime,
        @Param("leaseUntil") leaseUntil: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CommandRecordEntity command
           set command.commandState = :successState,
               command.deliveryToken = null,
               command.leaseUntil = null,
               command.version = command.version + 1
         where command.id = :recordId
           and command.deliveryToken = :token
           and command.leaseUntil > :now
           and command.expireAt > :now
           and command.commandState = :ownedState
        """
    )
    fun acknowledge(
        @Param("recordId") recordId: Long,
        @Param("token") token: String,
        @Param("ownedState") ownedState: CommandRecordEntity.CommandState,
        @Param("successState") successState: CommandRecordEntity.CommandState,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CommandRecordEntity command
           set command.commandState = :failureState,
               command.failureFactsJson = :failureFacts,
               command.nextTryTime = :nextTryTime,
               command.deliveryToken = null,
               command.leaseUntil = null,
               command.version = command.version + 1
         where command.id = :recordId
           and command.deliveryToken = :token
           and command.leaseUntil > :now
           and command.commandState = :ownedState
        """
    )
    fun transitionFailure(
        @Param("recordId") recordId: Long,
        @Param("token") token: String,
        @Param("ownedState") ownedState: CommandRecordEntity.CommandState,
        @Param("failureState") failureState: CommandRecordEntity.CommandState,
        @Param("failureFacts") failureFacts: String,
        @Param("nextTryTime") nextTryTime: LocalDateTime,
        @Param("now") now: LocalDateTime,
    ): Int
}
