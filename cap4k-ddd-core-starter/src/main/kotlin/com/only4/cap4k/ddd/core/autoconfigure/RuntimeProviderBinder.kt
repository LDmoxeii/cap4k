package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisor
import com.only4.cap4k.ddd.core.application.capability.CapabilitySupervisorSupport
import com.only4.cap4k.ddd.core.application.command.CommandSupervisor
import com.only4.cap4k.ddd.core.application.command.CommandSupervisorSupport
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisor
import com.only4.cap4k.ddd.core.application.command.ReliableCommandSupervisorSupport
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisorSupport
import com.only4.cap4k.ddd.core.application.query.QuerySupervisor
import com.only4.cap4k.ddd.core.application.query.QuerySupervisorSupport
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisorSupport
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisorSupport
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinator
import com.only4.cap4k.ddd.core.domain.managed.ManagedEntityAdmissionCoordinatorSupport
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisorSupport
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisorSupport
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext
import java.util.ArrayDeque

/**
 * Owns the process-wide static provider registrations for one active Spring
 * application context and releases exactly those registrations on shutdown.
 */
class RuntimeProviderBinder(
    private val applicationContext: ApplicationContext,
    private val beanFactory: ListableBeanFactory,
) : SmartInitializingSingleton, DisposableBean {
    private val releases = ArrayDeque<() -> Unit>()

    @Synchronized
    override fun afterSingletonsInstantiated() {
        val identifierGenerator = required(IdentifierGenerator::class.java, "identifiers")
        val commandSupervisor = required(CommandSupervisor::class.java, "commands")
        val querySupervisor = required(QuerySupervisor::class.java, "queries")
        val capabilitySupervisor = required(CapabilitySupervisor::class.java, "capabilities")
        val domainServiceSupervisor = required(DomainServiceSupervisor::class.java, "services")
        val domainEventSupervisor = required(DomainEventSupervisor::class.java, "domain-events")
        val domainEventManager = required(DomainEventManager::class.java, "domain-event-manager")
        val reliableCommandSupervisor = optional(ReliableCommandSupervisor::class.java, "reliable-commands")
        val aggregateFactorySupervisor = optional(AggregateFactorySupervisor::class.java, "factories")
        val managedEntityAdmissionCoordinator = optional(
            ManagedEntityAdmissionCoordinator::class.java,
            "managed-entity-admission",
        )
        val repositorySupervisor = optional(RepositorySupervisor::class.java, "repositories")
        val integrationEventSupervisor = optional(IntegrationEventSupervisor::class.java, "integration-events")
        val integrationEventManager = optional(IntegrationEventManager::class.java, "integration-event-manager")
        optional(ReliableDomainEventProvider::class.java, "reliable-domain-events")

        try {
            bind(applicationContext, MediatorSupport::configure, MediatorSupport::release)
            bind(identifierGenerator, MediatorSupport::configure, MediatorSupport::release)
            bind(commandSupervisor, CommandSupervisorSupport::configure, CommandSupervisorSupport::release)
            bind(querySupervisor, QuerySupervisorSupport::configure, QuerySupervisorSupport::release)
            bind(capabilitySupervisor, CapabilitySupervisorSupport::configure, CapabilitySupervisorSupport::release)
            bind(
                domainServiceSupervisor,
                DomainServiceSupervisorSupport::configure,
                DomainServiceSupervisorSupport::release,
            )
            bind(domainEventSupervisor, DomainEventSupervisorSupport::configure, DomainEventSupervisorSupport::release)
            bind(domainEventManager, DomainEventSupervisorSupport::configure, DomainEventSupervisorSupport::release)
            reliableCommandSupervisor?.let {
                bind(it, ReliableCommandSupervisorSupport::configure, ReliableCommandSupervisorSupport::release)
            }
            aggregateFactorySupervisor?.let {
                bind(it, AggregateFactorySupervisorSupport::configure, AggregateFactorySupervisorSupport::release)
            }
            managedEntityAdmissionCoordinator?.let {
                bind(
                    it,
                    ManagedEntityAdmissionCoordinatorSupport::configure,
                    ManagedEntityAdmissionCoordinatorSupport::release,
                )
            }
            repositorySupervisor?.let {
                bind(it, RepositorySupervisorSupport::configure, RepositorySupervisorSupport::release)
            }
            integrationEventSupervisor?.let {
                bind(it, IntegrationEventSupervisorSupport::configure, IntegrationEventSupervisorSupport::release)
            }
            integrationEventManager?.let {
                bind(it, IntegrationEventSupervisorSupport::configure, IntegrationEventSupervisorSupport::release)
            }
        } catch (failure: Throwable) {
            releaseAll()
            throw failure
        }
    }

    @Synchronized
    override fun destroy() {
        releaseAll()
    }

    private fun <T : Any> required(type: Class<T>, slot: String): T =
        RuntimeProviderComposition.required(beanFactory, type, slot)

    private fun <T : Any> optional(type: Class<T>, slot: String): T? =
        RuntimeProviderComposition.optional(beanFactory, type, slot)

    private fun <T : Any> bind(
        provider: T,
        configure: (T) -> Unit,
        release: (T) -> Unit,
    ) {
        configure(provider)
        releases.addFirst { release(provider) }
    }

    private fun releaseAll() {
        while (releases.isNotEmpty()) {
            releases.removeFirst().invoke()
        }
    }
}
