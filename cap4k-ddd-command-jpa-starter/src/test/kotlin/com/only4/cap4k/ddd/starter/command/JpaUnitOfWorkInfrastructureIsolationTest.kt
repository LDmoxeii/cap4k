package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordEntity
import com.only4.cap4k.ddd.application.command.persistence.CommandRecordJpaRepository
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:uow-infrastructure-isolation;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ],
)
@Import(JpaUnitOfWorkInfrastructureIsolationTest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaUnitOfWorkInfrastructureIsolationTest {
    @Autowired
    lateinit var entityManager: EntityManager

    @Autowired
    lateinit var roots: InfrastructureIsolationRootRepository

    @Autowired
    lateinit var commandRecords: CommandRecordJpaRepository

    @Autowired
    lateinit var unitOfWork: JpaUnitOfWork

    @Test
    fun `tracked aggregate and reliable command infrastructure entity stabilize together`() {
        roots.saveAndFlush(InfrastructureIsolationRoot(1L, "before"))
        entityManager.clear()
        lateinit var commandRecord: CommandRecordEntity

        unitOfWork.execute {
            val root = roots.findById(1L).orElseThrow()
            unitOfWork.observeRepositoryLoad(root)
            root.name = "after"

            commandRecord = commandRecords.save(
                CommandRecordEntity(
                    commandUuid = "reliable-command-1",
                    svcName = "before-service",
                    commandType = "test-command",
                )
            )
            commandRecord.svcName = "after-service"
        }

        entityManager.clear()
        assertEquals("after", roots.findById(1L).orElseThrow().name)
        assertNotNull(commandRecord.id)
        assertEquals("after-service", commandRecords.findById(commandRecord.id!!).orElseThrow().svcName)
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [InfrastructureIsolationRoot::class, CommandRecordEntity::class])
    @EnableJpaRepositories(
        basePackageClasses = [InfrastructureIsolationRootRepository::class, CommandRecordJpaRepository::class]
    )
    class TestApplication

    class TestConfig {
        @Bean
        fun domainEventManager(): DomainEventManager = object : DomainEventManager {
            override fun release(entities: Set<Any>) = Unit
            override fun pendingCount(): Int = 0
        }

        @Bean
        fun jpaUnitOfWork(domainEventManager: DomainEventManager): JpaUnitOfWork =
            JpaUnitOfWork(domainEventManager)
    }
}

@Entity
@Table(name = "uow_infrastructure_root")
class InfrastructureIsolationRoot(
    @Id
    var id: Long = 0,
    @Column(nullable = false)
    var name: String = "",
)

interface InfrastructureIsolationRootRepository : JpaRepository<InfrastructureIsolationRoot, Long>
